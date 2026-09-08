package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.CourseOverviewVO;
import com.learnplatform.dto.CourseKnowledgePointFactVO;
import com.learnplatform.entity.Course;
import com.learnplatform.mapper.CourseLearningFactMapper;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.UserCourseMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.CourseStageAssessmentMapper;
import com.learnplatform.mapper.CourseStageAssessmentQuestionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseOverviewServiceTest {
    @Mock private UserCourseMapper userCourseMapper;
    @Mock private CourseMapper courseMapper;
    @Mock private CourseLearningFactMapper factMapper;
    @Mock private KnowledgePointMapper knowledgePointMapper;
    @Mock private CourseStageAssessmentMapper stageAssessmentMapper;
    @Mock private CourseStageAssessmentQuestionMapper stageAssessmentQuestionMapper;
    private CourseOverviewService service;

    @BeforeEach
    void setUp() {
        CourseOverviewTargetService target = new CourseOverviewTargetService(knowledgePointMapper, factMapper);
        CourseOverviewAssessmentService assessment = new CourseOverviewAssessmentService(
                stageAssessmentMapper, stageAssessmentQuestionMapper, new ObjectMapper());
        service = new CourseOverviewService(userCourseMapper, courseMapper, factMapper, target, assessment);
    }

    @Test
    void forwardsFactTotalsAndUsesOnlyFirstTodoIdsForTargets() {
        when(userCourseMapper.selectCount(any())).thenReturn(1L);
        when(courseMapper.selectById(10L)).thenReturn(course());
        when(factMapper.selectTotals(any(), any(), any())).thenReturn(totals(2, 1, 3, 4));
        when(factMapper.selectFirstDueQuestion(any(), any(), any())).thenReturn(21L);
        when(factMapper.selectFirstWrongQuestion(any(), any())).thenReturn(22L);
        when(factMapper.selectTutorProgress(any(), any())).thenReturn(List.of());
        when(knowledgePointMapper.selectOne(any())).thenReturn(null);
        CourseOverviewVO overview = service.getOverview(7L, 10L);
        assertEquals(2, overview.getAnsweredCount());
        assertEquals(1, overview.getCorrectCount());
        assertEquals(3, overview.getDueReviewCount());
        assertEquals(4, overview.getUnresolvedWrongCount());
        assertEquals(21L, overview.getRecommendedTargets().get(0).getQuestionId());
        assertEquals("有 3 道题已到复习时间", overview.getRecommendedTargets().get(0).getReason());
    }

    @Test
    void rejectsOverviewForCourseOutsideUsersLibrary() {
        when(userCourseMapper.selectCount(any())).thenReturn(0L);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getOverview(7L, 10L));
        assertEquals("请先将课程加入个人课程库", exception.getMessage());
        verifyNoInteractions(factMapper);
    }

    @Test
    void rejectsInvalidFactsPaginationWithoutFactQueries() {
        when(userCourseMapper.selectCount(any())).thenReturn(1L);
        when(courseMapper.selectById(10L)).thenReturn(course());
        assertThrows(BusinessException.class, () -> service.getKnowledgePointFacts(7L, 10L, 0, 10));
        assertThrows(BusinessException.class, () -> service.getKnowledgePointFacts(7L, 10L, 1, 51));
        verifyNoInteractions(factMapper);
    }

    @Test
    void returnsEmptyPageWhenOffsetIsOutsideTotal() {
        when(userCourseMapper.selectCount(any())).thenReturn(1L);
        when(courseMapper.selectById(10L)).thenReturn(course());
        when(factMapper.countKnowledgePoints(any(), any(), any())).thenReturn(2L);
        Page<CourseKnowledgePointFactVO> page = service.getKnowledgePointFacts(7L, 10L, 2, 2);
        assertEquals(2L, page.getTotal());
        verify(factMapper).countKnowledgePoints(any(), any(), any());
    }

    private CourseOverviewVO totals(int answered, int correct, int due, int wrong) {
        CourseOverviewVO value = new CourseOverviewVO();
        value.setAnsweredCount(answered); value.setCorrectCount(correct);
        value.setDueReviewCount(due); value.setUnresolvedWrongCount(wrong);
        return value;
    }
    private Course course() {
        Course value = new Course(); value.setId(10L); value.setName("408 数据结构"); return value;
    }
}
