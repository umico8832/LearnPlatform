package com.learnplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.SubmissionKPTaggingVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.QuestionSubmission;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionSubmissionMapper;
import com.learnplatform.service.ai.AiProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubmissionKPTaggingService 单元测试")
class SubmissionKPTaggingServiceTest {

    @Mock
    private AiProvider aiProvider;
    @Mock
    private AiCallGovernanceService callGovernanceService;
    @Mock
    private QuestionSubmissionMapper submissionMapper;
    @Mock
    private CourseMapper courseMapper;
    @Mock
    private KnowledgePointMapper knowledgePointMapper;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private SubmissionKPTaggingService kpTaggingService;

    private QuestionSubmission submission;
    private List<KnowledgePoint> knowledgePoints;
    private Course course;

    @BeforeEach
    void setUp() {
        submission = new QuestionSubmission();
        submission.setId(1L);
        submission.setUserId(100L);
        submission.setContent("以下哪个排序算法的平均时间复杂度为 O(n log n)？");
        submission.setQuestionType("SINGLE_CHOICE");
        submission.setCourseId(10L);
        submission.setDifficulty(3);
        submission.setAnalysis("快速排序、归并排序、堆排序的平均时间复杂度均为 O(n log n)。");

        course = new Course();
        course.setId(10L);
        course.setName("数据结构与算法");

        KnowledgePoint kp1 = new KnowledgePoint();
        kp1.setId(101L);
        kp1.setName("排序算法");
        kp1.setCourseId(10L);
        kp1.setDescription("各种排序算法的原理与复杂度分析");

        KnowledgePoint kp2 = new KnowledgePoint();
        kp2.setId(102L);
        kp2.setName("时间复杂度");
        kp2.setCourseId(10L);
        kp2.setDescription("算法时间复杂度的分析方法");

        KnowledgePoint kp3 = new KnowledgePoint();
        kp3.setId(103L);
        kp3.setName("链表");
        kp3.setCourseId(10L);
        kp3.setDescription("链表数据结构");

        knowledgePoints = List.of(kp1, kp2, kp3);
    }

    @Test
    @DisplayName("投稿不存在时抛出异常")
    void tagKnowledgePoints_submissionNotFound() {
        when(submissionMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> kpTaggingService.tagKnowledgePoints(1L, 100L));
    }

    @Test
    @DisplayName("投稿未关联课程时抛出异常")
    void tagKnowledgePoints_noCourseId() {
        submission.setCourseId(null);
        when(submissionMapper.selectById(1L)).thenReturn(submission);
        assertThrows(BusinessException.class, () -> kpTaggingService.tagKnowledgePoints(1L, 100L));
    }

    @Test
    @DisplayName("课程下没有知识点时抛出异常")
    void tagKnowledgePoints_noKnowledgePoints() {
        when(submissionMapper.selectById(1L)).thenReturn(submission);
        when(knowledgePointMapper.selectList(any())).thenReturn(List.of());
        assertThrows(BusinessException.class, () -> kpTaggingService.tagKnowledgePoints(1L, 100L));
    }

    @Test
    @DisplayName("AI 正常返回 JSON 时正确解析知识点推荐")
    void tagKnowledgePoints_aiReturnsValidJson() {
        when(submissionMapper.selectById(1L)).thenReturn(submission);
        when(knowledgePointMapper.selectList(any())).thenReturn(knowledgePoints);
        when(courseMapper.selectById(10L)).thenReturn(course);

        String aiResponse = "{\"recommendations\":["
                + "{\"id\":101,\"confidence\":\"HIGH\",\"reason\":\"题目直接涉及排序算法\"},"
                + "{\"id\":102,\"confidence\":\"MEDIUM\",\"reason\":\"题目提到了时间复杂度\"}"
                + "],\"analysis\":\"该题目主要考察排序算法和时间复杂度的知识。\"}";
        when(aiProvider.chat(anyString(), anyString())).thenReturn(aiResponse);

        SubmissionKPTaggingVO result = kpTaggingService.tagKnowledgePoints(1L, 100L);

        assertEquals(2, result.getRecommendations().size());
        assertEquals(101L, result.getRecommendations().get(0).getId());
        assertEquals("排序算法", result.getRecommendations().get(0).getName());
        assertEquals("HIGH", result.getRecommendations().get(0).getConfidence());
        assertEquals("101,102", result.getSuggestedIds());
        assertTrue(result.getAnalysis().contains("排序算法"));

        verify(callGovernanceService).checkDailyQuota(100L);
        verify(callGovernanceService).logCall(eq(100L), eq("submission_kp_tagging"), eq(true), isNull(), anyInt());
    }

    @Test
    @DisplayName("AI 调用失败时回退到关键词匹配")
    void tagKnowledgePoints_aiFails_fallbackToKeywordMatch() {
        submission.setContent("请解释排序算法中的快速排序原理及其时间复杂度。");
        when(submissionMapper.selectById(1L)).thenReturn(submission);
        when(knowledgePointMapper.selectList(any())).thenReturn(knowledgePoints);
        when(courseMapper.selectById(10L)).thenReturn(course);
        when(aiProvider.chat(anyString(), anyString())).thenThrow(new RuntimeException("AI unavailable"));

        SubmissionKPTaggingVO result = kpTaggingService.tagKnowledgePoints(1L, 100L);

        // "排序算法" and "时间复杂度" should be found via keyword match
        assertTrue(result.getRecommendations().size() >= 1);
        assertTrue(result.getAnalysis().contains("关键词匹配") || result.getAnalysis().contains("AI 服务暂不可用"));
        verify(callGovernanceService).logCall(eq(100L), eq("submission_kp_tagging"), eq(false), anyString(), anyInt());
    }

    @Test
    @DisplayName("AI 推荐了不存在的知识点 ID 时跳过该推荐")
    void tagKnowledgePoints_aiReturnsNonExistentId() {
        when(submissionMapper.selectById(1L)).thenReturn(submission);
        when(knowledgePointMapper.selectList(any())).thenReturn(knowledgePoints);
        when(courseMapper.selectById(10L)).thenReturn(course);

        // AI returns id 999 which doesn't exist in kp list
        String aiResponse = "{\"recommendations\":["
                + "{\"id\":999,\"confidence\":\"HIGH\",\"reason\":\"不存在\"},"
                + "{\"id\":101,\"confidence\":\"HIGH\",\"reason\":\"排序算法\"}"
                + "],\"analysis\":\"测试\"}";
        when(aiProvider.chat(anyString(), anyString())).thenReturn(aiResponse);

        SubmissionKPTaggingVO result = kpTaggingService.tagKnowledgePoints(1L, 100L);

        assertEquals(1, result.getRecommendations().size());
        assertEquals(101L, result.getRecommendations().get(0).getId());
    }

    @Test
    @DisplayName("AI 返回 Markdown 代码块包裹的 JSON 也能正确解析")
    void tagKnowledgePoints_aiReturnsMarkdownWrappedJson() {
        when(submissionMapper.selectById(1L)).thenReturn(submission);
        when(knowledgePointMapper.selectList(any())).thenReturn(knowledgePoints);
        when(courseMapper.selectById(10L)).thenReturn(course);

        String aiResponse = "```json\n{\"recommendations\":[{\"id\":101,\"confidence\":\"HIGH\",\"reason\":\"排序\"}],\"analysis\":\"分析\"}\n```";
        when(aiProvider.chat(anyString(), anyString())).thenReturn(aiResponse);

        SubmissionKPTaggingVO result = kpTaggingService.tagKnowledgePoints(1L, 100L);

        assertEquals(1, result.getRecommendations().size());
        assertEquals(101L, result.getRecommendations().get(0).getId());
    }

    @Test
    @DisplayName("AI 返回无效 JSON 时返回空推荐结果")
    void tagKnowledgePoints_aiReturnsInvalidJson() {
        when(submissionMapper.selectById(1L)).thenReturn(submission);
        when(knowledgePointMapper.selectList(any())).thenReturn(knowledgePoints);
        when(courseMapper.selectById(10L)).thenReturn(course);

        when(aiProvider.chat(anyString(), anyString())).thenReturn("This is not JSON at all");

        SubmissionKPTaggingVO result = kpTaggingService.tagKnowledgePoints(1L, 100L);

        assertTrue(result.getRecommendations().isEmpty());
        assertTrue(result.getAnalysis().contains("解析失败"));
    }
}
