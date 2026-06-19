package com.learnplatform.service;

import com.learnplatform.entity.QuestionReviewSchedule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SpacedRepetitionService SM-2 算法单元测试
 */
class SpacedRepetitionServiceTest {

    // 创建一个最小化的 SpacedRepetitionService 来测试 SM-2 算法
    // 由于 applySM2 和 calculateQuality 是 package-private，可以直接测试
    private final SpacedRepetitionService service = new SpacedRepetitionService(
            null, null, null, null, null, null, null, null
    );

    @Test
    void testSM2_firstCorrect_setsIntervalTo1() {
        QuestionReviewSchedule schedule = createSchedule(2.5, 0, 0, 0);
        service.applySM2(schedule, 4); // quality=4 (答对但犹豫)

        assertEquals(1, schedule.getRepetitions());
        assertEquals(1, schedule.getIntervalDays());
        assertEquals(LocalDate.now().plusDays(1), schedule.getNextReviewDate());
        assertEquals(1, schedule.getTotalReviews());
        // EF 应该基本不变（q=4, delta≈0）
        assertTrue(schedule.getEaseFactor().compareTo(new BigDecimal("2.40")) >= 0);
        assertTrue(schedule.getEaseFactor().compareTo(new BigDecimal("2.60")) <= 0);
    }

    @Test
    void testSM2_secondCorrect_setsIntervalTo6() {
        QuestionReviewSchedule schedule = createSchedule(2.5, 1, 1, 1);
        service.applySM2(schedule, 4);

        assertEquals(2, schedule.getRepetitions());
        assertEquals(6, schedule.getIntervalDays());
        assertEquals(LocalDate.now().plusDays(6), schedule.getNextReviewDate());
    }

    @Test
    void testSM2_thirdCorrect_multipliesByEF() {
        QuestionReviewSchedule schedule = createSchedule(2.5, 2, 6, 2);
        service.applySM2(schedule, 4);

        assertEquals(3, schedule.getRepetitions());
        // interval = 6 * EF ≈ 6 * 2.5 = 15
        assertTrue(schedule.getIntervalDays() >= 14 && schedule.getIntervalDays() <= 16,
                "Third interval should be ~15 days, got: " + schedule.getIntervalDays());
    }

    @Test
    void testSM2_perfectRecall_increasesEF() {
        QuestionReviewSchedule schedule = createSchedule(2.5, 3, 15, 3);
        service.applySM2(schedule, 5); // quality=5 (完美记住)

        // EF should increase slightly
        assertTrue(schedule.getEaseFactor().compareTo(new BigDecimal("2.50")) > 0,
                "EF should increase with quality=5, got: " + schedule.getEaseFactor());
    }

    @Test
    void testSM2_wrongAnswer_resetsRepetitions() {
        QuestionReviewSchedule schedule = createSchedule(2.5, 5, 30, 5);
        service.applySM2(schedule, 1); // quality=1 (答错)

        assertEquals(0, schedule.getRepetitions());
        assertEquals(1, schedule.getIntervalDays()); // 重置为 1 天
        assertEquals(LocalDate.now().plusDays(1), schedule.getNextReviewDate());
        // EF should decrease
        assertTrue(schedule.getEaseFactor().compareTo(new BigDecimal("2.50")) < 0,
                "EF should decrease with quality=1, got: " + schedule.getEaseFactor());
    }

    @Test
    void testSM2_EFneverBelowMinimum() {
        QuestionReviewSchedule schedule = createSchedule(1.30, 0, 0, 0);
        service.applySM2(schedule, 0); // quality=0 (完全不记得)

        assertTrue(schedule.getEaseFactor().compareTo(new BigDecimal("1.30")) >= 0,
                "EF should not go below 1.30, got: " + schedule.getEaseFactor());
    }

    @Test
    void testSM2_repeatedFailures_keepEFAtMinimum() {
        QuestionReviewSchedule schedule = createSchedule(1.30, 0, 1, 0);
        // 连续答错多次
        for (int i = 0; i < 10; i++) {
            service.applySM2(schedule, 0);
        }
        assertEquals(new BigDecimal("1.30"), schedule.getEaseFactor());
        assertEquals(1, schedule.getIntervalDays());
    }

    @Test
    void testSM2_stableReview_increasesInterval() {
        // 模拟稳定复习序列：一直 quality=4
        QuestionReviewSchedule schedule = createSchedule(2.5, 0, 0, 0);

        // 第一次
        service.applySM2(schedule, 4);
        assertEquals(1, schedule.getIntervalDays());

        // 第二次
        service.applySM2(schedule, 4);
        assertEquals(6, schedule.getIntervalDays());

        // 第三次
        int prevInterval = schedule.getIntervalDays();
        service.applySM2(schedule, 4);
        assertTrue(schedule.getIntervalDays() > prevInterval,
                "Interval should increase with stable reviews");
    }

    @Test
    void testCalculateQuality_selfAssessedTakesPriority() {
        assertEquals(5, service.calculateQuality(true, 5));
        assertEquals(0, service.calculateQuality(true, 0));
        assertEquals(3, service.calculateQuality(false, 3));
    }

    @Test
    void testCalculateQuality_defaultMapping() {
        assertEquals(4, service.calculateQuality(true, null));   // 答对→4
        assertEquals(1, service.calculateQuality(false, null));  // 答错→1
    }

    @Test
    void testCalculateQuality_outOfRange_usesDefault() {
        // 超出范围的自评不生效
        assertEquals(4, service.calculateQuality(true, -1));
        assertEquals(4, service.calculateQuality(true, 6));
        assertEquals(1, service.calculateQuality(false, -1));
    }

    @Test
    void testSM2_scheduleWithNullFields_handledGracefully() {
        QuestionReviewSchedule schedule = new QuestionReviewSchedule();
        schedule.setEaseFactor(null);
        schedule.setIntervalDays(null);
        schedule.setRepetitions(null);
        schedule.setTotalReviews(null);

        // 不应抛异常
        assertDoesNotThrow(() -> service.applySM2(schedule, 4));
        assertEquals(1, schedule.getRepetitions());
        assertEquals(1, schedule.getIntervalDays());
        assertEquals(1, schedule.getTotalReviews());
        assertNotNull(schedule.getEaseFactor());
    }

    // ========== 辅助方法 ==========

    private QuestionReviewSchedule createSchedule(double ef, int reps, int interval, int totalReviews) {
        QuestionReviewSchedule schedule = new QuestionReviewSchedule();
        schedule.setEaseFactor(BigDecimal.valueOf(ef));
        schedule.setIntervalDays(interval);
        schedule.setRepetitions(reps);
        schedule.setTotalReviews(totalReviews);
        schedule.setLastReviewDate(LocalDate.now().minusDays(1));
        schedule.setNextReviewDate(LocalDate.now());
        return schedule;
    }
}