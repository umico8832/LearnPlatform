package com.learnplatform.service.review;

import com.learnplatform.common.time.BusinessTime;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.entity.QuestionReviewSchedule;
import com.learnplatform.mapper.QuestionReviewScheduleMapper;
import io.github.openspacedrepetition.Card;
import io.github.openspacedrepetition.Rating;
import io.github.openspacedrepetition.Scheduler;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
public class FsrsReviewService {
    private static final BigDecimal LEGACY_DEFAULT_EASE = new BigDecimal("2.50");
    private static final Scheduler SCHEDULER = Scheduler.builder()
            .learningSteps(new Duration[] {})
            .relearningSteps(new Duration[] {})
            .enableFuzzing(false)
            .build();

    private final Clock clock;
    private final QuestionReviewScheduleMapper schedules;
    private final JdbcTemplate db;

    @Autowired
    public FsrsReviewService(QuestionReviewScheduleMapper schedules, JdbcTemplate db) {
        this(schedules, db, BusinessTime.clock());
    }

    FsrsReviewService(QuestionReviewScheduleMapper schedules, JdbcTemplate db, Clock clock) {
        this.clock = clock;
        this.schedules = schedules;
        this.db = db;
    }

    public enum Source { PRACTICE, UNDERSTANDING_VARIANT, REVIEW }

    @Transactional
    public QuestionReviewSchedule score(long userId, long questionId, Source source,
                                        long sourceId, boolean correct, Integer selfAssessedQuality) {
        if (sourceId <= 0) {
            throw new IllegalArgumentException("FSRS source ID must be positive");
        }
        Rating rating = rating(correct, selfAssessedQuality);
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, BusinessTime.ZONE);
        schedules.insertOrRestore(userId, questionId, LEGACY_DEFAULT_EASE, today);
        QuestionReviewSchedule schedule = schedules.selectByUserAndQuestionForUpdate(userId, questionId);
        if (schedule == null) {
            throw new IllegalStateException("Review schedule disappeared before FSRS score");
        }
        Map<String, Object> prior = event(source, sourceId);
        if (prior != null) {
            if (!Long.valueOf(userId).equals(((Number) prior.get("user_id")).longValue())
                    || !Long.valueOf(questionId).equals(((Number) prior.get("question_id")).longValue())) {
                throw new IllegalStateException("FSRS source belongs to another answer");
            }
            return schedule;
        }
        String savedCard = cardJson(schedule.getId());
        Card card = savedCard == null ? Card.builder().build() : Card.fromJson(savedCard);
        Card updated = SCHEDULER.reviewCard(card, rating, now).card();
        LocalDate dueDate = updated.getDue().atZone(BusinessTime.ZONE).toLocalDate();
        int intervalDays = Math.toIntExact(Math.max(0, ChronoUnit.DAYS.between(today, dueDate)));
        int repetitions = correct ? defaultInt(schedule.getRepetitions()) + 1 : 0;
        int quality = quality(rating);
        int changed = db.update("""
                UPDATE question_review_schedule
                SET fsrs_card_json=?,fsrs_last_rating=?,fsrs_version=1,ease_factor=NULL,
                    next_review_date=?,last_review_date=?,interval_days=?,repetitions=?,last_quality=?,
                    total_reviews=total_reviews+1
                WHERE id=? AND user_id=? AND question_id=? AND deleted=0
                """, updated.toJson(), rating.name(), dueDate, today, intervalDays,
                repetitions, quality, schedule.getId(), userId, questionId);
        if (changed != 1) {
            throw new IllegalStateException("FSRS schedule update lost its target");
        }
        db.update("""
                INSERT INTO fsrs_review_event
                (schedule_id,user_id,question_id,source_type,source_id,rating,reviewed_at)
                VALUES(?,?,?,?,?,?,?)
                """, schedule.getId(), userId, questionId, source.name(), sourceId, rating.name(),
                LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        schedule.setFsrsCardJson(updated.toJson());
        schedule.setFsrsLastRating(rating.name());
        schedule.setEaseFactor(null);
        schedule.setNextReviewDate(dueDate);
        schedule.setLastReviewDate(today);
        schedule.setIntervalDays(intervalDays);
        schedule.setRepetitions(repetitions);
        schedule.setLastQuality(quality);
        schedule.setTotalReviews(defaultInt(schedule.getTotalReviews()) + 1);
        return schedule;
    }

    public void reset(long scheduleId) {
        db.update("""
                UPDATE question_review_schedule
                SET fsrs_card_json=NULL,fsrs_last_rating=NULL,fsrs_version=NULL
                WHERE id=?
                """, scheduleId);
    }

    private Rating rating(boolean correct, Integer quality) {
        if (quality != null && (quality < 0 || quality > 5)) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "自评质量须为 0–5");
        }
        if (!correct) { return Rating.AGAIN; }
        if (quality == null || quality == 4) { return Rating.GOOD; }
        return quality == 5 ? Rating.EASY : Rating.HARD;
    }

    private int quality(Rating rating) {
        return switch (rating) {
            case AGAIN -> 1;
            case HARD -> 3;
            case GOOD -> 4;
            case EASY -> 5;
        };
    }

    private String cardJson(long scheduleId) {
        List<Map<String, Object>> rows = db.queryForList(
                "SELECT fsrs_card_json FROM question_review_schedule WHERE id=?", scheduleId);
        Object value = rows.getFirst().get("fsrs_card_json");
        return value == null ? null : value.toString();
    }

    private Map<String, Object> event(Source source, long sourceId) {
        List<Map<String, Object>> rows = db.queryForList("""
                SELECT user_id,question_id FROM fsrs_review_event WHERE source_type=? AND source_id=?
                """, source.name(), sourceId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private int defaultInt(Integer value) { return value == null ? 0 : value; }
}
