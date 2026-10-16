package com.learnplatform.service.review;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.dto.ReviewScheduleVO;
import com.learnplatform.dto.ReviewStatsVO;
import com.learnplatform.service.ReviewScheduleQueryService;
import com.learnplatform.service.SpacedRepetitionService;
import com.learnplatform.service.question.AutomaticGradingPolicy;
import io.github.openspacedrepetition.Card;
import io.github.openspacedrepetition.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class ReviewStatsIntegrationTest extends IntegrationTestBase {
    private static final long USER = 989314L;
    private static final long OTHER = 989315L;
    @Autowired ReviewScheduleQueryService queries;
    @Autowired FsrsReviewService fsrs;
    @Autowired SpacedRepetitionService repetition;
    @Autowired JdbcTemplate db;
    private List<Long> questions;

    @BeforeEach
    void setUp() {
        questions = db.queryForList("SELECT id FROM question WHERE visibility <> 'DRAFT' AND id IN ("
                + AutomaticGradingPolicy.eligibleQuestionIdsSql() + ") ORDER BY id LIMIT 9", Long.class);
        assertEquals(9, questions.size());
    }

    @Test
    void statsAndListUseTheSameStateAndRatingAcrossLegacyAndUnavailableCards() {
        LocalDate today = LocalDate.now();
        insert(USER, 0, null, null, today, 0);
        insert(USER, 1, null, null, today.minusDays(1), 5);
        insert(USER, 2, State.LEARNING, "GOOD", today.plusDays(2), 1);
        insert(USER, 3, State.REVIEW, "AGAIN", today.plusDays(100), 9);
        insert(USER, 4, State.RELEARNING, "HARD", today.minusDays(2), 2);
        insert(USER, 5, State.REVIEW, "EASY", today.plusDays(30), 5);
        insert(USER, 6, State.REVIEW, "AGAIN", today, 2);
        insert(USER, 7, State.REVIEW, "HARD", today, 2);
        insert(USER, 8, State.REVIEW, "AGAIN", today, 2);
        insert(OTHER, 0, State.REVIEW, "HARD", today, 2);
        db.update("UPDATE question_review_schedule SET last_review_date=? WHERE user_id=? AND question_id=?",
                today.minusDays(1), USER, questions.get(5));
        db.update("UPDATE question_option SET is_correct=0 WHERE question_id=?", questions.get(6));
        db.update("UPDATE question_review_schedule SET deleted=1 WHERE user_id=? AND question_id=?",
                USER, questions.get(7));
        db.update("UPDATE question SET visibility='DRAFT' WHERE id=?", questions.get(8));

        ReviewStatsVO stats = queries.getReviewStats(USER);
        List<ReviewScheduleVO> cards = queries.getAllReviewCards(USER, null);

        assertEquals(7, stats.getTotalCards());
        assertEquals(7, cards.size());
        assertEquals(2, stats.getPendingRatingCards());
        assertEquals(1, stats.getLearningCards());
        assertEquals(3, stats.getReviewCards());
        assertEquals(1, stats.getRelearningCards());
        assertEquals(2, stats.getLastAgainCards());
        assertEquals(1, stats.getLastHardCards());
        assertEquals(stats.getPendingRatingCards(), countState(cards, "UNRATED"));
        assertEquals(stats.getLearningCards(), countState(cards, "LEARNING"));
        assertEquals(stats.getReviewCards(), countState(cards, "REVIEW"));
        assertEquals(stats.getRelearningCards(), countState(cards, "RELEARNING"));
        assertEquals(stats.getLastAgainCards(), cards.stream().filter(c -> "AGAIN".equals(c.getLastRating())).count());
        assertEquals(stats.getLastHardCards(), cards.stream().filter(c -> "HARD".equals(c.getLastRating())).count());
        assertEquals(3, stats.getDueToday());
        assertEquals(2, stats.getOverdue());
        assertEquals(5, stats.getReviewedToday());
        assertEquals(stats.getDueToday(), queries.getDueReviewCards(USER, null, 50).size());
        assertFalse(cards.stream().filter(c -> c.getQuestionId().equals(questions.get(6)))
                .findFirst().orElseThrow().isAvailableForReview());
        assertTrue(cards.stream().noneMatch(c -> c.getStatusLabel().contains("掌握")));
        assertEquals(1, queries.getReviewStats(OTHER).getLastHardCards());
    }

    @Test
    void firstIndependentScoreAndResetChangeStateWithoutRewritingLegacyHistory() {
        LocalDate legacyDue = LocalDate.now().plusDays(80);
        insert(USER, 0, null, null, legacyDue, 8);
        ReviewScheduleVO legacy = queries.getAllReviewCards(USER, null).getFirst();
        assertEquals("UNRATED", legacy.getSchedulingState());
        assertEquals(legacyDue, legacy.getNextReviewDate());
        assertNull(legacy.getLastRating());
        assertEquals(0, queries.getReviewStats(USER).getLastAgainCards());

        var scored = fsrs.score(USER, questions.get(0), FsrsReviewService.Source.REVIEW, 98931401L, false, 5);
        assertEquals("AGAIN", ReviewSchedulePolicy.lastRating(scored));
        assertEquals(ReviewSchedulePolicy.schedulingState(scored).name(),
                queries.getAllReviewCards(USER, null).getFirst().getSchedulingState());
        assertEquals(1, queries.getReviewStats(USER).getLastAgainCards());
        assertEquals(0, queries.getReviewStats(USER).getPendingRatingCards());

        repetition.resetReviewProgress(USER, questions.get(0));
        ReviewStatsVO reset = queries.getReviewStats(USER);
        assertEquals(1, reset.getPendingRatingCards());
        assertEquals(0, reset.getLastAgainCards());
        assertEquals(0, reset.getReviewedToday());
        assertEquals(1, reset.getDueToday());
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM fsrs_review_event WHERE user_id=?", Integer.class, USER));
        fsrs.score(USER, questions.get(1), FsrsReviewService.Source.PRACTICE, 98931402L, true, null);
        assertEquals(1, queries.getReviewStats(USER).getReviewCards());
        assertEquals(2, queries.getReviewStats(USER).getTotalCards());
    }

    @Test
    void emptyPlanReturnsZeroCounts() {
        ReviewStatsVO stats = queries.getReviewStats(USER);
        assertEquals(0, stats.getTotalCards());
        assertEquals(0, stats.getDueToday());
        assertEquals(0, stats.getReviewedToday());
        assertEquals(0, stats.getPendingRatingCards());
        assertEquals(0, stats.getLearningCards());
        assertEquals(0, stats.getReviewCards());
        assertEquals(0, stats.getRelearningCards());
        assertEquals(0, stats.getLastAgainCards());
        assertEquals(0, stats.getLastHardCards());
    }

    private long countState(List<ReviewScheduleVO> cards, String state) {
        return cards.stream().filter(c -> state.equals(c.getSchedulingState())).count();
    }

    private void insert(long user, int index, State state, String rating, LocalDate due, int reviews) {
        Card card = Card.builder().build();
        if (state != null) {
            card.setState(state);
        }
        db.update("""
                INSERT INTO question_review_schedule
                (user_id,question_id,ease_factor,interval_days,next_review_date,total_reviews,
                 last_review_date,last_quality,fsrs_card_json,fsrs_last_rating)
                VALUES(?,?,1.50,100,?,?,?,1,?,?)
                """, user, questions.get(index), due, reviews, reviews == 0 ? null : LocalDate.now(),
                state == null ? null : card.toJson(), rating);
    }
}
