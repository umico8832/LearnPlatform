package com.learnplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.dto.CourseStageAssessmentSummaryVO;
import com.learnplatform.entity.CourseStageAssessment;
import com.learnplatform.entity.CourseStageAssessmentQuestion;
import com.learnplatform.mapper.CourseStageAssessmentMapper;
import com.learnplatform.mapper.CourseStageAssessmentQuestionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseOverviewAssessmentServiceTest {

    @Mock
    private CourseStageAssessmentMapper stageAssessmentMapper;

    @Mock
    private CourseStageAssessmentQuestionMapper stageAssessmentQuestionMapper;

    private CourseOverviewAssessmentService assessmentService;

    @BeforeEach
    void setUp() {
        assessmentService = new CourseOverviewAssessmentService(
                stageAssessmentMapper, stageAssessmentQuestionMapper, new ObjectMapper());
    }

    @Test
    void returnsNullWithoutCompletedAssessment() {
        assertNull(assessmentService.getLatest(7L, 10L));

        verifyNoInteractions(stageAssessmentQuestionMapper);
    }

    @Test
    void exposesLatestAssessmentFactsWithoutInferringMastery() {
        CourseStageAssessment latest = new CourseStageAssessment();
        latest.setId(51L);
        latest.setQuestionCount(5);
        latest.setCorrectCount(3);
        latest.setCompleteTime(LocalDateTime.of(2026, 8, 15, 11, 0));
        CourseStageAssessmentQuestion item = new CourseStageAssessmentQuestion();
        item.setSourceCategorySnapshot("OFFICIAL_EXAM");
        item.setKnowledgePointsJson("[{\"id\":31,\"name\":\"栈\"}]");
        item.setIsCorrect(0);
        when(stageAssessmentMapper.selectLatestCompleted(7L, 10L)).thenReturn(latest);
        when(stageAssessmentQuestionMapper.selectByAssessmentId(51L)).thenReturn(List.of(item));

        CourseStageAssessmentSummaryVO result = assessmentService.getLatest(7L, 10L);

        assertEquals(51L, result.getId());
        assertEquals(3, result.getCorrectCount());
        assertEquals(5, result.getQuestionCount());
        assertEquals(1, result.getSourceComposition().getOfficialExamCount());
        assertEquals(31L, result.getKnowledgePointSummary().get(0).getId());
        assertEquals("栈", result.getKnowledgePointSummary().get(0).getName());
        assertEquals(1, result.getKnowledgePointSummary().get(0).getQuestionCount());
        assertEquals(0, result.getKnowledgePointSummary().get(0).getCorrectCount());
    }
}
