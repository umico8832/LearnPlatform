package com.learnplatform.service.review;

import com.learnplatform.entity.QuestionReviewSchedule;

import java.math.BigDecimal;

public final class ReviewSchedulePolicy {

    public static final int MASTERED_THRESHOLD_DAYS = 21;
    public static final BigDecimal DIFFICULT_THRESHOLD = new BigDecimal("2.00");

    private ReviewSchedulePolicy() {
    }

    public static boolean isNew(QuestionReviewSchedule schedule) {
        return schedule.getTotalReviews() == null || schedule.getTotalReviews() == 0;
    }

    public static boolean isMastered(QuestionReviewSchedule schedule) {
        return schedule.getIntervalDays() != null
                && schedule.getIntervalDays() >= MASTERED_THRESHOLD_DAYS;
    }

    public static boolean isDifficult(QuestionReviewSchedule schedule) {
        return schedule.getEaseFactor() != null
                && schedule.getEaseFactor().compareTo(DIFFICULT_THRESHOLD) < 0;
    }

    public static String statusLabel(QuestionReviewSchedule schedule) {
        if (isNew(schedule)) {
            return "新卡片";
        }
        if (isDifficult(schedule)) {
            return "困难";
        }
        if (isMastered(schedule)) {
            return "已掌握";
        }
        return "学习中";
    }
}
