package com.learnplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.SubmissionQualityCheckVO;
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
 * 投稿 AI 质检服务测试
 */
@ExtendWith(MockitoExtension.class)
class SubmissionAiQualityServiceTest {

    @Mock
    private AiProvider aiProvider;
    @Mock
    private AiService aiService;
    @Mock
    private QuestionSubmissionMapper submissionMapper;
    @Mock
    private CourseMapper courseMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private SubmissionAiQualityService qualityService;

    private QuestionSubmission sampleSubmission;

    @BeforeEach
    void setUp() {
        // Use reflection to inject objectMapper since it's final-initialized
        try {
            var field = SubmissionAiQualityService.class.getDeclaredField("objectMapper");
            field.setAccessible(true);
            field.set(qualityService, objectMapper);
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
        sampleSubmission.setSource("课本");
    }

    @Test
    @DisplayName("投稿不存在时抛异常")
    void checkQuality_submissionNotFound_throws() {
        when(submissionMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> qualityService.checkQuality(999L, 1L));
    }

    @Test
    @DisplayName("AI 返回标准 JSON 时正确解析")
    void checkQuality_aiReturnsValidJson_parsesCorrectly() {
        when(submissionMapper.selectById(1L)).thenReturn(sampleSubmission);
        when(courseMapper.selectById(1L)).thenReturn(new Course());

        String aiJson = "{\"qualityScore\":85,\"summary\":\"题目质量良好\",\"recommendation\":\"APPROVE\","
                + "\"formatCheck\":{\"status\":\"PASS\",\"detail\":\"格式规范\"},"
                + "\"completenessCheck\":{\"status\":\"PASS\",\"detail\":\"内容完整\"},"
                + "\"answerCheck\":{\"status\":\"PASS\",\"detail\":\"答案正确\"},"
                + "\"analysisCheck\":{\"status\":\"WARNING\",\"detail\":\"解析可更详细\"},"
                + "\"knowledgePointCheck\":{\"status\":\"PASS\",\"detail\":\"知识点相关\"},"
                + "\"riskPoints\":[\"解析较简短\"],\"suggestions\":[\"建议补充更详细的解析\"]}";

        when(aiProvider.chat(anyString(), anyString())).thenReturn(aiJson);

        SubmissionQualityCheckVO result = qualityService.checkQuality(1L, 1L);

        assertNotNull(result);
        assertEquals(85, result.getQualityScore());
        assertEquals("题目质量良好", result.getSummary());
        assertEquals("APPROVE", result.getRecommendation());
        assertEquals("PASS", result.getFormatCheck().getStatus());
        assertEquals("WARNING", result.getAnalysisCheck().getStatus());
        assertEquals(1, result.getRiskPoints().size());
        assertEquals(1, result.getSuggestions().size());

        verify(aiService).checkDailyQuota(1L);
        verify(aiService).logCall(eq(1L), eq("submission_quality_check"), eq(true), isNull(), anyInt());
    }

    @Test
    @DisplayName("AI 调用失败时回退到基础规则检查")
    void checkQuality_aiFails_fallbackToRuleCheck() {
        when(submissionMapper.selectById(1L)).thenReturn(sampleSubmission);
        when(aiProvider.chat(anyString(), anyString())).thenThrow(new RuntimeException("AI 服务不可用"));

        SubmissionQualityCheckVO result = qualityService.checkQuality(1L, 1L);

        assertNotNull(result);
        assertNotNull(result.getQualityScore());
        assertNotNull(result.getRecommendation());
        // 标准投稿应该通过基础规则检查
        assertTrue(result.getQualityScore() >= 70);
        verify(aiService).logCall(eq(1L), eq("submission_quality_check"), eq(false), anyString(), anyInt());
    }

    @Test
    @DisplayName("AI 返回带 Markdown 代码块时仍可解析")
    void checkQuality_aiReturnsMarkdownWrappedJson_parsesCorrectly() {
        when(submissionMapper.selectById(1L)).thenReturn(sampleSubmission);
        when(courseMapper.selectById(1L)).thenReturn(new Course());

        String aiJson = "```json\n{\"qualityScore\":70,\"summary\":\"尚可\",\"recommendation\":\"REVISE\","
                + "\"formatCheck\":{\"status\":\"PASS\",\"detail\":\"OK\"},"
                + "\"completenessCheck\":{\"status\":\"PASS\",\"detail\":\"OK\"},"
                + "\"answerCheck\":{\"status\":\"PASS\",\"detail\":\"OK\"},"
                + "\"analysisCheck\":{\"status\":\"PASS\",\"detail\":\"OK\"},"
                + "\"knowledgePointCheck\":{\"status\":\"PASS\",\"detail\":\"OK\"},"
                + "\"riskPoints\":[],\"suggestions\":[]}\n```";

        when(aiProvider.chat(anyString(), anyString())).thenReturn(aiJson);

        SubmissionQualityCheckVO result = qualityService.checkQuality(1L, 1L);

        assertEquals(70, result.getQualityScore());
        assertEquals("REVISE", result.getRecommendation());
    }

    @Test
    @DisplayName("基础规则检查：缺少解析扣分")
    void checkQuality_fallback_noAnalysis_deductsScore() {
        sampleSubmission.setAnalysis(null);
        when(submissionMapper.selectById(1L)).thenReturn(sampleSubmission);
        when(aiProvider.chat(anyString(), anyString())).thenThrow(new RuntimeException("AI unavailable"));

        SubmissionQualityCheckVO result = qualityService.checkQuality(1L, 1L);

        assertEquals("WARNING", result.getAnalysisCheck().getStatus());
        assertTrue(result.getQualityScore() < 100);
    }

    @Test
    @DisplayName("基础规则检查：题干过短 FAIL")
    void checkQuality_fallback_shortContent_fail() {
        sampleSubmission.setContent("短题");
        when(submissionMapper.selectById(1L)).thenReturn(sampleSubmission);
        when(aiProvider.chat(anyString(), anyString())).thenThrow(new RuntimeException("AI unavailable"));

        SubmissionQualityCheckVO result = qualityService.checkQuality(1L, 1L);

        assertEquals("FAIL", result.getFormatCheck().getStatus());
        assertTrue(result.getQualityScore() <= 80, "Short content should reduce score to 80 or below");
    }

    @Test
    @DisplayName("基础规则检查：选择题缺少选项 FAIL")
    void checkQuality_fallback_noOptions_fail() {
        sampleSubmission.setOptionsJson(null);
        when(submissionMapper.selectById(1L)).thenReturn(sampleSubmission);
        when(aiProvider.chat(anyString(), anyString())).thenThrow(new RuntimeException("AI unavailable"));

        SubmissionQualityCheckVO result = qualityService.checkQuality(1L, 1L);

        assertEquals("FAIL", result.getCompletenessCheck().getStatus());
    }

    // ======================== generateReviewComment 测试 ========================

    @Test
    @DisplayName("生成审核意见：包含评分和摘要")
    void generateReviewComment_includesScoreAndSummary() {
        when(submissionMapper.selectById(1L)).thenReturn(sampleSubmission);
        when(courseMapper.selectById(1L)).thenReturn(new Course());

        String aiJson = "{\"qualityScore\":85,\"summary\":\"题目质量良好\",\"recommendation\":\"APPROVE\","
                + "\"formatCheck\":{\"status\":\"PASS\",\"detail\":\"格式规范\"},"
                + "\"completenessCheck\":{\"status\":\"PASS\",\"detail\":\"内容完整\"},"
                + "\"answerCheck\":{\"status\":\"PASS\",\"detail\":\"答案正确\"},"
                + "\"analysisCheck\":{\"status\":\"WARNING\",\"detail\":\"解析较简短\"},"
                + "\"knowledgePointCheck\":{\"status\":\"PASS\",\"detail\":\"知识点相关\"},"
                + "\"riskPoints\":[\"解析较简短\"],\"suggestions\":[\"建议补充更详细的解析\"]}";

        when(aiProvider.chat(anyString(), anyString())).thenReturn(aiJson);

        String comment = qualityService.generateReviewComment(1L, 1L);

        assertNotNull(comment);
        assertTrue(comment.contains("【AI 质检报告】"));
        assertTrue(comment.contains("综合评分：85"));
        assertTrue(comment.contains("解析较简短"));
        assertTrue(comment.contains("建议补充更详细的解析"));
    }

    @Test
    @DisplayName("生成审核意见：全部通过时不列出检查项")
    void generateReviewComment_allPass_noCheckItems() {
        when(submissionMapper.selectById(1L)).thenReturn(sampleSubmission);
        when(courseMapper.selectById(1L)).thenReturn(new Course());

        String aiJson = "{\"qualityScore\":95,\"summary\":\"质量优秀\",\"recommendation\":\"APPROVE\","
                + "\"formatCheck\":{\"status\":\"PASS\",\"detail\":\"OK\"},"
                + "\"completenessCheck\":{\"status\":\"PASS\",\"detail\":\"OK\"},"
                + "\"answerCheck\":{\"status\":\"PASS\",\"detail\":\"OK\"},"
                + "\"analysisCheck\":{\"status\":\"PASS\",\"detail\":\"OK\"},"
                + "\"knowledgePointCheck\":{\"status\":\"PASS\",\"detail\":\"OK\"},"
                + "\"riskPoints\":[],\"suggestions\":[]}";

        when(aiProvider.chat(anyString(), anyString())).thenReturn(aiJson);

        String comment = qualityService.generateReviewComment(1L, 1L);

        assertNotNull(comment);
        assertTrue(comment.contains("【AI 质检报告】"));
        // 不应包含不通过的检查项
        assertFalse(comment.contains("WARNING"));
        assertFalse(comment.contains("FAIL"));
    }

    @Test
    @DisplayName("生成审核意见：回退模式也正常输出")
    void generateReviewComment_fallbackMode_works() {
        when(submissionMapper.selectById(1L)).thenReturn(sampleSubmission);
        when(aiProvider.chat(anyString(), anyString())).thenThrow(new RuntimeException("AI unavailable"));

        String comment = qualityService.generateReviewComment(1L, 1L);

        assertNotNull(comment);
        assertTrue(comment.contains("【AI 质检报告】"));
        assertTrue(comment.contains("分"));
    }
}
