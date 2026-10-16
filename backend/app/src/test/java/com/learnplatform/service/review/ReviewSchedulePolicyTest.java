package com.learnplatform.service.review;

import com.learnplatform.entity.QuestionReviewSchedule;
import io.github.openspacedrepetition.Card;
import io.github.openspacedrepetition.State;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReviewSchedulePolicyTest {

    @Test
    void legacyIntervalsAndEaseDoNotImplyFsrsStateOrMastery() {
        QuestionReviewSchedule schedule = schedule(5, new BigDecimal("1.50"), 30);
        assertEquals("待首次 FSRS 评分", ReviewSchedulePolicy.statusLabel(schedule));
        schedule.setTotalReviews(0);
        assertEquals("待首次 FSRS 评分", ReviewSchedulePolicy.statusLabel(schedule));
    }

    @Test
    void fsrsStateAndRatingAreIndependentOfLegacyProjections() {
        QuestionReviewSchedule schedule = schedule(0, new BigDecimal("1.50"), 100);
        Card card = Card.builder().build();
        for (State state : State.values()) {
            card.setState(state);
            schedule.setFsrsCardJson(card.toJson());
            assertEquals(state.name(), ReviewSchedulePolicy.schedulingState(schedule).name());
        }
        schedule.setFsrsLastRating("AGAIN");
        card.setState(State.REVIEW);
        schedule.setFsrsCardJson(card.toJson());
        assertEquals("间隔复习", ReviewSchedulePolicy.statusLabel(schedule));
        assertEquals("AGAIN", ReviewSchedulePolicy.lastRating(schedule));
        schedule.setFsrsCardJson(null);
        assertEquals(null, ReviewSchedulePolicy.lastRating(schedule));
    }

    private QuestionReviewSchedule schedule(int totalReviews, BigDecimal easeFactor, int intervalDays) {
        QuestionReviewSchedule schedule = new QuestionReviewSchedule();
        schedule.setTotalReviews(totalReviews);
        schedule.setEaseFactor(easeFactor);
        schedule.setIntervalDays(intervalDays);
        return schedule;
    }
}
