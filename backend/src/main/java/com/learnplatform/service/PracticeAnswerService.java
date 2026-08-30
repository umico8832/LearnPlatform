package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.PracticeResultVO;
import com.learnplatform.dto.PracticeSubmitRequest;
import com.learnplatform.entity.PracticeRecord;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.mapper.PracticeRecordMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import com.learnplatform.service.question.QuestionAccessPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/** 执行练习判分，并在同一业务入口写入练习事实及后续学习状态。 */
@Service
public class PracticeAnswerService {

    private static final Logger log = LoggerFactory.getLogger(PracticeAnswerService.class);

    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final PracticeRecordMapper practiceRecordMapper;
    private final WrongQuestionService wrongQuestionService;
    private final AnswerEvaluator answerEvaluator;
    private final CacheEvictService cacheEvictService;
    private final SpacedRepetitionService spacedRepetitionService;
    private final CourseLearningEventService courseLearningEventService;

    public PracticeAnswerService(QuestionMapper questionMapper,
                                 QuestionOptionMapper questionOptionMapper,
                                 PracticeRecordMapper practiceRecordMapper,
                                 WrongQuestionService wrongQuestionService,
                                 AnswerEvaluator answerEvaluator,
                                 CacheEvictService cacheEvictService,
                                 SpacedRepetitionService spacedRepetitionService,
                                 CourseLearningEventService courseLearningEventService) {
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.practiceRecordMapper = practiceRecordMapper;
        this.wrongQuestionService = wrongQuestionService;
        this.answerEvaluator = answerEvaluator;
        this.cacheEvictService = cacheEvictService;
        this.spacedRepetitionService = spacedRepetitionService;
        this.courseLearningEventService = courseLearningEventService;
    }

    @Transactional
    public PracticeResultVO submitAnswer(PracticeSubmitRequest request, Long userId) {
        log.info("提交练习答案: userId={}, questionId={}", userId, request.getQuestionId());
        validateRequest(request);
        Question question = questionMapper.selectById(request.getQuestionId());
        if (!QuestionAccessPolicy.canAccess(question, userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "题目不存在");
        }

        LambdaQueryWrapper<QuestionOption> optionWrapper = new LambdaQueryWrapper<>();
        optionWrapper.eq(QuestionOption::getQuestionId, question.getId())
                .orderByAsc(QuestionOption::getSortOrder);
        List<QuestionOption> correctOptions = questionOptionMapper.selectList(optionWrapper).stream()
                .filter(option -> option.getIsCorrect() != null && option.getIsCorrect() == 1)
                .collect(Collectors.toList());
        String correctAnswer = answerEvaluator.buildCorrectAnswer(
                correctOptions, question.getQuestionType());
        String userAnswer = request.getUserAnswer().trim();
        boolean correct = answerEvaluator.isCorrect(
                question.getQuestionType(), userAnswer, correctAnswer);
        log.info("判分结果: userId={}, questionId={}, isCorrect={}",
                userId, request.getQuestionId(), correct);

        PracticeRecord record = saveRecord(request, userId, userAnswer, correct);
        if (courseLearningEventService != null) {
            courseLearningEventService.recordQuestionAnswer(
                    userId, question, "PRACTICE_ANSWERED", "PRACTICE",
                    record.getId(), correct, record.getCreateTime());
        }
        updateWrongQuestion(userId, request.getQuestionId(), userAnswer, correct);
        cacheEvictService.evictUserStatistics(userId);
        addReviewPlan(userId, request.getQuestionId());
        return buildResult(question, record, userAnswer, correctAnswer, correct);
    }

    private void validateRequest(PracticeSubmitRequest request) {
        if (request.getQuestionId() == null) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "题目ID不能为空");
        }
        if (request.getUserAnswer() == null || request.getUserAnswer().trim().isEmpty()) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "答案不能为空");
        }
    }

    private PracticeRecord saveRecord(PracticeSubmitRequest request, Long userId,
                                      String userAnswer, boolean correct) {
        PracticeRecord record = new PracticeRecord();
        record.setUserId(userId);
        record.setQuestionId(request.getQuestionId());
        record.setUserAnswer(userAnswer);
        record.setIsCorrect(correct ? 1 : 0);
        record.setAnswerTime(request.getAnswerTime());
        practiceRecordMapper.insert(record);
        return record;
    }

    private void updateWrongQuestion(Long userId, Long questionId, String userAnswer, boolean correct) {
        try {
            if (correct) {
                wrongQuestionService.removeOnCorrect(userId, questionId);
            } else {
                wrongQuestionService.addWrongQuestion(userId, questionId, userAnswer);
            }
        } catch (Exception exception) {
            log.warn(correct ? "移出错题本失败: {}" : "加入错题本失败: {}", exception.getMessage());
        }
    }

    private void addReviewPlan(Long userId, Long questionId) {
        try {
            spacedRepetitionService.addToReviewPlan(userId, questionId);
        } catch (Exception exception) {
            log.warn("加入复习计划失败: {}", exception.getMessage());
        }
    }

    private PracticeResultVO buildResult(Question question, PracticeRecord record,
                                         String userAnswer, String correctAnswer, boolean correct) {
        PracticeResultVO result = new PracticeResultVO();
        result.setRecordId(record.getId());
        result.setQuestionId(question.getId());
        result.setUserAnswer(userAnswer);
        result.setCorrect(correct);
        result.setCorrectAnswer(correctAnswer);
        result.setAnalysis(question.getAnalysis());
        result.setScore(question.getScore());
        return result;
    }
}
