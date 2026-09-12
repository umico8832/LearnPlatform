package com.learnplatform.service.review;

import com.learnplatform.entity.QuestionReviewSchedule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewSchedulePolicyTest {

    @Test
    void classifiesNewDifficultAndMasteredCardsInPriorityOrder() {
        QuestionReviewSchedule schedule = schedule(0, new BigDecimal("1.50"), 30);
        assertEquals("新卡片", ReviewSchedulePolicy.statusLabel(schedule));

        schedule.setTotalReviews(1);
        assertEquals("困难", ReviewSchedulePolicy.statusLabel(schedule));

        schedule.setEaseFactor(new BigDecimal("2.00"));
        assertEquals("已掌握", ReviewSchedulePolicy.statusLabel(schedule));
    }

    @Test
    void keepsThresholdBoundariesStable() {
        QuestionReviewSchedule schedule = schedule(1, new BigDecimal("2.00"), 20);
        assertFalse(ReviewSchedulePolicy.isDifficult(schedule));
        assertFalse(ReviewSchedulePolicy.isMastered(schedule));
        assertEquals("学习中", ReviewSchedulePolicy.statusLabel(schedule));

        schedule.setEaseFactor(new BigDecimal("1.99"));
        schedule.setIntervalDays(21);
        assertTrue(ReviewSchedulePolicy.isDifficult(schedule));
        assertTrue(ReviewSchedulePolicy.isMastered(schedule));
    }

    private QuestionReviewSchedule schedule(int totalReviews, BigDecimal easeFactor, int intervalDays) {
        QuestionReviewSchedule schedule = new QuestionReviewSchedule();
        schedule.setTotalReviews(totalReviews);
        schedule.setEaseFactor(easeFactor);
        schedule.setIntervalDays(intervalDays);
        return schedule;
    }
}
