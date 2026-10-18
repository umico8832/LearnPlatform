package com.learnplatform.service;

import com.learnplatform.common.time.BusinessTime;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.ReviewScheduleVO;
import com.learnplatform.dto.ReviewSubmitRequest;
import com.learnplatform.entity.QuestionReviewSchedule;
import com.learnplatform.entity.WrongQuestion;
import com.learnplatform.mapper.QuestionReviewScheduleMapper;
import com.learnplatform.mapper.WrongQuestionMapper;
import com.learnplatform.service.review.FsrsReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 复习计划写入入口；到期日期由 FSRS 评分服务更新。 */
@Service
public class SpacedRepetitionService {

    private static final Logger log = LoggerFactory.getLogger(SpacedRepetitionService.class);

    /** 未评级卡片与存量卡片仍保留原表的兼容列默认值。 */
    private static final BigDecimal DEFAULT_EASE_FACTOR = new BigDecimal("2.50");

    private final QuestionReviewScheduleMapper reviewScheduleMapper;
    private final WrongQuestionMapper wrongQuestionMapper;
    private final ReviewAnswerRecordingService reviewAnswerRecordingService;
    private final CacheEvictService cacheEvictService;
    private final ReviewScheduleQueryService reviewScheduleQueryService;
    private final FsrsReviewService fsrs;

    public SpacedRepetitionService(QuestionReviewScheduleMapper reviewScheduleMapper,
                                    WrongQuestionMapper wrongQuestionMapper,
                                    ReviewAnswerRecordingService reviewAnswerRecordingService,
                                    CacheEvictService cacheEvictService,
                                    ReviewScheduleQueryService reviewScheduleQueryService,
                                    FsrsReviewService fsrs) {
        this.reviewScheduleMapper = reviewScheduleMapper;
        this.wrongQuestionMapper = wrongQuestionMapper;
        this.reviewAnswerRecordingService = reviewAnswerRecordingService;
        this.cacheEvictService = cacheEvictService;
        this.reviewScheduleQueryService = reviewScheduleQueryService;
        this.fsrs = fsrs;
    }

    /**
     * 将题目加入复习计划（如果已存在则忽略）
     */
    public void addToReviewPlan(Long userId, Long questionId) {
        int changed = reviewScheduleMapper.insertOrRestore(
                userId, questionId, DEFAULT_EASE_FACTOR, BusinessTime.today());
        if (changed > 0) {
            log.info("题目加入复习计划: userId={}, questionId={}", userId, questionId);
        }
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
                int changed = reviewScheduleMapper.insertOrRestore(
                        userId, wq.getQuestionId(), DEFAULT_EASE_FACTOR, BusinessTime.today());
                if (changed > 0) {
                    syncedCount++;
                }
            }
        }

        if (syncedCount > 0) {
            log.info("同步错题到复习计划: userId={}, 新增 {} 道错题", userId, syncedCount);
        }
        return syncedCount;
    }

    /** 提交独立复习作答并更新 FSRS 排期。 */
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
        QuestionReviewSchedule schedule = reviewScheduleMapper.selectByUserAndQuestionForUpdate(userId, questionId);
        if (schedule == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "题目不在复习计划中");
        }

        com.learnplatform.dto.ReviewAnswerOutcome outcome = reviewAnswerRecordingService
                .evaluateAndRecordWithReward(request, userId);
        boolean isCorrect = outcome.correct();
        schedule = fsrs.score(userId, questionId, FsrsReviewService.Source.REVIEW,
                outcome.recordId(), isCorrect, request.getSelfAssessedQuality());

        // 清除统计缓存
        cacheEvictService.evictUserStatistics(userId);

        log.info("复习答题完成: userId={}, questionId={}, isCorrect={}, newInterval={}d",
                userId, questionId, isCorrect, schedule.getIntervalDays());

        ReviewScheduleVO view = reviewScheduleQueryService.buildScheduleView(schedule);
        view.setReward(outcome.reward());
        view.setCorrect(isCorrect);
        return view;
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
        schedule.setNextReviewDate(BusinessTime.today());
        schedule.setLastReviewDate(null);
        schedule.setLastQuality(null);
        schedule.setTotalReviews(0);
        reviewScheduleMapper.updateById(schedule);
        fsrs.reset(schedule.getId());
    }

}
