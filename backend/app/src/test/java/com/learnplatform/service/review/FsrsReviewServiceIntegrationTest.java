package com.learnplatform.service.review;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.common.exception.BusinessException;
import io.github.openspacedrepetition.Card;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
class FsrsReviewServiceIntegrationTest extends IntegrationTestBase {
    private static final long USER = 989303L;
    @Autowired FsrsReviewService service;
    @Autowired JdbcTemplate jdbc;
    private long questionId;

    @BeforeEach void setUp() {
        questionId = jdbc.queryForObject("""
                SELECT question_id FROM question_understanding_content
                WHERE source_title='系统种子生成：合法出栈序列'
                """, Long.class);
        cleanup();
        jdbc.update("""
                INSERT INTO question_review_schedule
                (user_id,question_id,ease_factor,interval_days,repetitions,next_review_date,last_quality,total_reviews)
                VALUES(?,?,2.10,14,3,DATE_ADD(CURRENT_DATE,INTERVAL 14 DAY),4,5)
                """, USER, questionId);
    }

    @AfterEach void tearDown() { cleanup(); }

    @Test void preservesLegacyDueUntilFirstIndependentRatingAndDeduplicatesSource() {
        LocalDate legacyDue = jdbc.queryForObject("""
                SELECT next_review_date FROM question_review_schedule WHERE user_id=? AND question_id=?
                """, LocalDate.class, USER, questionId);
        assertEquals(LocalDate.now().plusDays(14), legacyDue);
        assertNull(jdbc.queryForObject("""
                SELECT fsrs_card_json FROM question_review_schedule WHERE user_id=? AND question_id=?
                """, String.class, USER, questionId));

        service.score(USER, questionId, FsrsReviewService.Source.PRACTICE, 901L, true, null);
        service.score(USER, questionId, FsrsReviewService.Source.PRACTICE, 901L, true, null);
        String cardJson = jdbc.queryForObject("""
                SELECT fsrs_card_json FROM question_review_schedule WHERE user_id=? AND question_id=?
                """, String.class, USER, questionId);
        assertNotNull(cardJson);
        LocalDate fsrsDue = Card.fromJson(cardJson).getDue().atZone(ZoneId.systemDefault()).toLocalDate();
        assertEquals(fsrsDue, jdbc.queryForObject("""
                SELECT next_review_date FROM question_review_schedule WHERE user_id=? AND question_id=?
                """, LocalDate.class, USER, questionId));
        assertEquals(6, jdbc.queryForObject("""
                SELECT total_reviews FROM question_review_schedule WHERE user_id=? AND question_id=?
                """, Integer.class, USER, questionId));
        assertNull(jdbc.queryForObject("""
                SELECT ease_factor FROM question_review_schedule WHERE user_id=? AND question_id=?
                """, Object.class, USER, questionId));
        assertEquals(1, countEvents());

        service.score(USER, questionId, FsrsReviewService.Source.REVIEW, 902L, false, 5);
        assertEquals("AGAIN", jdbc.queryForObject("""
                SELECT fsrs_last_rating FROM question_review_schedule WHERE user_id=? AND question_id=?
                """, String.class, USER, questionId));
        assertEquals(2, countEvents());
    }

    @Test void concurrentRetryOfOneSourceChangesCardOnce() throws Exception {
        var latch = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> {
                latch.await();
                return service.score(USER, questionId, FsrsReviewService.Source.PRACTICE, 903L, true, null);
            });
            var retry = pool.submit(() -> {
                latch.await();
                return service.score(USER, questionId, FsrsReviewService.Source.PRACTICE, 903L, true, null);
            });
            latch.countDown();
            first.get();
            retry.get();
        }
        assertEquals(1, countEvents());
        assertEquals(6, jdbc.queryForObject("""
                SELECT total_reviews FROM question_review_schedule WHERE user_id=? AND question_id=?
                """, Integer.class, USER, questionId));
    }

    private int countEvents() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM fsrs_review_event WHERE user_id=?", Integer.class, USER);
    }

    @ParameterizedTest
    @CsvSource({"0,HARD,3", "3,HARD,3", "4,GOOD,4", "5,EASY,5"})
    void independentCorrectSelfAssessmentMapsToStoredFsrsRating(int quality, String rating, int storedQuality) {
        service.score(USER, questionId, FsrsReviewService.Source.REVIEW, 904L, true, quality);
        var schedule = jdbc.queryForMap("""
                SELECT fsrs_last_rating,last_quality,total_reviews FROM question_review_schedule
                WHERE user_id=? AND question_id=? AND deleted=0
                """, USER, questionId);
        assertEquals(rating, schedule.get("fsrs_last_rating"));
        assertEquals(storedQuality, ((Number) schedule.get("last_quality")).intValue());
        assertEquals(6, ((Number) schedule.get("total_reviews")).intValue());
        assertEquals(rating, jdbc.queryForObject(
                "SELECT rating FROM fsrs_review_event WHERE user_id=?", String.class, USER));
        assertEquals(1, countEvents());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 6})
    void invalidSelfAssessmentDoesNotMutateCardOrCreateEvent(int quality) {
        var before = jdbc.queryForMap("SELECT * FROM question_review_schedule WHERE user_id=? AND question_id=?",
                USER, questionId);
        assertEquals(1001, assertThrows(BusinessException.class, () -> service.score(
                USER, questionId, FsrsReviewService.Source.REVIEW, 905L, true, quality)).getCode());
        assertEquals(before, jdbc.queryForMap("SELECT * FROM question_review_schedule WHERE user_id=? AND question_id=?",
                USER, questionId));
        assertEquals(0, countEvents());
    }

    @Test void sourceCollisionWithAnotherQuestionRollsBackNewSchedule() {
        service.score(USER, questionId, FsrsReviewService.Source.PRACTICE, 906L, true, null);
        long otherQuestion = jdbc.queryForObject("SELECT MIN(id) FROM question WHERE id<>? AND deleted=0",
                Long.class, questionId);
        var before = jdbc.queryForMap("SELECT * FROM question_review_schedule WHERE user_id=? AND question_id=?",
                USER, questionId);
        assertThrows(IllegalStateException.class, () -> service.score(
                USER, otherQuestion, FsrsReviewService.Source.PRACTICE, 906L, true, 5));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM question_review_schedule WHERE user_id=? AND question_id=?",
                Integer.class, USER, otherQuestion));
        assertEquals(before, jdbc.queryForMap("SELECT * FROM question_review_schedule WHERE user_id=? AND question_id=?",
                USER, questionId));
        assertEquals(1, countEvents());
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void missingPersistentSourceCannotCreateReviewEvidence(long sourceId) {
        assertThrows(IllegalArgumentException.class, () -> service.score(
                USER, questionId, FsrsReviewService.Source.REVIEW, sourceId, true, null));
        assertEquals(0, countEvents());
        assertNull(jdbc.queryForObject("""
                SELECT fsrs_card_json FROM question_review_schedule WHERE user_id=? AND question_id=?
                """, String.class, USER, questionId));
    }

    private void cleanup() {
        if (jdbc == null || questionId == 0) { return; }
        jdbc.update("DELETE FROM fsrs_review_event WHERE user_id=?", USER);
        jdbc.update("DELETE FROM question_review_schedule WHERE user_id=?", USER);
    }
}
