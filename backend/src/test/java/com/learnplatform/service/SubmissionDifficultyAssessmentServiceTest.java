package com.learnplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.SubmissionDifficultyVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.QuestionSubmission;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.QuestionSubmissionMapper;
import com.learnplatform.service.ai.AiProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 投稿 AI 难度评估服务测试
 */
@ExtendWith(MockitoExtension.class)
class SubmissionDifficultyAssessmentServiceTest {

    @Mock
    private AiProvider aiProvider;
    @Mock
    private AiCallGovernanceService callGovernanceService;
    @Mock
    private QuestionSubmissionMapper submissionMapper;
    @Mock
    private CourseMapper courseMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private SubmissionDifficultyAssessmentService assessmentService;

    private QuestionSubmission sampleSubmission;

    @BeforeEach
    void setUp() {
        try {
            var field = SubmissionDifficultyAssessmentService.class.getDeclaredField("objectMapper");
            field.setAccessible(true);
            field.set(assessmentService, objectMapper);
        } catch (Exception e) {
            fail("Failed to inject ObjectMapper: " + e.getMessage());
        }

        sampleSubmission = new QuestionSubmission();
        sampleSubmission.setId(1L);
        sampleSubmission.setUserId(10L);
        sampleSubmission.setContent("以下哪个是 Java 中的基本数据类型？");
        sampleSubmission.setQuestionType("SINGLE_CHOICE");
        sampleSubmission.setCourseId(1L);
        sampleSubmission.setDifficulty(2);
        sampleSubmission.setAnalysis("Java 基本数据类型包括 int、char、boolean 等");
        sampleSubmission.setOptionsJson("[{\"content\":\"int\",\"label\":\"A\",\"isCorrect\":true},{\"content\":\"String\",\"label\":\"B\",\"isCorrect\":false}]");
        sampleSubmission.setCorrectAnswer("A");
        sampleSubmission.setKnowledgePointIds("1");
        sampleSubmission.setTags("Java基础");
    }

    @Test
    @DisplayName("投稿不存在时抛异常")
    void assessDifficulty_submissionNotFound_throws() {
        when(submissionMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> assessmentService.assessDifficulty(999L, 1L));
    }

    @Test
    @DisplayName("AI 返回标准 JSON 时正确解析")
    void assessDifficulty_aiReturnsValidJson_parsesCorrectly() {
        when(submissionMapper.selectById(1L)).thenReturn(sampleSubmission);
        when(courseMapper.selectById(1L)).thenReturn(new Course());

        String aiJson = "{\"suggestedDifficulty\":2,\"confidence\":\"HIGH\","
                + "\"reason\":\"基础识记题，直接考查 Java 基本数据类型\","
                + "\"cognitiveLevel\":\"记忆\","
                + "\"factors\":[{\"name\":\"知识点深度\",\"description\":\"基础概念\",\"impact\":\"DECREASE\"}],"
                + "\"summary\":\"简单基础题\"}";

        when(aiProvider.chat(anyString(), anyString())).thenReturn(aiJson);

        SubmissionDifficultyVO result = assessmentService.assessDifficulty(1L, 1L);

        assertNotNull(result);
        assertEquals(2, result.getSuggestedDifficulty());
        assertEquals(2, result.getOriginalDifficulty());
        assertTrue(result.getDifficultyMatch());
        assertEquals("HIGH", result.getConfidence());
        assertEquals("记忆", result.getCognitiveLevel());
        assertEquals(1, result.getFactors().size());
        assertEquals("DECREASE", result.getFactors().get(0).getImpact());

        verify(callGovernanceService).checkDailyQuota(1L);
        verify(callGovernanceService).logCall(eq(1L), eq("submission_difficulty_assessment"), eq(true), isNull(), anyInt());
    }

    @Test
    @DisplayName("AI 评估难度与投稿者不一致时 difficultyMatch=false")
    void assessDifficulty_aiSuggestsDifferentDifficulty_matchFalse() {
        when(submissionMapper.selectById(1L)).thenReturn(sampleSubmission);
        when(courseMapper.selectById(1L)).thenReturn(new Course());

        String aiJson = "{\"suggestedDifficulty\":4,\"confidence\":\"MEDIUM\","
                + "\"reason\":\"需要综合分析\",\"cognitiveLevel\":\"分析\","
                + "\"factors\":[],\"summary\":\"中高难度\"}";

        when(aiProvider.chat(anyString(), anyString())).thenReturn(aiJson);

        SubmissionDifficultyVO result = assessmentService.assessDifficulty(1L, 1L);

        assertEquals(4, result.getSuggestedDifficulty());
        assertEquals(2, result.getOriginalDifficulty());
        assertFalse(result.getDifficultyMatch());
    }

    @Test
    @DisplayName("AI 调用失败时回退到基础规则评估")
    void assessDifficulty_aiFails_fallbackToRuleEstimate() {
        when(submissionMapper.selectById(1L)).thenReturn(sampleSubmission);
        when(aiProvider.chat(anyString(), anyString())).thenThrow(new RuntimeException("AI 服务不可用"));

        SubmissionDifficultyVO result = assessmentService.assessDifficulty(1L, 1L);

        assertNotNull(result);
        assertNotNull(result.getSuggestedDifficulty());
        assertTrue(result.getSuggestedDifficulty() >= 1 && result.getSuggestedDifficulty() <= 5);
        assertEquals("LOW", result.getConfidence());
        assertEquals(2, result.getOriginalDifficulty());

        verify(callGovernanceService).logCall(eq(1L), eq("submission_difficulty_assessment"), eq(false), anyString(), anyInt());
    }

    @Test
    @DisplayName("AI 返回带 Markdown 代码块时仍可解析")
    void assessDifficulty_aiReturnsMarkdownWrappedJson_parsesCorrectly() {
        when(submissionMapper.selectById(1L)).thenReturn(sampleSubmission);
        when(courseMapper.selectById(1L)).thenReturn(new Course());

        String aiJson = "```json\n{\"suggestedDifficulty\":3,\"confidence\":\"MEDIUM\","
                + "\"reason\":\"中等难度\",\"cognitiveLevel\":\"应用\","
                + "\"factors\":[],\"summary\":\"中等\"}\n```";

        when(aiProvider.chat(anyString(), anyString())).thenReturn(aiJson);

        SubmissionDifficultyVO result = assessmentService.assessDifficulty(1L, 1L);

        assertEquals(3, result.getSuggestedDifficulty());
    }

    @Test
    @DisplayName("AI 返回无效 JSON 时回退到原始难度")
    void assessDifficulty_aiReturnsInvalidJson_fallbackToOriginal() {
        when(submissionMapper.selectById(1L)).thenReturn(sampleSubmission);
        when(aiProvider.chat(anyString(), anyString())).thenReturn("这不是一个有效的 JSON");

        SubmissionDifficultyVO result = assessmentService.assessDifficulty(1L, 1L);

        assertNotNull(result);
        assertEquals("LOW", result.getConfidence());
        // Should fallback to original difficulty
        assertEquals(2, result.getSuggestedDifficulty());
    }

    @Test
    @DisplayName("投稿者未设置难度时 fallback 仍正常工作")
    void assessDifficulty_noOriginalDifficulty_fallbackWorks() {
        sampleSubmission.setDifficulty(null);
        when(submissionMapper.selectById(1L)).thenReturn(sampleSubmission);
        when(aiProvider.chat(anyString(), anyString())).thenThrow(new RuntimeException("AI unavailable"));

        SubmissionDifficultyVO result = assessmentService.assessDifficulty(1L, 1L);

        assertNotNull(result);
        assertNull(result.getOriginalDifficulty());
        assertNotNull(result.getSuggestedDifficulty());
        assertTrue(result.getSuggestedDifficulty() >= 1 && result.getSuggestedDifficulty() <= 5);
    }

    @Test
    @DisplayName("AI 评估难度限制在 1-5 范围内")
    void assessDifficulty_aiReturnsOutOfRange_clampedToRange() {
        when(submissionMapper.selectById(1L)).thenReturn(sampleSubmission);
        when(courseMapper.selectById(1L)).thenReturn(new Course());

        String aiJson = "{\"suggestedDifficulty\":10,\"confidence\":\"HIGH\","
                + "\"reason\":\"测试\",\"cognitiveLevel\":\"记忆\","
                + "\"factors\":[],\"summary\":\"测试\"}";

        when(aiProvider.chat(anyString(), anyString())).thenReturn(aiJson);

        SubmissionDifficultyVO result = assessmentService.assessDifficulty(1L, 1L);

        assertTrue(result.getSuggestedDifficulty() >= 1 && result.getSuggestedDifficulty() <= 5,
                "Difficulty should be clamped to 1-5, got: " + result.getSuggestedDifficulty());
    }
}
