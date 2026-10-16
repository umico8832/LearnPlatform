package com.learnplatform.service.review;

import com.learnplatform.entity.QuestionReviewSchedule;
import io.github.openspacedrepetition.Card;

public final class ReviewSchedulePolicy {
    public enum SchedulingState { UNRATED, LEARNING, REVIEW, RELEARNING }

    private ReviewSchedulePolicy() {
    }

    public static SchedulingState schedulingState(QuestionReviewSchedule schedule) {
        if (schedule.getFsrsCardJson() == null) {
            return SchedulingState.UNRATED;
        }
        return SchedulingState.valueOf(Card.fromJson(schedule.getFsrsCardJson()).getState().name());
    }

    public static String lastRating(QuestionReviewSchedule schedule) {
        return schedule.getFsrsCardJson() == null ? null : schedule.getFsrsLastRating();
    }

    public static String statusLabel(QuestionReviewSchedule schedule) {
        return switch (schedulingState(schedule)) {
            case UNRATED -> "待首次 FSRS 评分";
            case LEARNING -> "初学排期";
            case REVIEW -> "间隔复习";
            case RELEARNING -> "重学排期";
        };
    }
}
