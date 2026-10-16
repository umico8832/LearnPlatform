package com.learnplatform.service.understanding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.IntegrationTestBase;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.ReviewSubmitRequest;
import com.learnplatform.service.QuestionService;
import com.learnplatform.service.ReviewScheduleQueryService;
import com.learnplatform.service.SpacedRepetitionService;
import com.learnplatform.service.WrongQuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class QuestionUnderstandingAvailabilityIntegrationTest extends IntegrationTestBase {
    private static final long USER = 989501L;
    @Autowired QuestionService questions;
    @Autowired WrongQuestionService wrong;
    @Autowired ReviewScheduleQueryService reviews;
    @Autowired SpacedRepetitionService repetition;
    @Autowired QuestionUnderstandingService understanding;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;
    private long questionId;
    private long contentId;

    @BeforeEach void setUp() {
        questionId = db.queryForObject("SELECT question_id FROM question_understanding_content WHERE source_title='系统种子生成：合法出栈序列'", Long.class);
        contentId = db.queryForObject("SELECT id FROM question_understanding_content WHERE question_id=? ORDER BY content_version DESC LIMIT 1", Long.class, questionId);
        db.update("INSERT INTO user(id,username,password,role,status,deleted) VALUES (?,'availability-user','test','USER',1,0)", USER);
        db.update("UPDATE question_understanding_content SET review_status='REVIEWED',reviewed_by='test-reviewer',reviewed_time=CURRENT_TIMESTAMP WHERE id=?", contentId);
        understanding.submit(USER, questionId, json.createObjectNode().put("answer", "B"));
        db.update("UPDATE question_review_schedule SET next_review_date=? WHERE user_id=?", LocalDate.now(), USER);
    }

    @Test void publicAndWrongListsMatchFormalReadIncludingPendingHintChain() {
        assertAvailability(true);
        assertTrue(json.valueToTree(questions.getEnabledQuestionById(questionId, USER)).path("hasUnderstanding").asBoolean());
    }

    @Test void pendingWithdrawnAndNewerUnreviewedContentDoNotAdvertiseUnderstanding() {
        db.update("UPDATE question_understanding_content SET review_status='PENDING' WHERE id=?", contentId);
        assertAvailability(false);
        db.update("UPDATE question_understanding_content SET review_status='WITHDRAWN' WHERE id=?", contentId);
        assertAvailability(false);
        db.update("UPDATE question_understanding_content SET review_status='REVIEWED' WHERE id=?", contentId);
        db.update("""
                INSERT INTO question_understanding_content
                  (question_id,content_version,review_status,question_fingerprint,knowledge_point_id,
                   solution_explanation,source_verification)
                SELECT question_id,content_version+1,'PENDING',question_fingerprint,knowledge_point_id,
                       solution_explanation,source_verification FROM question_understanding_content WHERE id=?
                """, contentId);
        assertAvailability(false);
    }

    @Test void changedQuestionInvalidGradingAndMissingKnowledgeLinkMatchFormalRead() {
        db.update("UPDATE question SET content=CONCAT(content,'changed') WHERE id=?", questionId);
        assertAvailability(false);
        db.update("UPDATE question SET content=LEFT(content,CHAR_LENGTH(content)-7) WHERE id=?", questionId);
        db.update("UPDATE question_option SET is_correct=0 WHERE question_id=? AND option_label='A'", questionId);
        assertAvailability(false);
        db.update("UPDATE question_option SET is_correct=1 WHERE question_id=? AND option_label='A'", questionId);
        db.update("DELETE FROM question_knowledge_point WHERE question_id=?", questionId);
        assertAvailability(false);
    }

    @Test void unpublishedCourseAndOtherUsersPrivateQuestionsCannotOfferEntry() {
        db.update("UPDATE course SET status=0 WHERE id=(SELECT course_id FROM question WHERE id=?)", questionId);
        assertAvailability(false);
        db.update("UPDATE course SET status=1 WHERE id=(SELECT course_id FROM question WHERE id=?)", questionId);
        db.update("UPDATE question SET visibility='PRIVATE',owner_user_id=? WHERE id=?", USER + 1, questionId);
        assertWrongMatches(false);
        assertTrue(questions.getEnabledQuestionPage(1, 10, null, null, null, questionId, null).getRecords().isEmpty());
        db.update("UPDATE question SET owner_user_id=? WHERE id=?", USER, questionId);
        assertWrongMatches(true);
        assertTrue(json.valueToTree(questions.getEnabledQuestionById(questionId, USER)).path("hasUnderstanding").asBoolean());
        db.update("UPDATE question SET status=0 WHERE id=?", questionId);
        assertWrongMatches(false);
        db.update("UPDATE question SET status=1,deleted=1 WHERE id=?", questionId);
        assertWrongMatches(false);
    }

    @Test void noContentAndInvalidOfficialSourceGiveFalseRatherThanFailingLists() {
        db.update("UPDATE question_understanding_content SET source_exam_question_id=-1 WHERE id=?", contentId);
        assertAvailability(false);
        db.update("DELETE FROM question_understanding_content WHERE question_id=?", questionId);
        assertAvailability(false);
    }

    @Test void reviewCardsUseTheirOwnerAndDoNotReadOtherUsersSchedules() {
        long other = USER + 1;
        db.update("INSERT INTO question_review_schedule(user_id,question_id,next_review_date) VALUES(?,?,?)",
                other, questionId, LocalDate.now());
        db.update("UPDATE question SET visibility='PRIVATE',owner_user_id=? WHERE id=?", USER, questionId);
        sqlSession.clearCache();
        for (long user : new long[] {USER, other}) {
            var cards = reviews.getAllReviewCards(user, null);
            assertEquals(1, cards.size());
            assertEquals(user == USER, json.valueToTree(cards.getFirst()).path("hasUnderstanding").asBoolean());
            assertEquals(user == USER, json.valueToTree(reviews.getDueReviewCards(user, null, 50).getFirst())
                    .path("hasUnderstanding").asBoolean());
        }
        db.update("UPDATE question_review_schedule SET deleted=1 WHERE user_id=?", other);
        sqlSession.clearCache();
        assertTrue(reviews.getAllReviewCards(other, null).isEmpty());
        assertTrue(reviews.getDueReviewCards(other, null, 50).isEmpty());
        assertEquals(1, reviews.getAllReviewCards(USER, null).size());
    }

    @Test void draftQuestionsRemainExcludedFromReviewLists() {
        db.update("UPDATE question SET visibility='DRAFT' WHERE id=?", questionId);
        sqlSession.clearCache();
        assertTrue(reviews.getAllReviewCards(USER, null).isEmpty());
        assertTrue(reviews.getDueReviewCards(USER, null, 50).isEmpty());
    }

    @Test void submitResponseUsesTheSameUnderstandingGate() {
        ReviewSubmitRequest request = new ReviewSubmitRequest();
        request.setQuestionId(questionId);
        request.setUserAnswer("A");
        assertTrue(json.valueToTree(repetition.submitReview(request, USER)).path("hasUnderstanding").asBoolean());
        db.update("UPDATE question_understanding_content SET review_status='PENDING' WHERE id=?", contentId);
        var card = json.valueToTree(repetition.submitReview(request, USER));
        assertTrue(card.path("hasUnderstanding").isBoolean());
        assertFalse(card.path("hasUnderstanding").asBoolean());
    }

    @Test void moreReviewCardsDoNotAddPerCardQueries() {
        long singleAll = reviewQueryCount(false, 1);
        long singleDue = reviewQueryCount(true, 1);
        int added = db.update("""
                INSERT INTO question_review_schedule(user_id,question_id,next_review_date)
                SELECT ?,q.id,? FROM question q WHERE q.id<>? AND q.visibility='PUBLIC' AND q.deleted=0
                  AND q.id IN (SELECT question_id FROM question_understanding_content WHERE review_status='REVIEWED')
                """, USER, LocalDate.now(), questionId);
        assertTrue(added > 1);
        assertEquals(singleAll, reviewQueryCount(false, added + 1));
        assertEquals(singleDue, reviewQueryCount(true, added + 1));
    }

    private long reviewQueryCount(boolean due, int expectedSize) {
        sqlSession.clearCache();
        long before = statementCount();
        var cards = due ? reviews.getDueReviewCards(USER, null, 50) : reviews.getAllReviewCards(USER, null);
        long queries = statementCount() - before;
        assertEquals(expectedSize, cards.size());
        return queries;
    }

    private long statementCount() {
        return db.queryForObject("SHOW SESSION STATUS LIKE 'Questions'", (row, index) -> row.getLong(2));
    }

    private void assertAvailability(boolean expected) {
        assertWrongMatches(expected);
        var page = questions.getEnabledQuestionPage(1, 10, null, null, null, questionId, null);
        assertEquals(1, page.getRecords().size());
        var row = json.valueToTree(page.getRecords().getFirst());
        assertTrue(row.has("hasUnderstanding"));
        assertEquals(expected, row.path("hasUnderstanding").asBoolean());
        assertFalse(row.hasNonNull("analysis"));
        var matching = questions.getEnabledQuestionPage(1, 10, null, null, null,
                questionId, null, expected, USER);
        assertEquals(1, matching.getTotal());
        assertEquals(1, matching.getRecords().size());
        var excluded = questions.getEnabledQuestionPage(1, 10, null, null, null,
                questionId, null, !expected, USER);
        assertEquals(0, excluded.getTotal());
        assertTrue(excluded.getRecords().isEmpty());
    }

    private void assertWrongMatches(boolean expected) {
        sqlSession.clearCache();
        boolean readable;
        try { understanding.question(USER, questionId); readable = true; }
        catch (BusinessException exception) { readable = false; }
        assertEquals(expected, readable);
        var page = wrong.getWrongQuestions(USER, 1, 10, null, questionId, null, null);
        assertEquals(1, page.getRecords().size());
        var row = json.valueToTree(page.getRecords().getFirst());
        assertTrue(row.has("hasUnderstanding"));
        assertEquals(expected, row.path("hasUnderstanding").asBoolean());
        var all = reviews.getAllReviewCards(USER, null);
        assertEquals(1, all.size());
        var due = reviews.getDueReviewCards(USER, null, 50);
        assertEquals(all.getFirst().isAvailableForReview() ? 1 : 0, due.size());
        for (var view : Stream.concat(all.stream(), due.stream()).toList()) {
            var card = json.valueToTree(view);
            assertTrue(card.has("hasUnderstanding"));
            assertTrue(card.path("hasUnderstanding").isBoolean());
            assertEquals(expected, card.path("hasUnderstanding").asBoolean());
        }
    }
}
