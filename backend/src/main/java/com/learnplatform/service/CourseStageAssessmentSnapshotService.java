package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.CourseStageAssessmentVO;
import com.learnplatform.entity.CourseStageAssessmentQuestion;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.mapper.CourseStageAssessmentMapper;
import com.learnplatform.mapper.CourseStageAssessmentQuestionMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CourseStageAssessmentSnapshotService {
    private final CourseStageAssessmentMapper assessmentMapper;
    private final CourseStageAssessmentQuestionMapper assessmentQuestionMapper;
    private final QuestionOptionMapper optionMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final AnswerEvaluator answerEvaluator;
    private final ObjectMapper objectMapper;

    public CourseStageAssessmentSnapshotService(
            CourseStageAssessmentMapper assessmentMapper,
            CourseStageAssessmentQuestionMapper assessmentQuestionMapper,
            QuestionOptionMapper optionMapper,
            KnowledgePointMapper knowledgePointMapper,
            AnswerEvaluator answerEvaluator,
            ObjectMapper objectMapper) {
        this.assessmentMapper = assessmentMapper;
        this.assessmentQuestionMapper = assessmentQuestionMapper;
        this.optionMapper = optionMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.answerEvaluator = answerEvaluator;
        this.objectMapper = objectMapper;
    }

    public void createSnapshot(Long assessmentId, int sortOrder, Question question) {
        List<QuestionOption> options = optionMapper.selectList(new LambdaQueryWrapper<QuestionOption>()
                .eq(QuestionOption::getQuestionId, question.getId())
                .orderByAsc(QuestionOption::getSortOrder));
        List<QuestionOption> correctOptions = options.stream()
                .filter(option -> Integer.valueOf(1).equals(option.getIsCorrect())).toList();
        String correctAnswer = answerEvaluator.buildCorrectAnswer(correctOptions, question.getQuestionType());
        if (options.isEmpty() || correctAnswer.isBlank()) {
            throw validation("课程题目缺少可判分选项");
        }
        List<CourseStageAssessmentVO.OptionItem> optionSnapshot = options.stream()
                .map(option -> new CourseStageAssessmentVO.OptionItem(
                        "TRUE_FALSE".equals(question.getQuestionType())
                                ? trueFalseValue(option.getContent()) : option.getOptionLabel(),
                        option.getContent()))
                .toList();
        CourseStageAssessmentQuestion item = new CourseStageAssessmentQuestion();
        item.setAssessmentId(assessmentId);
        item.setQuestionId(question.getId());
        item.setSortOrder(sortOrder);
        item.setQuestionType(question.getQuestionType());
        item.setSourceTypeSnapshot(question.getSourceType() == null ? "MANUAL" : question.getSourceType());
        item.setSourceCategorySnapshot(sourceCategory(question));
        item.setOriginQuestionIdSnapshot(question.getOriginQuestionId());
        item.setKnowledgePointsJson(snapshotKnowledgePoints(question.getId()));
        item.setContentSnapshot(question.getContent());
        item.setOptionsSnapshot(writeJson(optionSnapshot));
        item.setCorrectAnswerSnapshot(correctAnswer);
        item.setAnalysisSnapshot(question.getAnalysis());
        item.setScore(question.getScore() == null ? 1 : question.getScore());
        assessmentQuestionMapper.insert(item);
    }

    String sourceCategory(Question question) {
        if ("AI_GENERATED".equals(question.getSourceType())) {
            return "AI_GENERATED";
        }
        if ("PRIVATE".equals(question.getVisibility())
                || "USER_PRIVATE_IMPORT".equals(question.getSourceType())) {
            return "USER_PRIVATE";
        }
        Long officialReferences = assessmentMapper.countVerifiedOfficialPaperReferences(question.getId());
        return officialReferences != null && officialReferences > 0 ? "OFFICIAL_EXAM" : "MANUAL";
    }

    private String snapshotKnowledgePoints(Long questionId) {
        List<KnowledgePoint> points = knowledgePointMapper.selectByQuestionId(questionId);
        if (points.isEmpty()) {
            return null;
        }
        return writeJson(points.stream()
                .map(point -> new CourseStageAssessmentVO.KnowledgePointVO(point.getId(), point.getName()))
                .toList());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "测评题目快照保存失败");
        }
    }

    private String trueFalseValue(String content) {
        return "TRUE".equalsIgnoreCase(content) || "正确".equals(content) ? "TRUE" : "FALSE";
    }

    private BusinessException validation(String message) {
        return new BusinessException(ResultCode.VALIDATION_ERROR, message);
    }
}
