package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.service.question.AutomaticGradingPolicy;
import com.learnplatform.service.question.QuestionAccessPolicy;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.ReviewSubmitRequest;
import com.learnplatform.dto.ReviewAnswerOutcome;
import com.learnplatform.dto.GamificationRewardFeedback;
import com.learnplatform.entity.PracticeRecord;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.mapper.PracticeRecordMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReviewAnswerRecordingService {

    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final PracticeRecordMapper practiceRecordMapper;
    private final WrongQuestionService wrongQuestionService;
    private final AnswerEvaluator answerEvaluator;
    private final CourseLearningEventService courseLearningEventService;

    public ReviewAnswerRecordingService(QuestionMapper questionMapper,
                                        QuestionOptionMapper questionOptionMapper,
                                        PracticeRecordMapper practiceRecordMapper,
                                        WrongQuestionService wrongQuestionService,
                                        AnswerEvaluator answerEvaluator,
                                        CourseLearningEventService courseLearningEventService) {
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.practiceRecordMapper = practiceRecordMapper;
        this.wrongQuestionService = wrongQuestionService;
        this.answerEvaluator = answerEvaluator;
        this.courseLearningEventService = courseLearningEventService;
    }

    public boolean evaluateAndRecord(ReviewSubmitRequest request, Long userId) {
        return evaluateAndRecordWithReward(request, userId).correct();
    }

    public ReviewAnswerOutcome evaluateAndRecordWithReward(ReviewSubmitRequest request, Long userId) {
        Long questionId = request.getQuestionId();
        Question question = questionMapper.selectById(questionId);
        if (!QuestionAccessPolicy.canAccess(question, userId)
                || !Integer.valueOf(1).equals(question.getStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "题目不存在");
        }

        LambdaQueryWrapper<QuestionOption> optionQuery = new LambdaQueryWrapper<>();
        optionQuery.eq(QuestionOption::getQuestionId, questionId)
                .orderByAsc(QuestionOption::getSortOrder);
        List<QuestionOption> correctOptions = questionOptionMapper.selectList(optionQuery).stream()
                .filter(option -> option.getIsCorrect() != null && option.getIsCorrect() == 1)
                .collect(Collectors.toList());
        AutomaticGradingPolicy.requireBasis(question.getQuestionType(), correctOptions);
        String correctAnswer = answerEvaluator.buildCorrectAnswer(correctOptions, question.getQuestionType());
        boolean correct = answerEvaluator.isCorrect(
                question.getQuestionType(), request.getUserAnswer(), correctAnswer);

        PracticeRecord record = new PracticeRecord();
        record.setUserId(userId);
        record.setQuestionId(questionId);
        record.setUserAnswer(request.getUserAnswer().trim());
        record.setIsCorrect(correct ? 1 : 0);
        record.setAnswerTime(request.getAnswerTime());
        practiceRecordMapper.insert(record);
        GamificationRewardFeedback reward = null;
        if (courseLearningEventService != null) {
            reward = courseLearningEventService.recordQuestionAnswer(
                    userId, question, "REVIEW_ANSWERED", "REVIEW",
                    record.getId(), correct, record.getCreateTime());
        }

        if (correct) {
            wrongQuestionService.removeOnCorrect(userId, questionId);
        } else {
            wrongQuestionService.addWrongQuestion(userId, questionId, request.getUserAnswer().trim());
        }
        return new ReviewAnswerOutcome(correct, record.getId(), reward);
    }
}
