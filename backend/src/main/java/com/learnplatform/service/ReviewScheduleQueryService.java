package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.dto.ReviewContextVO;
import com.learnplatform.dto.ReviewScheduleVO;
import com.learnplatform.dto.ReviewStatsVO;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionReviewSchedule;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionReviewScheduleMapper;
import com.learnplatform.service.review.ReviewSchedulePolicy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 复习计划只读查询、统计和 AI 上下文组装。
 */
@Service
public class ReviewScheduleQueryService {

    private final QuestionReviewScheduleMapper reviewScheduleMapper;
    private final QuestionMapper questionMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final ReviewScheduleCardViewService cardViewService;

    public ReviewScheduleQueryService(QuestionReviewScheduleMapper reviewScheduleMapper,
                                      QuestionMapper questionMapper,
                                      KnowledgePointMapper knowledgePointMapper,
                                      ReviewScheduleCardViewService cardViewService) {
        this.reviewScheduleMapper = reviewScheduleMapper;
        this.questionMapper = questionMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.cardViewService = cardViewService;
    }

    public List<ReviewScheduleVO> getDueReviewCards(Long userId, Long courseId, int limit) {
        return getDueReviewCards(userId, courseId, null, null, limit);
    }

    public List<ReviewScheduleVO> getDueReviewCards(Long userId, Long courseId, Long questionId, int limit) {
        return getDueReviewCards(userId, courseId, questionId, null, limit);
    }

    /**
     * 获取课程内今日待复习题目，可限定课程、目标题目和知识点（数据库分页前筛选）。
     */
    public List<ReviewScheduleVO> getDueReviewCards(Long userId, Long courseId, Long questionId,
                                                    Long knowledgePointId, int limit) {
        if (limit <= 0 || limit > 50) {
            limit = 20;
        }

        Set<Long> courseQuestionIds = findCourseQuestionIds(courseId);
        if (courseId != null && courseQuestionIds.isEmpty()) {
            return List.of();
        }
        Set<Long> knowledgePointQuestionIds = findKnowledgePointQuestionIds(knowledgePointId);
        if (knowledgePointId != null && knowledgePointQuestionIds.isEmpty()) {
            return List.of();
        }

        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<QuestionReviewSchedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionReviewSchedule::getUserId, userId)
                .le(QuestionReviewSchedule::getNextReviewDate, today);
        if (courseId != null) {
            wrapper.in(QuestionReviewSchedule::getQuestionId, courseQuestionIds);
        }
        if (knowledgePointId != null) {
            wrapper.in(QuestionReviewSchedule::getQuestionId, knowledgePointQuestionIds);
        }
        if (questionId != null) {
            wrapper.eq(QuestionReviewSchedule::getQuestionId, questionId);
        }
        wrapper.orderByAsc(QuestionReviewSchedule::getNextReviewDate)
                .orderByDesc(QuestionReviewSchedule::getEaseFactor)
                .last("LIMIT " + limit);

        List<QuestionReviewSchedule> schedules = reviewScheduleMapper.selectList(wrapper);
        if (courseId != null) {
            schedules = retainQuestionIds(schedules, courseQuestionIds);
        }
        if (knowledgePointId != null) {
            schedules = retainQuestionIds(schedules, knowledgePointQuestionIds);
        }
        if (questionId != null) {
            schedules = schedules.stream()
                    .filter(schedule -> questionId.equals(schedule.getQuestionId()))
                    .toList();
        }
        return cardViewService.toViews(schedules, today);
    }

    /**
     * 获取复习统计概览。
     */
    public ReviewStatsVO getReviewStats(Long userId) {
        LocalDate today = LocalDate.now();
        ReviewStatsVO stats = new ReviewStatsVO();

        LambdaQueryWrapper<QuestionReviewSchedule> allWrapper = new LambdaQueryWrapper<>();
        allWrapper.eq(QuestionReviewSchedule::getUserId, userId);
        long totalCards = reviewScheduleMapper.selectCount(allWrapper);
        stats.setTotalCards((int) totalCards);
        if (totalCards == 0) {
            return stats;
        }

        stats.setDueToday(count(userId, QuestionReviewSchedule::getNextReviewDate, today, false));
        stats.setOverdue(count(userId, QuestionReviewSchedule::getNextReviewDate, today, true));

        LambdaQueryWrapper<QuestionReviewSchedule> doneWrapper = new LambdaQueryWrapper<>();
        doneWrapper.eq(QuestionReviewSchedule::getUserId, userId)
                .eq(QuestionReviewSchedule::getLastReviewDate, today);
        stats.setReviewedToday(toInt(reviewScheduleMapper.selectCount(doneWrapper)));

        fillCardCategories(stats, reviewScheduleMapper.selectList(allWrapper), totalCards);
        stats.setStreakDays(calculateStreakDays(userId, today));
        return stats;
    }

    /**
     * 获取所有复习计划卡片。
     */
    public List<ReviewScheduleVO> getAllReviewCards(Long userId, Long courseId) {
        LambdaQueryWrapper<QuestionReviewSchedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionReviewSchedule::getUserId, userId)
                .orderByAsc(QuestionReviewSchedule::getNextReviewDate);

        List<ReviewScheduleVO> views = cardViewService.toViews(reviewScheduleMapper.selectList(wrapper), LocalDate.now());
        if (courseId == null) {
            return views;
        }
        return views.stream()
                .filter(view -> courseId.equals(view.getCourseId()))
                .toList();
    }

    /**
     * 收集复习上下文数据，用于构建 AI 复习建议 Prompt。
     */
    public ReviewContextVO buildReviewContext(Long userId) {
        ReviewContextVO context = new ReviewContextVO();
        context.setStats(getReviewStats(userId));

        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<QuestionReviewSchedule> difficultWrapper = new LambdaQueryWrapper<>();
        difficultWrapper.eq(QuestionReviewSchedule::getUserId, userId)
                .lt(QuestionReviewSchedule::getEaseFactor, ReviewSchedulePolicy.DIFFICULT_THRESHOLD)
                .orderByAsc(QuestionReviewSchedule::getEaseFactor)
                .last("LIMIT 10");
        context.setDifficultCards(cardViewService.toViews(reviewScheduleMapper.selectList(difficultWrapper), today));

        LambdaQueryWrapper<QuestionReviewSchedule> overdueWrapper = new LambdaQueryWrapper<>();
        overdueWrapper.eq(QuestionReviewSchedule::getUserId, userId)
                .lt(QuestionReviewSchedule::getNextReviewDate, today)
                .orderByAsc(QuestionReviewSchedule::getNextReviewDate)
                .last("LIMIT 10");
        context.setOverdueCards(cardViewService.toViews(reviewScheduleMapper.selectList(overdueWrapper), today));

        List<Integer> dailyReviews = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LambdaQueryWrapper<QuestionReviewSchedule> dayWrapper = new LambdaQueryWrapper<>();
            dayWrapper.eq(QuestionReviewSchedule::getUserId, userId)
                    .eq(QuestionReviewSchedule::getLastReviewDate, today.minusDays(i));
            dailyReviews.add(toInt(reviewScheduleMapper.selectCount(dayWrapper)));
        }
        context.setRecentDailyReviews(dailyReviews);
        return context;
    }

    ReviewScheduleVO buildScheduleView(QuestionReviewSchedule schedule) {
        return cardViewService.toView(schedule);
    }

    private Set<Long> findCourseQuestionIds(Long courseId) {
        if (courseId == null) {
            return Set.of();
        }
        return questionMapper.selectList(new LambdaQueryWrapper<Question>()
                        .eq(Question::getCourseId, courseId)).stream()
                .map(Question::getId)
                .collect(Collectors.toSet());
    }

    private Set<Long> findKnowledgePointQuestionIds(Long knowledgePointId) {
        if (knowledgePointId == null) {
            return Set.of();
        }
        return new HashSet<>(knowledgePointMapper.selectQuestionIdsByKnowledgePointId(knowledgePointId));
    }

    private List<QuestionReviewSchedule> retainQuestionIds(List<QuestionReviewSchedule> schedules,
                                                           Set<Long> allowedQuestionIds) {
        return schedules.stream()
                .filter(schedule -> allowedQuestionIds.contains(schedule.getQuestionId()))
                .toList();
    }

    private int count(Long userId,
                      com.baomidou.mybatisplus.core.toolkit.support.SFunction<QuestionReviewSchedule, ?> column,
                      LocalDate today,
                      boolean beforeOnly) {
        LambdaQueryWrapper<QuestionReviewSchedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionReviewSchedule::getUserId, userId);
        if (beforeOnly) {
            wrapper.lt(column, today);
        } else {
            wrapper.le(column, today);
        }
        return toInt(reviewScheduleMapper.selectCount(wrapper));
    }

    private void fillCardCategories(ReviewStatsVO stats,
                                    List<QuestionReviewSchedule> cards,
                                    long totalCards) {
        int newCards = 0;
        int learning = 0;
        int mastered = 0;
        int difficult = 0;
        BigDecimal totalEaseFactor = BigDecimal.ZERO;

        for (QuestionReviewSchedule card : cards) {
            if (ReviewSchedulePolicy.isNew(card)) {
                newCards++;
            } else if (ReviewSchedulePolicy.isMastered(card)) {
                mastered++;
            } else {
                learning++;
            }
            if (ReviewSchedulePolicy.isDifficult(card)) {
                difficult++;
            }
            if (card.getEaseFactor() != null) {
                totalEaseFactor = totalEaseFactor.add(card.getEaseFactor());
            }
        }
        stats.setNewCards(newCards);
        stats.setLearningCards(learning);
        stats.setMasteredCards(mastered);
        stats.setDifficultCards(difficult);
        stats.setAvgEaseFactor(totalEaseFactor.divide(
                BigDecimal.valueOf(totalCards), 2, RoundingMode.HALF_UP).doubleValue());
    }

    private int calculateStreakDays(Long userId, LocalDate today) {
        int streak = 0;
        LocalDate checkDate = today;
        while (true) {
            LambdaQueryWrapper<QuestionReviewSchedule> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(QuestionReviewSchedule::getUserId, userId)
                    .eq(QuestionReviewSchedule::getLastReviewDate, checkDate);
            if (reviewScheduleMapper.selectCount(wrapper) <= 0) {
                return streak;
            }
            streak++;
            checkDate = checkDate.minusDays(1);
        }
    }

    private int toInt(Long count) {
        return count == null ? 0 : count.intValue();
    }
}
