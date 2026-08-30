package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.ReviewScheduleVO;
import com.learnplatform.dto.ReviewSubmitRequest;
import com.learnplatform.entity.PracticeRecord;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.entity.QuestionReviewSchedule;
import com.learnplatform.entity.WrongQuestion;
import com.learnplatform.mapper.PracticeRecordMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import com.learnplatform.mapper.QuestionReviewScheduleMapper;
import com.learnplatform.mapper.WrongQuestionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 间隔重复复习服务（SM-2 算法）
 *
 * SM-2 核心公式：
 * - quality >= 3: interval(n) = 1, 6, interval(n-1) * EF
 * - quality < 3: 重置 repetitions 为 0，interval 重新从 1 天开始
 * - EF = EF + (0.1 - (5-q) * (0.08 + (5-q) * 0.02))
 * - EF 最低 1.30
 *
 * 质量评分映射（用户答题结果 → 0-5）：
 * - 答对且快速: 5
 * - 答对但犹豫: 4
 * - 答对但困难: 3
 * - 答错但看到答案理解: 2
 * - 答错但模糊记得: 1
 * - 完全不记得: 0
 */
@Service
public class SpacedRepetitionService {

    private static final Logger log = LoggerFactory.getLogger(SpacedRepetitionService.class);

    /** 新卡片初始间隔天数 */
    private static final int INITIAL_INTERVAL = 1;

    /** 第二次成功复习间隔天数 */
    private static final int SECOND_INTERVAL = 6;

    /** 最低简易因子 */
    private static final BigDecimal MIN_EASE_FACTOR = new BigDecimal("1.30");

    /** 默认初始简易因子 */
    private static final BigDecimal DEFAULT_EASE_FACTOR = new BigDecimal("2.50");

    private final QuestionReviewScheduleMapper reviewScheduleMapper;
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final PracticeRecordMapper practiceRecordMapper;
    private final WrongQuestionMapper wrongQuestionMapper;
    private final AnswerEvaluator answerEvaluator;
    private final CacheEvictService cacheEvictService;
    private final CourseLearningEventService courseLearningEventService;
    private final ReviewScheduleQueryService reviewScheduleQueryService;

    public SpacedRepetitionService(QuestionReviewScheduleMapper reviewScheduleMapper,
                                    QuestionMapper questionMapper,
                                    QuestionOptionMapper questionOptionMapper,
                                    PracticeRecordMapper practiceRecordMapper,
                                    WrongQuestionMapper wrongQuestionMapper,
                                    AnswerEvaluator answerEvaluator,
                                    CacheEvictService cacheEvictService,
                                    CourseLearningEventService courseLearningEventService,
                                    ReviewScheduleQueryService reviewScheduleQueryService) {
        this.reviewScheduleMapper = reviewScheduleMapper;
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.practiceRecordMapper = practiceRecordMapper;
        this.wrongQuestionMapper = wrongQuestionMapper;
        this.answerEvaluator = answerEvaluator;
        this.cacheEvictService = cacheEvictService;
        this.courseLearningEventService = courseLearningEventService;
        this.reviewScheduleQueryService = reviewScheduleQueryService;
    }

    /**
     * 将题目加入复习计划（如果已存在则忽略）
     */
    public void addToReviewPlan(Long userId, Long questionId) {
        LambdaQueryWrapper<QuestionReviewSchedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionReviewSchedule::getUserId, userId)
               .eq(QuestionReviewSchedule::getQuestionId, questionId);
        if (reviewScheduleMapper.selectCount(wrapper) > 0) {
            return; // 已在复习计划中
        }

        QuestionReviewSchedule schedule = new QuestionReviewSchedule();
        schedule.setUserId(userId);
        schedule.setQuestionId(questionId);
        schedule.setEaseFactor(DEFAULT_EASE_FACTOR);
        schedule.setIntervalDays(0);
        schedule.setRepetitions(0);
        schedule.setNextReviewDate(LocalDate.now()); // 新卡片今天就可以复习
        schedule.setTotalReviews(0);
        reviewScheduleMapper.insert(schedule);

        log.info("题目加入复习计划: userId={}, questionId={}", userId, questionId);
    }

    /**
     * 将错题本中未掌握/部分掌握的题目同步到复习计划
     * 返回新同步的题目数量
     */
    @Transactional
    public int syncWrongQuestionsToReviewPlan(Long userId) {
        // 1. 查询错题本中未掌握(masteryLevel=0)和部分掌握(masteryLevel=1)的题目
        LambdaQueryWrapper<WrongQuestion> wqWrapper = new LambdaQueryWrapper<>();
        wqWrapper.eq(WrongQuestion::getUserId, userId)
                 .in(WrongQuestion::getMasteryLevel, 0, 1);
        List<WrongQuestion> wrongQuestions = wrongQuestionMapper.selectList(wqWrapper);

        if (wrongQuestions.isEmpty()) {
            log.info("同步错题到复习计划: userId={}, 无符合条件的错题", userId);
            return 0;
        }

        // 2. 查询已在复习计划中的题目ID集合
        LambdaQueryWrapper<QuestionReviewSchedule> rsWrapper = new LambdaQueryWrapper<>();
        rsWrapper.eq(QuestionReviewSchedule::getUserId, userId);
        Set<Long> existingQuestionIds = reviewScheduleMapper.selectList(rsWrapper).stream()
                .map(QuestionReviewSchedule::getQuestionId)
                .collect(Collectors.toSet());

        // 3. 过滤出不在复习计划中的错题并添加
        int syncedCount = 0;
        for (WrongQuestion wq : wrongQuestions) {
            if (!existingQuestionIds.contains(wq.getQuestionId())) {
                QuestionReviewSchedule schedule = new QuestionReviewSchedule();
                schedule.setUserId(userId);
                schedule.setQuestionId(wq.getQuestionId());
                schedule.setEaseFactor(DEFAULT_EASE_FACTOR);
                schedule.setIntervalDays(0);
                schedule.setRepetitions(0);
                schedule.setNextReviewDate(LocalDate.now());
                schedule.setTotalReviews(0);
                reviewScheduleMapper.insert(schedule);
                syncedCount++;
            }
        }

        if (syncedCount > 0) {
            log.info("同步错题到复习计划: userId={}, 新增 {} 道错题", userId, syncedCount);
        }
        return syncedCount;
    }

    /**
     * 提交复习答题并更新 SM-2 调度
     */
    @Transactional
    public ReviewScheduleVO submitReview(ReviewSubmitRequest request, Long userId) {
        Long questionId = request.getQuestionId();
        if (questionId == null) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "题目ID不能为空");
        }
        if (request.getUserAnswer() == null || request.getUserAnswer().trim().isEmpty()) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "答案不能为空");
        }

        // 查找复习计划
        LambdaQueryWrapper<QuestionReviewSchedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionReviewSchedule::getUserId, userId)
               .eq(QuestionReviewSchedule::getQuestionId, questionId);
        QuestionReviewSchedule schedule = reviewScheduleMapper.selectOne(wrapper);
        if (schedule == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "题目不在复习计划中");
        }

        // 获取题目并判分
        Question question = questionMapper.selectById(questionId);
        if (question == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "题目不存在");
        }

        LambdaQueryWrapper<QuestionOption> optWrapper = new LambdaQueryWrapper<>();
        optWrapper.eq(QuestionOption::getQuestionId, questionId)
                  .orderByAsc(QuestionOption::getSortOrder);
        List<QuestionOption> allOptions = questionOptionMapper.selectList(optWrapper);
        List<QuestionOption> correctOptions = allOptions.stream()
                .filter(o -> o.getIsCorrect() != null && o.getIsCorrect() == 1)
                .collect(Collectors.toList());
        String correctAnswer = answerEvaluator.buildCorrectAnswer(correctOptions, question.getQuestionType());
        boolean isCorrect = answerEvaluator.isCorrect(question.getQuestionType(), request.getUserAnswer(),
                correctAnswer);

        // 保存答题记录
        PracticeRecord record = new PracticeRecord();
        record.setUserId(userId);
        record.setQuestionId(questionId);
        record.setUserAnswer(request.getUserAnswer().trim());
        record.setIsCorrect(isCorrect ? 1 : 0);
        record.setAnswerTime(request.getAnswerTime());
        practiceRecordMapper.insert(record);
        if (courseLearningEventService != null) {
            courseLearningEventService.recordQuestionAnswer(userId, question, "REVIEW_ANSWERED", "REVIEW",
                    record.getId(), isCorrect, record.getCreateTime());
        }

        // 处理错题本
        if (isCorrect) {
            try {
                LambdaQueryWrapper<WrongQuestion> wqWrapper = new LambdaQueryWrapper<>();
                wqWrapper.eq(WrongQuestion::getUserId, userId)
                         .eq(WrongQuestion::getQuestionId, questionId);
                WrongQuestion existing = wrongQuestionMapper.selectOne(wqWrapper);
                if (existing != null) {
                    wrongQuestionMapper.deleteById(existing.getId());
                }
            } catch (Exception e) {
                log.warn("移出错题本失败: {}", e.getMessage());
            }
        } else {
            try {
                LambdaQueryWrapper<WrongQuestion> wqWrapper = new LambdaQueryWrapper<>();
                wqWrapper.eq(WrongQuestion::getUserId, userId)
                         .eq(WrongQuestion::getQuestionId, questionId);
                WrongQuestion existing = wrongQuestionMapper.selectOne(wqWrapper);
                if (existing != null) {
                    existing.setWrongCount(existing.getWrongCount() + 1);
                    existing.setLastWrongAnswer(request.getUserAnswer().trim());
                    if (existing.getMasteryLevel() != null && existing.getMasteryLevel() == 2) {
                        existing.setMasteryLevel(0);
                    }
                    wrongQuestionMapper.updateById(existing);
                } else {
                    WrongQuestion wq = new WrongQuestion();
                    wq.setUserId(userId);
                    wq.setQuestionId(questionId);
                    wq.setWrongCount(1);
                    wq.setMasteryLevel(0);
                    wq.setLastWrongAnswer(request.getUserAnswer().trim());
                    wq.setDeleted(0);
                    wrongQuestionMapper.insert(wq);
                }
            } catch (Exception e) {
                log.warn("加入错题本失败: {}", e.getMessage());
            }
        }

        // 计算 SM-2 质量评分
        int quality = calculateQuality(isCorrect, request.getSelfAssessedQuality());

        // 应用 SM-2 算法更新调度
        applySM2(schedule, quality);
        reviewScheduleMapper.updateById(schedule);

        // 清除统计缓存
        cacheEvictService.evictUserStatistics(userId);

        log.info("复习答题完成: userId={}, questionId={}, isCorrect={}, quality={}, newInterval={}d",
                userId, questionId, isCorrect, quality, schedule.getIntervalDays());

        return reviewScheduleQueryService.buildScheduleView(schedule);
    }

    /**
     * 从复习计划中移除题目
     */
    public void removeFromReviewPlan(Long userId, Long questionId) {
        LambdaQueryWrapper<QuestionReviewSchedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionReviewSchedule::getUserId, userId)
               .eq(QuestionReviewSchedule::getQuestionId, questionId);
        QuestionReviewSchedule schedule = reviewScheduleMapper.selectOne(wrapper);
        if (schedule != null) {
            reviewScheduleMapper.deleteById(schedule.getId());
            log.info("题目移出复习计划: userId={}, questionId={}", userId, questionId);
        }
    }

    /**
     * 重置复习进度（重新开始学习）
     */
    @Transactional
    public void resetReviewProgress(Long userId, Long questionId) {
        LambdaQueryWrapper<QuestionReviewSchedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionReviewSchedule::getUserId, userId)
               .eq(QuestionReviewSchedule::getQuestionId, questionId);
        QuestionReviewSchedule schedule = reviewScheduleMapper.selectOne(wrapper);
        if (schedule == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "题目不在复习计划中");
        }
        schedule.setEaseFactor(DEFAULT_EASE_FACTOR);
        schedule.setIntervalDays(0);
        schedule.setRepetitions(0);
        schedule.setNextReviewDate(LocalDate.now());
        schedule.setLastReviewDate(null);
        schedule.setLastQuality(null);
        schedule.setTotalReviews(0);
        reviewScheduleMapper.updateById(schedule);
    }

    // ========== SM-2 算法核心 ==========

    /**
     * 应用 SM-2 算法更新复习计划
     */
    void applySM2(QuestionReviewSchedule schedule, int quality) {
        LocalDate today = LocalDate.now();

        // 获取当前值
        int repetitions = schedule.getRepetitions() != null ? schedule.getRepetitions() : 0;
        BigDecimal ef = schedule.getEaseFactor() != null ? schedule.getEaseFactor() : DEFAULT_EASE_FACTOR;
        int interval = schedule.getIntervalDays() != null ? schedule.getIntervalDays() : 0;

        // 更新简易因子
        // EF' = EF + (0.1 - (5-q) * (0.08 + (5-q) * 0.02))
        int qDiff = 5 - quality;
        BigDecimal delta = new BigDecimal("0.1")
                .subtract(BigDecimal.valueOf(qDiff)
                        .multiply(new BigDecimal("0.08")
                                .add(BigDecimal.valueOf(qDiff).multiply(new BigDecimal("0.02")))));
        ef = ef.add(delta);

        // EF 最低 1.30
        if (ef.compareTo(MIN_EASE_FACTOR) < 0) {
            ef = MIN_EASE_FACTOR;
        }

        // 计算下次间隔
        if (quality >= 3) {
            // 答对
            repetitions++;
            if (repetitions == 1) {
                interval = INITIAL_INTERVAL;
            } else if (repetitions == 2) {
                interval = SECOND_INTERVAL;
            } else {
                // interval(n) = interval(n-1) * EF
                interval = BigDecimal.valueOf(interval)
                        .multiply(ef)
                        .setScale(0, RoundingMode.CEILING)
                        .intValue();
            }
        } else {
            // 答错：重置
            repetitions = 0;
            interval = INITIAL_INTERVAL;
        }

        // 更新实体
        schedule.setRepetitions(repetitions);
        schedule.setEaseFactor(ef);
        schedule.setIntervalDays(interval);
        schedule.setNextReviewDate(today.plusDays(interval));
        schedule.setLastReviewDate(today);
        schedule.setLastQuality(quality);
        schedule.setTotalReviews((schedule.getTotalReviews() != null ? schedule.getTotalReviews() : 0) + 1);
    }

    /**
     * 计算质量评分 (0-5)
     * 优先使用用户自评，否则根据答题结果自动映射
     */
    int calculateQuality(boolean isCorrect, Integer selfAssessedQuality) {
        if (selfAssessedQuality != null && selfAssessedQuality >= 0 && selfAssessedQuality <= 5) {
            return selfAssessedQuality;
        }
        // 默认映射：答对=4（稍有犹豫），答错=1（答错但还记得部分）
        return isCorrect ? 4 : 1;
    }

}
