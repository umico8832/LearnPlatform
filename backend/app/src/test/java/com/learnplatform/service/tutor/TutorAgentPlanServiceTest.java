package com.learnplatform.service.tutor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.dto.CourseOverviewVO;
import com.learnplatform.dto.TutorAgentPlanStepVO;
import com.learnplatform.mapper.TutorAgentPlanConfirmationMapper;
import com.learnplatform.mapper.TutorAgentPlanQueryMapper;
import com.learnplatform.service.CourseOverviewService;
import com.learnplatform.service.TutorSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TutorAgentPlanServiceTest {
    private final TutorSessionService sessions = mock(TutorSessionService.class);
    private final CourseOverviewService overviews = mock(CourseOverviewService.class);
    private final TutorAgentPlanQueryMapper plans = mock(TutorAgentPlanQueryMapper.class);
    private final TutorAgentPlanConfirmationMapper confirmations = mock(TutorAgentPlanConfirmationMapper.class);
    private TutorAgentPlanService service;

    @BeforeEach
    void setUp() {
        service = new TutorAgentPlanService(sessions, overviews, plans, confirmations, new ObjectMapper());
    }

    @Test
    void proposesTheFirstThreeValidTargetsAndDeduplicatesQuestionTargets() {
        when(overviews.getOverview(7L, 10L)).thenReturn(overview(List.of(
                target("TUTOR", "缺少知识点", "无效目标", null, null),
                target("TUTOR", "复习栈", "回看核心规则", null, 31L),
                target("DUE_REVIEW", "到期复习", "完成到期题", 51L, null),
                target("WRONG_QUESTION", "错题重做", "同题不重复", 51L, null),
                target("COURSE_SEQUENCE", "继续队列", "按课程顺序", null, 32L),
                target("TUTOR", "后续节点", "达到上限后不选", null, 33L))));

        List<TutorAgentPlanStepVO> result = service.propose(7L, 10L, "session");

        assertEquals(List.of("TUTOR", "DUE_REVIEW", "COURSE_SEQUENCE"), result.stream()
                .map(TutorAgentPlanStepVO::type).toList());
        assertEquals(java.util.Arrays.asList(31L, null, 32L), result.stream()
                .map(TutorAgentPlanStepVO::knowledgePointId).toList());
        assertEquals(java.util.Arrays.asList(null, 51L, null), result.stream()
                .map(TutorAgentPlanStepVO::questionId).toList());
        verify(sessions).get(7L, 10L, "session");
        verifyNoInteractions(plans, confirmations);
    }

    @Test
    void returnsEmptyWhenTheCourseHasNoTargetsWithoutWritingConfirmation() {
        when(overviews.getOverview(7L, 10L)).thenReturn(overview(List.of()));

        assertEquals(List.of(), service.propose(7L, 10L, "session"));
        verify(sessions).get(7L, 10L, "session");
        verifyNoInteractions(plans, confirmations);
    }

    private CourseOverviewVO overview(List<CourseOverviewVO.LearningTargetVO> targets) {
        CourseOverviewVO overview = new CourseOverviewVO();
        overview.setRecommendedTargets(targets);
        return overview;
    }

    private CourseOverviewVO.LearningTargetVO target(String type, String title, String reason,
                                                       Long questionId, Long knowledgePointId) {
        CourseOverviewVO.LearningTargetVO target = new CourseOverviewVO.LearningTargetVO();
        target.setType(type);
        target.setTitle(title);
        target.setReason(reason);
        target.setQuestionId(questionId);
        target.setKnowledgePointId(knowledgePointId);
        return target;
    }
}
