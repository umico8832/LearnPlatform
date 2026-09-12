package com.learnplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.dto.CourseStageAssessmentSummaryVO;
import com.learnplatform.entity.CourseStageAssessment;
import com.learnplatform.entity.CourseStageAssessmentQuestion;
import com.learnplatform.mapper.CourseStageAssessmentMapper;
import com.learnplatform.mapper.CourseStageAssessmentQuestionMapper;
import com.learnplatform.service.assessment.CourseStageAssessmentKnowledgePointSummary;
import com.learnplatform.service.assessment.CourseStageAssessmentSourceComposition;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CourseOverviewAssessmentService {

    private final CourseStageAssessmentMapper stageAssessmentMapper;
    private final CourseStageAssessmentQuestionMapper stageAssessmentQuestionMapper;
    private final ObjectMapper objectMapper;

    public CourseOverviewAssessmentService(CourseStageAssessmentMapper stageAssessmentMapper,
                                           CourseStageAssessmentQuestionMapper stageAssessmentQuestionMapper,
                                           ObjectMapper objectMapper) {
        this.stageAssessmentMapper = stageAssessmentMapper;
        this.stageAssessmentQuestionMapper = stageAssessmentQuestionMapper;
        this.objectMapper = objectMapper;
    }

    CourseStageAssessmentSummaryVO getLatest(Long userId, Long courseId) {
        CourseStageAssessment assessment = stageAssessmentMapper.selectLatestCompleted(userId, courseId);
        if (assessment == null) {
            return null;
        }
        CourseStageAssessmentSummaryVO view = new CourseStageAssessmentSummaryVO();
        view.setId(assessment.getId());
        view.setSelectionStrategy(assessment.getSelectionStrategy());
        view.setTargetKnowledgePointId(assessment.getTargetKnowledgePointId());
        view.setTargetKnowledgePointName(assessment.getTargetKnowledgePointNameSnapshot());
        view.setQuestionCount(assessment.getQuestionCount());
        view.setCorrectCount(assessment.getCorrectCount());
        view.setStartTime(assessment.getStartTime());
        view.setCompleteTime(assessment.getCompleteTime());
        List<CourseStageAssessmentQuestion> items =
                stageAssessmentQuestionMapper.selectByAssessmentId(assessment.getId());
        view.setSourceComposition(CourseStageAssessmentSourceComposition.from(items));
        view.setKnowledgePointSummary(CourseStageAssessmentKnowledgePointSummary.from(items, objectMapper));
        return view;
    }
}
