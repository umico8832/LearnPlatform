package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.ExamSubmitRequest;
import com.learnplatform.entity.ExamAnswer;
import com.learnplatform.entity.ExamPaper;
import com.learnplatform.entity.ExamQuestion;
import com.learnplatform.entity.ExamRecord;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.mapper.ExamAnswerMapper;
import com.learnplatform.mapper.ExamQuestionMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Applies per-question automatic grading and its learning side effects. */
@Service
public class ExamAnswerSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(ExamAnswerSubmissionService.class);

    private final ExamAnswerMapper examAnswerMapper;
    private final ExamQuestionMapper examQuestionMapper;
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final WrongQuestionService wrongQuestionService;
    private final AnswerEvaluator answerEvaluator;
    private final CourseLearningEventService courseLearningEventService;

    public ExamAnswerSubmissionService(ExamAnswerMapper examAnswerMapper,
                                       ExamQuestionMapper examQuestionMapper,
                                       QuestionMapper questionMapper,
                                       QuestionOptionMapper questionOptionMapper,
                                       WrongQuestionService wrongQuestionService,
                                       AnswerEvaluator answerEvaluator,
                                       CourseLearningEventService courseLearningEventService) {
        this.examAnswerMapper = examAnswerMapper;
        this.examQuestionMapper = examQuestionMapper;
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.wrongQuestionService = wrongQuestionService;
        this.answerEvaluator = answerEvaluator;
        this.courseLearningEventService = courseLearningEventService;
    }

    public GradingSummary gradeAndSave(ExamSubmitRequest request, ExamRecord record, ExamPaper paper, Long userId) {
        List<ExamQuestion> examQuestions = examQuestionMapper.selectList(new LambdaQueryWrapper<ExamQuestion>()
                .eq(ExamQuestion::getExamPaperId, paper.getId()));
        Map<Long, Integer> questionScoreMap = examQuestions.stream().collect(Collectors.toMap(
                ExamQuestion::getQuestionId, ExamQuestion::getScore, (first, ignored) -> first));
        int totalScore = examQuestions.stream().mapToInt(question ->
                question.getScore() != null ? question.getScore() : 1).sum();

        Map<Long, String> submittedAnswers = submittedAnswers(request, questionScoreMap);
        int earnedScore = 0;
        boolean hasPendingReview = false;
        if (request.getAnswers() != null) {
            for (ExamQuestion examQuestion : examQuestions) {
                Long questionId = examQuestion.getQuestionId();
                Question question = questionMapper.selectById(questionId);
                if (question == null) {
                    throw new BusinessException(ResultCode.NOT_FOUND, "试卷题目不存在");
                }
                int questionScore = questionScoreMap.getOrDefault(questionId, 1);
                String userAnswer = submittedAnswers.getOrDefault(questionId, "");
                List<QuestionOption> correctOptions = questionOptionMapper.selectList(
                                new LambdaQueryWrapper<QuestionOption>()
                                        .eq(QuestionOption::getQuestionId, question.getId())
                                        .orderByAsc(QuestionOption::getSortOrder))
                        .stream().filter(option -> option.getIsCorrect() != null && option.getIsCorrect() == 1)
                        .collect(Collectors.toList());
                String correctAnswer = answerEvaluator.buildCorrectAnswer(correctOptions, question.getQuestionType());
                boolean manualGrading = "SHORT_ANSWER".equals(question.getQuestionType());
                boolean correct = !manualGrading && answerEvaluator.isCorrect(question.getQuestionType(),
                        userAnswer != null ? userAnswer.trim() : "", correctAnswer);
                if (correct) {
                    earnedScore += questionScore;
                }
                if (manualGrading) {
                    hasPendingReview = true;
                }

                ExamAnswer examAnswer = new ExamAnswer();
                examAnswer.setExamRecordId(record.getId());
                examAnswer.setQuestionId(questionId);
                examAnswer.setUserAnswer(userAnswer != null ? userAnswer : "");
                examAnswer.setIsCorrect(manualGrading ? null : (correct ? 1 : 0));
                examAnswer.setScore(manualGrading ? null : (correct ? questionScore : 0));
                examAnswer.setGradingStatus(manualGrading ? "PENDING" : "AUTO_GRADED");
                examAnswerMapper.insert(examAnswer);
                if (!manualGrading && courseLearningEventService != null) {
                    courseLearningEventService.recordQuestionAnswer(userId, question, "EXAM_ANSWERED", "EXAM",
                            examAnswer.getId(), correct, examAnswer.getCreateTime());
                }
                updateWrongQuestion(userId, questionId, userAnswer, manualGrading, correct);
            }
        }
        return new GradingSummary(earnedScore, totalScore, hasPendingReview);
    }

    private Map<Long, String> submittedAnswers(ExamSubmitRequest request, Map<Long, Integer> questionScoreMap) {
        Map<Long, String> submittedAnswers = new HashMap<>();
        for (ExamSubmitRequest.AnswerItem answerItem : request.getAnswers()) {
            Long questionId = answerItem.getQuestionId();
            if (questionId == null || !questionScoreMap.containsKey(questionId)) {
                throw new BusinessException(ResultCode.VALIDATION_ERROR, "提交内容包含非本试卷题目");
            }
            if (submittedAnswers.put(questionId, answerItem.getUserAnswer()) != null) {
                throw new BusinessException(ResultCode.VALIDATION_ERROR, "同一道题不能重复提交");
            }
        }
        return submittedAnswers;
    }

    private void updateWrongQuestion(Long userId, Long questionId, String userAnswer,
                                     boolean manualGrading, boolean correct) {
        try {
            if (manualGrading) {
                return;
            }
            if (correct) {
                wrongQuestionService.removeOnCorrect(userId, questionId);
            } else {
                wrongQuestionService.addWrongQuestion(userId, questionId, userAnswer);
            }
        } catch (Exception exception) {
            log.warn("考试错题本处理失败: {}", exception.getMessage());
        }
    }

    record GradingSummary(int earnedScore, int totalScore, boolean hasPendingReview) {
    }
}
