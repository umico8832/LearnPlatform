package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.dto.ReviewContextVO;
import com.learnplatform.dto.ReviewScheduleVO;
import com.learnplatform.dto.ReviewStatsVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionReviewSchedule;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionReviewScheduleMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 复习计划只读查询、统计和视图组装。
 */
@Service
public class ReviewScheduleQueryService {

    private static final int MASTERED_THRESHOLD_DAYS = 21;
    private static final BigDecimal DIFFICULT_THRESHOLD = new BigDecimal("2.00");

    private final QuestionReviewScheduleMapper reviewScheduleMapper;
    private final QuestionMapper questionMapper;
    private final CourseMapper courseMapper;
    private final KnowledgePointMapper knowledgePointMapper;

    public ReviewScheduleQueryService(QuestionReviewScheduleMapper reviewScheduleMapper,
                                      QuestionMapper questionMapper,
                                      CourseMapper courseMapper,
                                      KnowledgePointMapper knowledgePointMapper) {
        this.reviewScheduleMapper = reviewScheduleMapper;
        this.questionMapper = questionMapper;
        this.courseMapper = courseMapper;
        this.knowledgePointMapper = knowledgePointMapper;
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
        return fillScheduleVOs(schedules, today);
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

        List<ReviewScheduleVO> views = fillScheduleVOs(reviewScheduleMapper.selectList(wrapper), LocalDate.now());
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
                .lt(QuestionReviewSchedule::getEaseFactor, DIFFICULT_THRESHOLD)
                .orderByAsc(QuestionReviewSchedule::getEaseFactor)
                .last("LIMIT 10");
        context.setDifficultCards(fillScheduleVOs(reviewScheduleMapper.selectList(difficultWrapper), today));

        LambdaQueryWrapper<QuestionReviewSchedule> overdueWrapper = new LambdaQueryWrapper<>();
        overdueWrapper.eq(QuestionReviewSchedule::getUserId, userId)
                .lt(QuestionReviewSchedule::getNextReviewDate, today)
                .orderByAsc(QuestionReviewSchedule::getNextReviewDate)
                .last("LIMIT 10");
        context.setOverdueCards(fillScheduleVOs(reviewScheduleMapper.selectList(overdueWrapper), today));

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
        List<ReviewScheduleVO> views = fillScheduleVOs(List.of(schedule), LocalDate.now());
        return views.isEmpty() ? null : views.get(0);
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

    private List<ReviewScheduleVO> fillScheduleVOs(List<QuestionReviewSchedule> schedules, LocalDate today) {
        if (schedules.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> questionIds = schedules.stream()
                .map(QuestionReviewSchedule::getQuestionId)
                .toList();
        Map<Long, Question> questionMap = questionMapper.selectBatchIds(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, question -> question));

        Set<Long> courseIds = questionMap.values().stream()
                .map(Question::getCourseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> courseNameMap = new HashMap<>();
        if (!courseIds.isEmpty()) {
            courseNameMap = courseMapper.selectBatchIds(courseIds).stream()
                    .collect(Collectors.toMap(Course::getId, Course::getName));
        }

        List<ReviewScheduleVO> result = new ArrayList<>();
        for (QuestionReviewSchedule schedule : schedules) {
            ReviewScheduleVO view = new ReviewScheduleVO();
            view.setId(schedule.getId());
            view.setQuestionId(schedule.getQuestionId());
            view.setEaseFactor(schedule.getEaseFactor());
            view.setIntervalDays(schedule.getIntervalDays());
            view.setRepetitions(schedule.getRepetitions());
            view.setNextReviewDate(schedule.getNextReviewDate());
            view.setLastReviewDate(schedule.getLastReviewDate());
            view.setLastQuality(schedule.getLastQuality());
            view.setTotalReviews(schedule.getTotalReviews());
            fillDueState(view, schedule, today);
            view.setStatusLabel(buildStatusLabel(schedule));
            fillQuestion(view, questionMap.get(schedule.getQuestionId()), courseNameMap);
            result.add(view);
        }
        return result;
    }

    private void fillDueState(ReviewScheduleVO view, QuestionReviewSchedule schedule, LocalDate today) {
        if (schedule.getNextReviewDate() == null) {
            return;
        }
        boolean overdue = schedule.getNextReviewDate().isBefore(today);
        view.setOverdue(overdue);
        if (overdue) {
            view.setOverdueDays((int) ChronoUnit.DAYS.between(schedule.getNextReviewDate(), today));
        }
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

    private void fillQuestion(ReviewScheduleVO view, Question question, Map<Long, String> courseNameMap) {
        if (question == null) {
            return;
        }
        view.setQuestionContent(truncate(question.getContent(), 100));
        view.setQuestionType(question.getQuestionType());
        view.setDifficulty(question.getDifficulty());
        view.setCourseId(question.getCourseId());
        view.setCourseName(courseNameMap.get(question.getCourseId()));
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String plain = text.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
        return plain.length() > maxLength ? plain.substring(0, maxLength) + "..." : plain;
    }

    private int toInt(Long count) {
        return count == null ? 0 : count.intValue();
    }
}
