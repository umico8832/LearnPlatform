package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.entity.CourseStageAssessmentQuestion;
import com.learnplatform.entity.Question;
import com.learnplatform.mapper.QuestionMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class CourseStageAssessmentLearningFactService {
    private final QuestionMapper questionMapper;
    private final WrongQuestionService wrongQuestionService;
    private final SpacedRepetitionService repetitionService;
    private final CourseLearningEventService eventService;

    public CourseStageAssessmentLearningFactService(
            QuestionMapper questionMapper,
            WrongQuestionService wrongQuestionService,
            SpacedRepetitionService repetitionService,
            CourseLearningEventService eventService) {
        this.questionMapper = questionMapper;
        this.wrongQuestionService = wrongQuestionService;
        this.repetitionService = repetitionService;
        this.eventService = eventService;
    }

    public void record(Long userId, CourseStageAssessmentQuestion item,
                       boolean correct, LocalDateTime answeredTime) {
        Question question = questionMapper.selectById(item.getQuestionId());
        if (question == null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "测评原题已不存在");
        }
        if (correct) {
            wrongQuestionService.removeOnCorrect(userId, question.getId());
        } else {
            wrongQuestionService.addWrongQuestion(userId, question.getId(), item.getUserAnswer());
        }
        repetitionService.addToReviewPlan(userId, question.getId());
        eventService.recordQuestionAnswer(userId, question, "STAGE_ASSESSMENT_ANSWERED",
                "STAGE_ASSESSMENT", item.getId(), correct, answeredTime);
    }
}
