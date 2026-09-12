package com.learnplatform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.CourseStageAssessmentSummaryVO;
import com.learnplatform.dto.CourseStageAssessmentVO;
import com.learnplatform.entity.CourseStageAssessment;
import com.learnplatform.entity.CourseStageAssessmentQuestion;
import com.learnplatform.mapper.CourseStageAssessmentQuestionMapper;
import com.learnplatform.service.assessment.CourseStageAssessmentKnowledgePointSummary;
import com.learnplatform.service.assessment.CourseStageAssessmentSourceComposition;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CourseStageAssessmentViewService {
    private static final String COMPLETED = "COMPLETED";

    private final CourseStageAssessmentQuestionMapper assessmentQuestionMapper;
    private final ObjectMapper objectMapper;

    public CourseStageAssessmentViewService(
            CourseStageAssessmentQuestionMapper assessmentQuestionMapper,
            ObjectMapper objectMapper) {
        this.assessmentQuestionMapper = assessmentQuestionMapper;
        this.objectMapper = objectMapper;
    }

    public CourseStageAssessmentVO toView(CourseStageAssessment assessment) {
        boolean completed = COMPLETED.equals(assessment.getStatus());
        CourseStageAssessmentVO view = new CourseStageAssessmentVO();
        view.setId(assessment.getId());
        view.setCourseId(assessment.getCourseId());
        view.setStatus(assessment.getStatus());
        view.setSelectionStrategy(assessment.getSelectionStrategy());
        view.setTargetKnowledgePointId(assessment.getTargetKnowledgePointId());
        view.setTargetKnowledgePointName(assessment.getTargetKnowledgePointNameSnapshot());
        view.setQuestionCount(assessment.getQuestionCount());
        view.setCorrectCount(assessment.getCorrectCount());
        view.setStartTime(assessment.getStartTime());
        view.setCompleteTime(assessment.getCompleteTime());
        List<CourseStageAssessmentQuestion> items = assessmentQuestionMapper.selectByAssessmentId(assessment.getId());
        view.setSourceComposition(CourseStageAssessmentSourceComposition.from(items));
        view.setKnowledgePointSummary(completed
                ? CourseStageAssessmentKnowledgePointSummary.from(items, objectMapper) : null);
        view.setQuestions(items.stream().map(item -> toQuestionView(item, completed)).toList());
        return view;
    }

    public CourseStageAssessmentSummaryVO toSummary(
            CourseStageAssessment assessment, List<CourseStageAssessmentQuestion> sourceItems) {
        CourseStageAssessmentSummaryVO view = new CourseStageAssessmentSummaryVO();
        view.setId(assessment.getId());
        view.setSelectionStrategy(assessment.getSelectionStrategy());
        view.setTargetKnowledgePointId(assessment.getTargetKnowledgePointId());
        view.setTargetKnowledgePointName(assessment.getTargetKnowledgePointNameSnapshot());
        view.setQuestionCount(assessment.getQuestionCount());
        view.setCorrectCount(assessment.getCorrectCount());
        view.setStartTime(assessment.getStartTime());
        view.setCompleteTime(assessment.getCompleteTime());
        view.setSourceComposition(CourseStageAssessmentSourceComposition.from(sourceItems));
        return view;
    }

    private CourseStageAssessmentVO.QuestionItem toQuestionView(
            CourseStageAssessmentQuestion item, boolean completed) {
        CourseStageAssessmentVO.QuestionItem view = new CourseStageAssessmentVO.QuestionItem();
        view.setId(item.getId());
        view.setQuestionId(item.getQuestionId());
        view.setSortOrder(item.getSortOrder());
        view.setQuestionType(item.getQuestionType());
        view.setSourceType(item.getSourceTypeSnapshot());
        view.setSourceCategory(item.getSourceCategorySnapshot());
        view.setOriginQuestionId(item.getOriginQuestionIdSnapshot());
        view.setContent(item.getContentSnapshot());
        view.setOptions(readOptions(item.getOptionsSnapshot()));
        view.setScore(item.getScore());
        view.setUserAnswer(item.getUserAnswer());
        view.setCorrect(item.getIsCorrect() == null ? null : item.getIsCorrect() == 1);
        view.setCorrectAnswer(completed ? item.getCorrectAnswerSnapshot() : null);
        view.setAnalysis(completed ? item.getAnalysisSnapshot() : null);
        view.setKnowledgePoints(CourseStageAssessmentKnowledgePointSummary.readKnowledgePoints(
                item.getKnowledgePointsJson(), objectMapper));
        return view;
    }

    private List<CourseStageAssessmentVO.OptionItem> readOptions(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "测评题目快照损坏");
        }
    }
}
