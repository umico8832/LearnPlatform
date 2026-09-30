package com.learnplatform.service.gamification;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.dto.GamificationRewardFeedback;
import com.learnplatform.entity.CourseLearningEvent;
import com.learnplatform.entity.Question;
import com.learnplatform.service.CourseLearningEventService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
class GamificationIntegrityIntegrationTest extends IntegrationTestBase {
    private static final AtomicLong IDS = new AtomicLong(91000000L);
    @Autowired private GamificationRewardService rewards;
    @Autowired private GamificationBackfillService backfill;
    @Autowired private GamificationHeatmapService heatmap;
    @Autowired private CourseLearningEventService events;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    private long userId;

    @BeforeEach
    void setUp() { userId = IDS.incrementAndGet(); }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM gamification_achievement WHERE user_id=?", userId);
        jdbc.update("DELETE FROM gamification_xp_ledger WHERE user_id=?", userId);
        jdbc.update("DELETE FROM gamification_user_profile WHERE user_id=?", userId);
        jdbc.update("DELETE FROM course_learning_event WHERE user_id=?", userId);
        jdbc.update("DELETE FROM user_course WHERE user_id=?", userId);
    }

    @Test
    void newAnswerReplaysOlderFactsInTimeOrderAndBackfillIsRepeatable() {
        LocalDate today = LocalDate.now(GamificationRewardService.LEARNING_ZONE);
        fact(101, today.minusDays(3).atTime(23, 59), "PRACTICE_ANSWERED", "{ \"isCorrect\" : true }");
        fact(102, today.minusDays(2).atTime(0, 1), "REVIEW_ANSWERED", "{\"isCorrect\":true}");
        fact(103, today.minusDays(1).atTime(12, 0), "PRACTICE_ANSWERED", "{\"isCorrect\":false}");
        CourseLearningEvent current = fact(104, today.atTime(0, 1), "PRACTICE_ANSWERED", "{\"isCorrect\":true}");
        GamificationRewardFeedback feedback = rewards.reward(current, true);
        assertEquals(10, feedback.getAwardedXp());
        assertEquals(36, feedback.getSummary().getTotalXp());
        assertEquals(1, feedback.getSummary().getCurrentCombo());
        assertEquals(2, feedback.getSummary().getMaxCombo());
        assertEquals(4, feedback.getSummary().getStreakDays());
        assertEquals(36, backfill.backfill(userId).getTotalXp());
        assertEquals(36, backfill.backfill(userId).getTotalXp());
        assertEquals(4, count("gamification_xp_ledger"));
        assertTrue(rewards.achievements(userId).stream().anyMatch(a -> a.getCode().equals("STREAK_3") && a.isUnlocked()));
    }

    @Test
    void replayAcceptsOnlyGradedBooleanFactsAndKeepsYesterdayStreak() {
        LocalDate today = LocalDate.now(GamificationRewardService.LEARNING_ZONE);
        fact(101, today.minusDays(2).atTime(23, 59), "PRACTICE_ANSWERED", "{\"isCorrect\":true}");
        fact(102, today.minusDays(1).atTime(0, 1), "PRACTICE_ANSWERED", "{\"isCorrect\":true}");
        fact(103, today.atTime(1, 0), "PAPER_LEARNING_ANSWERED", "{\"isCorrect\":null}");
        fact(104, today.atTime(1, 1), "PRACTICE_ANSWERED", "{\"isCorrect\":\"true\"}");
        fact(105, today.atTime(1, 2), "PAPER_LEARNING_AI_ASSISTED", "{\"isCorrect\":true}");
        var summary = rewards.summary(userId);
        assertEquals(20, summary.getTotalXp());
        assertEquals(2, summary.getStreakDays());
        assertEquals(0, summary.getTodayAnsweredCount());
        assertEquals(2, count("gamification_xp_ledger"));
    }

    @Test
    void archivedStreakStillUnlocksAchievementButCurrentStreakExpires() {
        LocalDate today = LocalDate.now(GamificationRewardService.LEARNING_ZONE);
        for (int index = 10; index > 7; index--) {
            fact(100 + index, today.minusDays(index).atTime(12, 0), "PRACTICE_ANSWERED", "{\"isCorrect\":true}");
        }
        assertEquals(0, rewards.summary(userId).getStreakDays());
        assertTrue(rewards.achievements(userId).stream().anyMatch(a -> a.getCode().equals("STREAK_3") && a.isUnlocked()));
    }

    @Test
    void heatmapFirstReadInitializesHistoryAndMatchesGoalCounting() {
        LocalDate today = LocalDate.now(GamificationRewardService.LEARNING_ZONE);
        fact(101, today.minusDays(1).atTime(12, 0), "PRACTICE_ANSWERED", "{\"isCorrect\":true}");
        fact(101, today.minusDays(1).atTime(13, 0), "REVIEW_ANSWERED", "{\"isCorrect\":true}");
        fact(102, today.atTime(0, 1), "PRACTICE_ANSWERED", "{\"isCorrect\":false}");
        var days = heatmap.days(userId, today.minusDays(1), today);
        assertEquals(2, days.size());
        assertEquals(1, days.getFirst().getAnsweredCount());
        assertEquals(10, days.getFirst().getEarnedXp());
        assertEquals(2, days.getLast().getEarnedXp());
        assertEquals(1, rewards.summary(userId).getTodayAnsweredCount());
        assertEquals(3, count("gamification_xp_ledger"));
    }

    @Test
    void backfillConvertsSourceClockFactsIntoShanghaiDaysForGoalHeatmapAndStreak() {
        LocalDate today = LocalDate.now(GamificationRewardService.LEARNING_ZONE);
        fact(401, today.minusDays(1).atTime(0, 30), "PRACTICE_ANSWERED", "{\"isCorrect\":true}");
        fact(402, today.atTime(0, 30), "PRACTICE_ANSWERED", "{\"isCorrect\":true}");

        var summary = rewards.summary(userId);
        var days = heatmap.days(userId, today, today);

        assertEquals(1, summary.getTodayAnsweredCount());
        assertEquals(2, summary.getStreakDays());
        assertEquals(1, days.size());
        assertEquals(today, days.getFirst().getDate());
        assertEquals(10, days.getFirst().getEarnedXp());
    }

    @Test
    void liveRewardUsesTheSameShanghaiDayAndPreservesTheSourceDatetimeInMysql() {
        LocalDate today = LocalDate.now(GamificationRewardService.LEARNING_ZONE);
        rewards.summary(userId);
        LocalDateTime learningTime = today.atTime(0, 30);
        CourseLearningEvent event = fact(403, learningTime, "PRACTICE_ANSWERED", "{\"isCorrect\":true}");

        GamificationRewardFeedback feedback = rewards.reward(event, true);
        var days = heatmap.days(userId, today, today);

        assertEquals(1, feedback.getSummary().getTodayAnsweredCount());
        assertEquals(1, feedback.getSummary().getStreakDays());
        assertEquals(today, jdbc.queryForObject(
                "SELECT event_date FROM gamification_xp_ledger WHERE event_id=?", LocalDate.class, event.getId()));
        assertEquals(learningTime.atZone(GamificationRewardService.LEARNING_ZONE)
                        .withZoneSameInstant(GamificationRewardService.SOURCE_CLOCK_ZONE).toLocalDateTime(),
                jdbc.queryForObject(
                        "SELECT occurred_time FROM gamification_xp_ledger WHERE event_id=?",
                        LocalDateTime.class, event.getId()));
        assertEquals(today, days.getFirst().getDate());
        assertEquals(event.getOccurredTime(), rewards.achievements(userId).stream()
                .filter(achievement -> achievement.getCode().equals("FIRST_ANSWER"))
                .findFirst().orElseThrow().getUnlockedAt());
    }

    @Test
    void concurrentDifferentFactsPreserveTheTotal() throws Exception {
        rewards.summary(userId);
        var first = fact(201, LocalDateTime.now(GamificationRewardService.LEARNING_ZONE), "PRACTICE_ANSWERED", "{\"isCorrect\":true}");
        var second = fact(202, LocalDateTime.now(GamificationRewardService.LEARNING_ZONE), "PRACTICE_ANSWERED", "{\"isCorrect\":true}");
        race(first, second);
        assertEquals(20, rewards.summary(userId).getTotalXp());
        assertEquals(2, rewards.summary(userId).getCurrentCombo());
        assertEquals(2, count("gamification_xp_ledger"));
    }

    @Test
    void concurrentReplayAndSameObjectCannotIncreaseReward() throws Exception {
        rewards.summary(userId);
        var first = fact(201, LocalDateTime.now(GamificationRewardService.LEARNING_ZONE), "PRACTICE_ANSWERED", "{\"isCorrect\":true}");
        race(first, first);
        var sameQuestion = fact(201, LocalDateTime.now(GamificationRewardService.LEARNING_ZONE), "REVIEW_ANSWERED", "{\"isCorrect\":true}");
        assertEquals(0, rewards.reward(sameQuestion, true).getAwardedXp());
        assertEquals(10, rewards.summary(userId).getTotalXp());
        assertEquals(1, rewards.summary(userId).getTodayAnsweredCount());
        assertEquals(1, rewards.summary(userId).getCurrentCombo());
        assertEquals(2, count("gamification_xp_ledger"));
    }

    @Test
    void tutorCheckAndVariantTrainingWithSameSourceIdRemainDistinctFacts() {
        jdbc.update("INSERT INTO user_course(user_id,course_id) VALUES(?,1)", userId);
        Question question = new Question();
        question.setId(301L);
        question.setCourseId(1L);
        long sourceId = IDS.incrementAndGet();
        events.recordQuestionAnswer(userId, question, "AI_VARIANT_ANSWERED", "AI_TUTOR", sourceId,
                true, LocalDateTime.now(GamificationRewardService.LEARNING_ZONE));
        events.recordTutorCheck(userId, 1L, 2L, sourceId, true);
        assertEquals(2, count("course_learning_event"));
        assertEquals(2, count("gamification_xp_ledger"));
        assertEquals(20, rewards.summary(userId).getTotalXp());
        events.recordTutorCheck(userId, 1L, 2L, sourceId, true);
        assertEquals(2, count("course_learning_event"));
        assertEquals(20, rewards.summary(userId).getTotalXp());
    }

    @Test
    void rollingBackCourseFactAlsoRollsBackRewardAndAchievements() {
        jdbc.update("INSERT INTO user_course(user_id,course_id) VALUES(?,1)", userId);
        Question question = new Question();
        question.setId(301L);
        question.setCourseId(1L);
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactionManager).execute(status -> {
            events.recordQuestionAnswer(userId, question, "PRACTICE_ANSWERED", "PRACTICE", IDS.incrementAndGet(), true, LocalDateTime.now(GamificationRewardService.LEARNING_ZONE));
            throw new IllegalStateException("rollback fixture");
        }));
        assertEquals(0, count("course_learning_event"));
        assertEquals(0, count("gamification_xp_ledger"));
        assertEquals(0, count("gamification_achievement"));
    }

    private void race(CourseLearningEvent first, CourseLearningEvent second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { ready.countDown(); start.await(10, TimeUnit.SECONDS); return rewards.reward(first, true); });
            var b = executor.submit(() -> { ready.countDown(); start.await(10, TimeUnit.SECONDS); return rewards.reward(second, true); });
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            a.get(15, TimeUnit.SECONDS);
            b.get(15, TimeUnit.SECONDS);
        }
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id=?", Integer.class, userId);
    }

    /** The event table stores the application's existing no-zone JVM clock values. */
    private CourseLearningEvent fact(long questionId, LocalDateTime learningTime, String type, String payload) {
        long id = IDS.incrementAndGet();
        LocalDateTime sourceClockTime = learningTime.atZone(GamificationRewardService.LEARNING_ZONE)
                .withZoneSameInstant(GamificationRewardService.SOURCE_CLOCK_ZONE).toLocalDateTime();
        jdbc.update("INSERT INTO course_learning_event(id,user_id,course_id,event_type,event_source,subject_type,subject_id,source_record_id,idempotency_key,event_version,payload_json,occurred_time) VALUES(?,?,1,?,'PRACTICE','QUESTION',?,?,?,1,?,?)",
                id, userId, type, questionId, id, "integrity:" + id, payload, sourceClockTime);
        CourseLearningEvent event = new CourseLearningEvent();
        event.setId(id); event.setUserId(userId); event.setCourseId(1L); event.setEventType(type);
        event.setEventSource("PRACTICE"); event.setSubjectType("QUESTION"); event.setSubjectId(questionId);
        event.setOccurredTime(sourceClockTime); event.setPayloadJson(payload);
        return event;
    }
}
