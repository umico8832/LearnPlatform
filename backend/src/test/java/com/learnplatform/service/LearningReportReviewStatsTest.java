package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.dto.LearningReportVO;
import com.learnplatform.entity.*;
import com.learnplatform.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 学习报告复习统计集成单元测试
 *
 * 验证 StatisticsService.getLearningReport 正确整合间隔重复复习数据。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LearningReportReviewStatsTest {

    @Mock
    private PracticeRecordMapper practiceRecordMapper;
    @Mock
    private WrongQuestionMapper wrongQuestionMapper;
    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private CourseMapper courseMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private ExamPaperMapper examPaperMapper;
    @Mock
    private ExamRecordMapper examRecordMapper;
    @Mock
    private QuestionReviewScheduleMapper reviewScheduleMapper;

    private StatisticsService statisticsService;

    @BeforeEach
    void setUp() {
        statisticsService = new StatisticsService(
                practiceRecordMapper, wrongQuestionMapper, questionMapper,
                courseMapper, userMapper, examPaperMapper, examRecordMapper,
                reviewScheduleMapper
        );

        // 默认 stub：无刷题/错题/考试数据
        when(practiceRecordMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(wrongQuestionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(wrongQuestionMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(0L);
        when(examRecordMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
    }

    @Test
    void getLearningReport_withReviewData_returnsReviewStats() {
        Long userId = 1L;
        LocalDate today = LocalDate.now();

        // 模拟复习卡片数据：3 张卡片
        QuestionReviewSchedule card1 = createSchedule(userId, 101L, 2.5, 30, 3, today.minusDays(1));
        QuestionReviewSchedule card2 = createSchedule(userId, 102L, 2.3, 7, 2, today);
        QuestionReviewSchedule card3 = createSchedule(userId, 103L, 1.8, 1, 0, today);

        // 总卡片数 = 3
        when(reviewScheduleMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(3L);

        // 本月复习过的 + 全部卡片
        when(reviewScheduleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(card1, card2, card3));

        LearningReportVO report = statisticsService.getLearningReport(userId);

        // 验证复习统计字段
        assertNotNull(report);
        assertEquals(3, report.getTotalReviewCards());
        assertEquals(3, report.getMonthlyReviewedCount());
        // masteredReviewCards: card1 has intervalDays=30 >= 21 → 1 mastered
        assertEquals(1, report.getMasteredReviewCards());
        assertEquals(3, report.getDueTodayCount());
        assertNotNull(report.getMonthlyReviewTrend());
        // monthlyReviewTrend 应该有当月天数个条目
        assertEquals(today.getDayOfMonth(), report.getMonthlyReviewTrend().size());
    }

    @Test
    void getLearningReport_noReviewCards_returnsZeroReviewStats() {
        Long userId = 1L;

        // 模拟无复习卡片
        when(reviewScheduleMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(0L);
        when(reviewScheduleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        LearningReportVO report = statisticsService.getLearningReport(userId);

        assertNotNull(report);
        assertEquals(0, report.getTotalReviewCards());
        assertEquals(0, report.getMonthlyReviewedCount());
        assertEquals(0, report.getReviewStreakDays());
        assertEquals(0, report.getMasteredReviewCards());
        assertEquals(0, report.getDueTodayCount());
        assertNotNull(report.getMonthlyReviewTrend());
        assertTrue(report.getMonthlyReviewTrend().isEmpty());
    }

    @Test
    void getLearningReport_reviewStreakDays_calculatedCorrectly() {
        Long userId = 1L;

        // selectCount 被调用多次：total, due, streak day1, streak day2, streak day3(0 → stop)
        when(reviewScheduleMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(5L)   // total
                .thenReturn(2L)   // due
                .thenReturn(1L)   // streak: today has review
                .thenReturn(1L)   // streak: today-1 has review
                .thenReturn(0L);  // streak: today-2 no review → stop
        when(reviewScheduleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        LearningReportVO report = statisticsService.getLearningReport(userId);

        assertNotNull(report);
        assertEquals(5, report.getTotalReviewCards());
        assertEquals(2, report.getReviewStreakDays());
    }

    @Test
    void getLearningReport_multipleMasteredCards_countedCorrectly() {
        Long userId = 1L;
        LocalDate today = LocalDate.now();

        // 3 张卡片，2 张已掌握（intervalDays >= 21）
        QuestionReviewSchedule card1 = createSchedule(userId, 101L, 2.5, 30, 5, today.minusDays(2));
        QuestionReviewSchedule card2 = createSchedule(userId, 102L, 2.8, 25, 4, today.minusDays(1));
        QuestionReviewSchedule card3 = createSchedule(userId, 103L, 2.0, 7, 2, today);

        when(reviewScheduleMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(3L);
        when(reviewScheduleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(card1, card2, card3));

        LearningReportVO report = statisticsService.getLearningReport(userId);

        assertNotNull(report);
        assertEquals(3, report.getTotalReviewCards());
        assertEquals(2, report.getMasteredReviewCards());
    }

    // ========== 辅助方法 ==========

    private QuestionReviewSchedule createSchedule(Long userId, Long questionId,
                                                   double ef, int intervalDays,
                                                   int totalReviews, LocalDate lastReviewDate) {
        QuestionReviewSchedule schedule = new QuestionReviewSchedule();
        schedule.setUserId(userId);
        schedule.setQuestionId(questionId);
        schedule.setEaseFactor(BigDecimal.valueOf(ef));
        schedule.setIntervalDays(intervalDays);
        schedule.setRepetitions(2);
        schedule.setTotalReviews(totalReviews);
        schedule.setLastReviewDate(lastReviewDate);
        schedule.setNextReviewDate(LocalDate.now());
        return schedule;
    }
}