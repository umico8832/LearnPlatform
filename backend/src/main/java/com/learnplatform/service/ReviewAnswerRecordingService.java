package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.ReviewSubmitRequest;
import com.learnplatform.entity.PracticeRecord;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.entity.WrongQuestion;
import com.learnplatform.mapper.PracticeRecordMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import com.learnplatform.mapper.WrongQuestionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReviewAnswerRecordingService {

    private static final Logger log = LoggerFactory.getLogger(ReviewAnswerRecordingService.class);

    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final PracticeRecordMapper practiceRecordMapper;
    private final WrongQuestionMapper wrongQuestionMapper;
    private final AnswerEvaluator answerEvaluator;
    private final CourseLearningEventService courseLearningEventService;

    public ReviewAnswerRecordingService(QuestionMapper questionMapper,
                                        QuestionOptionMapper questionOptionMapper,
                                        PracticeRecordMapper practiceRecordMapper,
                                        WrongQuestionMapper wrongQuestionMapper,
                                        AnswerEvaluator answerEvaluator,
                                        CourseLearningEventService courseLearningEventService) {
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.practiceRecordMapper = practiceRecordMapper;
        this.wrongQuestionMapper = wrongQuestionMapper;
        this.answerEvaluator = answerEvaluator;
        this.courseLearningEventService = courseLearningEventService;
    }

    public boolean evaluateAndRecord(ReviewSubmitRequest request, Long userId) {
        Long questionId = request.getQuestionId();
        Question question = questionMapper.selectById(questionId);
        if (question == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "题目不存在");
        }

        LambdaQueryWrapper<QuestionOption> optionQuery = new LambdaQueryWrapper<>();
        optionQuery.eq(QuestionOption::getQuestionId, questionId)
                .orderByAsc(QuestionOption::getSortOrder);
        List<QuestionOption> correctOptions = questionOptionMapper.selectList(optionQuery).stream()
                .filter(option -> option.getIsCorrect() != null && option.getIsCorrect() == 1)
                .collect(Collectors.toList());
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
        if (courseLearningEventService != null) {
            courseLearningEventService.recordQuestionAnswer(
                    userId, question, "REVIEW_ANSWERED", "REVIEW",
                    record.getId(), correct, record.getCreateTime());
        }

        updateWrongQuestion(userId, questionId, request.getUserAnswer().trim(), correct);
        return correct;
    }

    private void updateWrongQuestion(Long userId, Long questionId, String userAnswer, boolean correct) {
        try {
            LambdaQueryWrapper<WrongQuestion> query = new LambdaQueryWrapper<>();
            query.eq(WrongQuestion::getUserId, userId)
                    .eq(WrongQuestion::getQuestionId, questionId);
            WrongQuestion existing = wrongQuestionMapper.selectOne(query);
            if (correct) {
                if (existing != null) {
                    wrongQuestionMapper.deleteById(existing.getId());
                }
                return;
            }
            if (existing != null) {
                existing.setWrongCount(existing.getWrongCount() + 1);
                existing.setLastWrongAnswer(userAnswer);
                if (existing.getMasteryLevel() != null && existing.getMasteryLevel() == 2) {
                    existing.setMasteryLevel(0);
                }
                wrongQuestionMapper.updateById(existing);
                return;
            }
            WrongQuestion wrongQuestion = new WrongQuestion();
            wrongQuestion.setUserId(userId);
            wrongQuestion.setQuestionId(questionId);
            wrongQuestion.setWrongCount(1);
            wrongQuestion.setMasteryLevel(0);
            wrongQuestion.setLastWrongAnswer(userAnswer);
            wrongQuestion.setDeleted(0);
            wrongQuestionMapper.insert(wrongQuestion);
        } catch (Exception exception) {
            log.warn("更新复习错题记录失败: {}", exception.getMessage());
        }
    }
}
