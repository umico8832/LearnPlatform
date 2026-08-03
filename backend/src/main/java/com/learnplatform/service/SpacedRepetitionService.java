package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.*;
import com.learnplatform.entity.*;
import com.learnplatform.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
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

    /** 已掌握阈值（间隔天数 >= 21 视为已掌握） */
    private static final int MASTERED_THRESHOLD_DAYS = 21;

    /** 困难卡片简易因子阈值 */
    private static final BigDecimal DIFFICULT_THRESHOLD = new BigDecimal("2.00");

    private final QuestionReviewScheduleMapper reviewScheduleMapper;
    private final QuestionMapper questionMapper;
    private final CourseMapper courseMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final PracticeRecordMapper practiceRecordMapper;
    private final WrongQuestionMapper wrongQuestionMapper;
    private final AnswerEvaluator answerEvaluator;
    private final CacheEvictService cacheEvictService;
    private final CourseLearningEventService courseLearningEventService;

    public SpacedRepetitionService(QuestionReviewScheduleMapper reviewScheduleMapper,
                                    QuestionMapper questionMapper,
                                    CourseMapper courseMapper,
                                    QuestionOptionMapper questionOptionMapper,
                                    PracticeRecordMapper practiceRecordMapper,
                                    WrongQuestionMapper wrongQuestionMapper,
                                    AnswerEvaluator answerEvaluator,
                                    CacheEvictService cacheEvictService) {
        this(reviewScheduleMapper, questionMapper, courseMapper, questionOptionMapper, practiceRecordMapper,
                wrongQuestionMapper, answerEvaluator, cacheEvictService, null);
    }

    @Autowired
    public SpacedRepetitionService(QuestionReviewScheduleMapper reviewScheduleMapper,
                                    QuestionMapper questionMapper,
                                    CourseMapper courseMapper,
                                    QuestionOptionMapper questionOptionMapper,
                                    PracticeRecordMapper practiceRecordMapper,
                                    WrongQuestionMapper wrongQuestionMapper,
                                    AnswerEvaluator answerEvaluator,
                                    CacheEvictService cacheEvictService,
                                    CourseLearningEventService courseLearningEventService) {
        this.reviewScheduleMapper = reviewScheduleMapper;
        this.questionMapper = questionMapper;
        this.courseMapper = courseMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.practiceRecordMapper = practiceRecordMapper;
        this.wrongQuestionMapper = wrongQuestionMapper;
        this.answerEvaluator = answerEvaluator;
        this.cacheEvictService = cacheEvictService;
        this.courseLearningEventService = courseLearningEventService;
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
     * 获取今日待复习题目（含逾期）
     */
    public List<ReviewScheduleVO> getDueReviewCards(Long userId, Long courseId, int limit) {
        if (limit <= 0 || limit > 50) {
            limit = 20;
        }

        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<QuestionReviewSchedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionReviewSchedule::getUserId, userId)
               .le(QuestionReviewSchedule::getNextReviewDate, today)
               .orderByAsc(QuestionReviewSchedule::getNextReviewDate) // 逾期最多的优先
               .orderByDesc(QuestionReviewSchedule::getEaseFactor);   // 简单的排后面
        wrapper.last("LIMIT " + limit);

        List<QuestionReviewSchedule> schedules = reviewScheduleMapper.selectList(wrapper);
        return fillScheduleVOs(schedules, today);
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
        boolean isCorrect = answerEvaluator.isCorrect(question.getQuestionType(), request.getUserAnswer(), correctAnswer);

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

        // 构建返回 VO
        List<ReviewScheduleVO> vos = fillScheduleVOs(List.of(schedule), LocalDate.now());
        return vos.isEmpty() ? null : vos.get(0);
    }

    /**
     * 获取复习统计概览
     */
    public ReviewStatsVO getReviewStats(Long userId) {
        LocalDate today = LocalDate.now();
        ReviewStatsVO stats = new ReviewStatsVO();

        // 总卡片数
        LambdaQueryWrapper<QuestionReviewSchedule> allWrapper = new LambdaQueryWrapper<>();
        allWrapper.eq(QuestionReviewSchedule::getUserId, userId);
        long totalCards = reviewScheduleMapper.selectCount(allWrapper);
        stats.setTotalCards((int) totalCards);

        if (totalCards == 0) {
            return stats;
        }

        // 今日待复习（今天及之前，排除今天已复习的）
        LambdaQueryWrapper<QuestionReviewSchedule> dueWrapper = new LambdaQueryWrapper<>();
        dueWrapper.eq(QuestionReviewSchedule::getUserId, userId)
                  .le(QuestionReviewSchedule::getNextReviewDate, today);
        Long dueToday = reviewScheduleMapper.selectCount(dueWrapper);
        stats.setDueToday(dueToday != null ? dueToday.intValue() : 0);

        // 逾期（昨天及之前到期但未复习的）
        LambdaQueryWrapper<QuestionReviewSchedule> overdueWrapper = new LambdaQueryWrapper<>();
        overdueWrapper.eq(QuestionReviewSchedule::getUserId, userId)
                      .lt(QuestionReviewSchedule::getNextReviewDate, today);
        Long overdueCount = reviewScheduleMapper.selectCount(overdueWrapper);
        stats.setOverdue(overdueCount != null ? overdueCount.intValue() : 0);

        // 今日已完成（lastReviewDate == today）
        LambdaQueryWrapper<QuestionReviewSchedule> doneWrapper = new LambdaQueryWrapper<>();
        doneWrapper.eq(QuestionReviewSchedule::getUserId, userId)
                   .eq(QuestionReviewSchedule::getLastReviewDate, today);
        Long reviewedTodayCount = reviewScheduleMapper.selectCount(doneWrapper);
        stats.setReviewedToday(reviewedTodayCount != null ? reviewedTodayCount.intValue() : 0);

        // 获取所有卡片进行分类统计
        List<QuestionReviewSchedule> allCards = reviewScheduleMapper.selectList(allWrapper);
        int newCards = 0, learning = 0, mastered = 0, difficult = 0;
        BigDecimal totalEf = BigDecimal.ZERO;

        for (QuestionReviewSchedule card : allCards) {
            if (card.getTotalReviews() == null || card.getTotalReviews() == 0) {
                newCards++;
            } else if (card.getIntervalDays() != null && card.getIntervalDays() >= MASTERED_THRESHOLD_DAYS) {
                mastered++;
            } else {
                learning++;
            }
            if (card.getEaseFactor() != null && card.getEaseFactor().compareTo(DIFFICULT_THRESHOLD) < 0) {
                difficult++;
            }
            if (card.getEaseFactor() != null) {
                totalEf = totalEf.add(card.getEaseFactor());
            }
        }
        stats.setNewCards(newCards);
        stats.setLearningCards(learning);
        stats.setMasteredCards(mastered);
        stats.setDifficultCards(difficult);
        stats.setAvgEaseFactor(totalCards > 0 ? totalEf.divide(BigDecimal.valueOf(totalCards), 2, RoundingMode.HALF_UP).doubleValue() : 2.5);

        // 连续复习天数（从今天往回数，每天至少完成 1 题复习）
        int streak = 0;
        LocalDate checkDate = today;
        while (true) {
            LambdaQueryWrapper<QuestionReviewSchedule> streakWrapper = new LambdaQueryWrapper<>();
            streakWrapper.eq(QuestionReviewSchedule::getUserId, userId)
                         .eq(QuestionReviewSchedule::getLastReviewDate, checkDate);
            if (reviewScheduleMapper.selectCount(streakWrapper) > 0) {
                streak++;
                checkDate = checkDate.minusDays(1);
            } else {
                break;
            }
        }
        stats.setStreakDays(streak);

        return stats;
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

    /**
     * 获取所有复习计划卡片（分页）
     */
    public List<ReviewScheduleVO> getAllReviewCards(Long userId, Long courseId) {
        LambdaQueryWrapper<QuestionReviewSchedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionReviewSchedule::getUserId, userId)
               .orderByAsc(QuestionReviewSchedule::getNextReviewDate);

        List<QuestionReviewSchedule> schedules = reviewScheduleMapper.selectList(wrapper);
        List<ReviewScheduleVO> vos = fillScheduleVOs(schedules, LocalDate.now());

        // 如果指定了课程，过滤
        if (courseId != null) {
            vos = vos.stream()
                    .filter(v -> courseId.equals(v.getCourseId()))
                    .collect(Collectors.toList());
        }
        return vos;
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

    // ========== 辅助方法 ==========

    /**
     * 将 schedule 实体列表转为 VO 列表（含题目信息和逾期信息）
     */
    private List<ReviewScheduleVO> fillScheduleVOs(List<QuestionReviewSchedule> schedules, LocalDate today) {
        if (schedules.isEmpty()) {
            return new ArrayList<>();
        }

        // 批量获取题目信息
        List<Long> questionIds = schedules.stream()
                .map(QuestionReviewSchedule::getQuestionId)
                .collect(Collectors.toList());

        Map<Long, Question> questionMap = new HashMap<>();
        if (!questionIds.isEmpty()) {
            List<Question> questions = questionMapper.selectBatchIds(questionIds);
            questionMap = questions.stream().collect(Collectors.toMap(Question::getId, q -> q));
        }

        // 批量获取课程名
        Set<Long> courseIds = questionMap.values().stream()
                .map(Question::getCourseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> courseNameMap = new HashMap<>();
        if (!courseIds.isEmpty()) {
            List<Course> courses = courseMapper.selectBatchIds(courseIds);
            courseNameMap = courses.stream().collect(Collectors.toMap(Course::getId, Course::getName));
        }

        List<ReviewScheduleVO> result = new ArrayList<>();
        for (QuestionReviewSchedule schedule : schedules) {
            ReviewScheduleVO vo = new ReviewScheduleVO();
            vo.setId(schedule.getId());
            vo.setQuestionId(schedule.getQuestionId());
            vo.setEaseFactor(schedule.getEaseFactor());
            vo.setIntervalDays(schedule.getIntervalDays());
            vo.setRepetitions(schedule.getRepetitions());
            vo.setNextReviewDate(schedule.getNextReviewDate());
            vo.setLastReviewDate(schedule.getLastReviewDate());
            vo.setLastQuality(schedule.getLastQuality());
            vo.setTotalReviews(schedule.getTotalReviews());

            // 逾期信息
            if (schedule.getNextReviewDate() != null) {
                boolean isOverdue = schedule.getNextReviewDate().isBefore(today);
                vo.setOverdue(isOverdue);
                if (isOverdue) {
                    vo.setOverdueDays((int) java.time.temporal.ChronoUnit.DAYS.between(schedule.getNextReviewDate(), today));
                }
            }

            // 状态标签
            vo.setStatusLabel(buildStatusLabel(schedule));

            // 填充题目信息
            Question q = questionMap.get(schedule.getQuestionId());
            if (q != null) {
                vo.setQuestionContent(truncate(q.getContent(), 100));
                vo.setQuestionType(q.getQuestionType());
                vo.setDifficulty(q.getDifficulty());
                vo.setCourseId(q.getCourseId());
                vo.setCourseName(courseNameMap.get(q.getCourseId()));
            }

            result.add(vo);
        }
        return result;
    }

    private String buildStatusLabel(QuestionReviewSchedule schedule) {
        if (schedule.getTotalReviews() == null || schedule.getTotalReviews() == 0) {
            return "新卡片";
        }
        if (schedule.getEaseFactor() != null && schedule.getEaseFactor().compareTo(DIFFICULT_THRESHOLD) < 0) {
            return "困难";
        }
        if (schedule.getIntervalDays() != null && schedule.getIntervalDays() >= MASTERED_THRESHOLD_DAYS) {
            return "已掌握";
        }
        return "学习中";
    }

    // ========== AI 复习建议上下文 ==========

    /**
     * 收集复习上下文数据，用于构建 AI 复习建议 Prompt
     */
    public ReviewContextVO buildReviewContext(Long userId) {
        ReviewContextVO ctx = new ReviewContextVO();

        // 1. 复习统计概览
        ctx.setStats(getReviewStats(userId));

        LocalDate today = LocalDate.now();

        // 2. 困难卡片（EF < 2.0，最多 10 条）
        LambdaQueryWrapper<QuestionReviewSchedule> diffWrapper = new LambdaQueryWrapper<>();
        diffWrapper.eq(QuestionReviewSchedule::getUserId, userId)
                   .lt(QuestionReviewSchedule::getEaseFactor, DIFFICULT_THRESHOLD)
                   .orderByAsc(QuestionReviewSchedule::getEaseFactor)
                   .last("LIMIT 10");
        List<QuestionReviewSchedule> diffCards = reviewScheduleMapper.selectList(diffWrapper);
        ctx.setDifficultCards(fillScheduleVOs(diffCards, today));

        // 3. 逾期卡片（最多 10 条）
        LambdaQueryWrapper<QuestionReviewSchedule> overdueWrapper = new LambdaQueryWrapper<>();
        overdueWrapper.eq(QuestionReviewSchedule::getUserId, userId)
                      .lt(QuestionReviewSchedule::getNextReviewDate, today)
                      .orderByAsc(QuestionReviewSchedule::getNextReviewDate)
                      .last("LIMIT 10");
        List<QuestionReviewSchedule> overdueCards = reviewScheduleMapper.selectList(overdueWrapper);
        ctx.setOverdueCards(fillScheduleVOs(overdueCards, today));

        // 4. 近 7 天每天复习量
        java.util.List<Integer> dailyReviews = new java.util.ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            LambdaQueryWrapper<QuestionReviewSchedule> dayWrapper = new LambdaQueryWrapper<>();
            dayWrapper.eq(QuestionReviewSchedule::getUserId, userId)
                      .eq(QuestionReviewSchedule::getLastReviewDate, d);
            Long count = reviewScheduleMapper.selectCount(dayWrapper);
            dailyReviews.add(count != null ? count.intValue() : 0);
        }
        ctx.setRecentDailyReviews(dailyReviews);

        return ctx;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        // Remove markdown/HTML for preview
        String plain = text.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
        return plain.length() > maxLen ? plain.substring(0, maxLen) + "..." : plain;
    }
}
