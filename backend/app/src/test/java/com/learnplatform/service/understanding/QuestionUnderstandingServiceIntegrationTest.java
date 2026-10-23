package com.learnplatform.service.understanding;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.learnplatform.IntegrationTestBase;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.service.WrongQuestionService;
import com.learnplatform.service.CourseOverviewService;
import com.learnplatform.service.ReviewScheduleQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
class QuestionUnderstandingServiceIntegrationTest extends IntegrationTestBase {
    private static final long USER = 989301L;
    private static final long OTHER = 989302L;
    @Autowired QuestionUnderstandingService service;
    @Autowired WrongQuestionService wrongQuestions;
    @Autowired CourseOverviewService overviews;
    @Autowired ReviewScheduleQueryService reviewQueries;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    private long questionId;
    private long courseId;
    private List<Map<String, Object>> originalContent;
    private List<Map<String, Object>> originalHints;
    private List<Map<String, Object>> originalVariants;

    @BeforeEach void setUp() {
        originalContent = jdbc.queryForList("SELECT * FROM question_understanding_content WHERE source_title IN ('系统种子生成：合法出栈序列','2026 年 408 真题 第 3 题')");
        originalHints = jdbc.queryForList("SELECT * FROM question_understanding_hint WHERE content_id IN (SELECT id FROM question_understanding_content WHERE source_title='系统种子生成：合法出栈序列')");
        originalVariants = jdbc.queryForList("SELECT v.* FROM ai_variant_question v JOIN question_ai_asset a ON a.id=v.asset_id JOIN question_understanding_content c ON c.question_id=a.question_id WHERE c.source_title='系统种子生成：合法出栈序列'");
        questionId = jdbc.queryForObject("SELECT question_id FROM question_understanding_content WHERE source_title='系统种子生成：合法出栈序列'", Long.class);
        courseId = jdbc.queryForObject("SELECT course_id FROM question WHERE id=?", Long.class, questionId);
        jdbc.update("UPDATE question_understanding_content SET review_status='REVIEWED',reviewed_by='test-reviewer',reviewed_time=CURRENT_TIMESTAMP WHERE question_id=?", questionId);
        jdbc.update("UPDATE ai_variant_question v JOIN question_ai_asset a ON a.id=v.asset_id SET v.review_status='APPROVED',v.reviewed_by=0,v.reviewed_time=CURRENT_TIMESTAMP WHERE a.question_id=?", questionId);
        jdbc.update("UPDATE question_understanding_hint SET review_status='PENDING',reviewed_by=NULL,reviewed_time=NULL WHERE content_id IN (SELECT id FROM question_understanding_content WHERE question_id=?)", questionId);
        jdbc.update("INSERT INTO user (id,username,password,role,status,deleted) VALUES (?,?,'test','USER',1,0), (?,?,'test','USER',1,0)",
                USER, "understanding-owner", OTHER, "understanding-other");
        jdbc.update("INSERT INTO user_course(user_id,course_id) VALUES(?,?)", USER, courseId);
    }

    @AfterEach void tearDown() {
        jdbc.update("DELETE FROM fsrs_review_event WHERE user_id IN (?,?)", USER, OTHER);
        jdbc.update("DELETE FROM question_understanding_hint_receipt WHERE attempt_id IN (SELECT id FROM question_understanding_attempt WHERE user_id IN (?,?))", USER, OTHER);
        jdbc.update("DELETE FROM question_understanding_viz_view WHERE attempt_id IN (SELECT id FROM question_understanding_attempt WHERE user_id IN (?,?))", USER, OTHER);
        jdbc.update("DELETE FROM course_learning_event WHERE user_id IN (?,?)", USER, OTHER);
        jdbc.update("DELETE FROM question_understanding_variant_attempt WHERE user_id IN (?,?)", USER, OTHER);
        jdbc.update("DELETE FROM question_understanding_attempt WHERE user_id IN (?,?)", USER, OTHER);
        jdbc.update("DELETE FROM practice_record WHERE user_id IN (?,?)", USER, OTHER);
        jdbc.update("DELETE FROM wrong_question WHERE user_id IN (?,?)", USER, OTHER);
        jdbc.update("DELETE FROM question_review_schedule WHERE user_id IN (?,?)", USER, OTHER);
        jdbc.update("DELETE FROM user_course WHERE user_id IN (?,?)", USER, OTHER);
        jdbc.update("DELETE FROM user WHERE id IN (?,?)", USER, OTHER);
        for (var row : originalContent) {
            jdbc.update("UPDATE question_understanding_content SET review_status=?,reviewed_by=?,reviewed_time=?,source_verification=?,source_verified_by=?,source_verified_time=? WHERE id=?",
                    row.get("review_status"), row.get("reviewed_by"), row.get("reviewed_time"), row.get("source_verification"), row.get("source_verified_by"), row.get("source_verified_time"), row.get("id"));
        }
        for (var row : originalHints) {
            jdbc.update("UPDATE question_understanding_hint SET review_status=?,reviewed_by=?,reviewed_time=? WHERE id=?",
                    row.get("review_status"), row.get("reviewed_by"), row.get("reviewed_time"), row.get("id"));
        }
        for (var row : originalVariants) {
            jdbc.update("UPDATE ai_variant_question SET review_status=?,reviewed_by=?,reviewed_time=? WHERE id=?",
                    row.get("review_status"), row.get("reviewed_by"), row.get("reviewed_time"), row.get("id"));
        }
    }

    @Test void hidesAnswersAndUsesOnlyChosenMisconception() throws Exception {
        Map<String, Object> question = service.question(USER, questionId);
        String publicJson = json.writeValueAsString(question);
        assertFalse(publicJson.contains("isCorrect"));
        assertFalse(publicJson.contains("correctAnswer"));
        assertFalse(publicJson.contains("explanation"));
        assertThrowsCode(1004, () -> service.question(USER, -1));
        Map<String, Object> attempt = service.submit(USER, questionId, body("B"));
        assertFalse(attempt.containsKey("correctAnswer"));
        assertEquals(true, ((Map<?, ?>) attempt.get("review")).get("counted"));
        assertNotNull(((Map<?, ?>) attempt.get("review")).get("nextReviewDate"));
        assertEquals("以为能越过 2 直接弹出 1", ((Map<?, ?>) attempt.get("misconception")).get("title"));
        assertEquals(1, count("SELECT COUNT(*) FROM practice_record WHERE user_id=? AND question_id=?", USER, questionId));
        assertEquals(1, count("SELECT COUNT(*) FROM course_learning_event WHERE user_id=? AND event_source='PRACTICE'", USER));
        long attemptId = ((Number) attempt.get("attemptId")).longValue();
        assertThrowsCode(1004, () -> service.reveal(OTHER, attemptId));
        assertThrowsCode(1004, () -> service.variant(USER, attemptId));
        assertEquals("A", service.reveal(USER, attemptId).get("correctAnswer"));
        assertEquals(service.reveal(USER, attemptId), service.reveal(USER, attemptId));
        assertNotNull(jdbc.queryForObject("SELECT solution_revealed_time FROM question_understanding_attempt WHERE id=?", Object.class, attemptId));
        assertFalse(json.writeValueAsString(service.variant(USER, attemptId)).contains("correctAnswer"));
        assertFalse(json.writeValueAsString(service.variant(USER, attemptId)).contains("analysis"));
        assertThrowsCode(1001, () -> service.submit(USER, questionId, body("Z")));
        assertEquals(1, count("SELECT COUNT(*) FROM practice_record WHERE user_id=? AND question_id=?", USER, questionId));
    }

    @Test void treeSourceUsesVerifiedTitleAndRequiresReviewer() {
        long treeId = jdbc.queryForObject("""
                SELECT question_id FROM question_understanding_content
                WHERE source_title='2026 年 408 真题 第 3 题'
                """, Long.class);
        jdbc.update("""
                UPDATE question_understanding_content SET review_status='REVIEWED',reviewed_by='test-reviewer',
                  reviewed_time=CURRENT_TIMESTAMP,source_verification='VERIFIED',source_verified_by='test-reviewer',
                  source_verified_time=CURRENT_TIMESTAMP WHERE question_id=?
                """, treeId);
        try {
            Map<String, Object> view = service.question(USER, treeId);
            Map<?, ?> source = (Map<?, ?>) view.get("source");
            assertEquals("OFFICIAL_EXAM", source.get("kind"));
            assertEquals("VERIFIED", source.get("verification"));
            assertEquals("2026 年 408 真题 第 3 题", source.get("title"));
            assertEquals("树和二叉树", ((Map<?, ?>) view.get("position")).get("chapterName"));
            jdbc.update("UPDATE question_understanding_content SET source_verified_by=NULL WHERE question_id=?", treeId);
            Map<?, ?> unreviewed = (Map<?, ?>) service.question(USER, treeId).get("source");
            assertEquals("MANUAL", unreviewed.get("kind"));
        } finally {
            jdbc.update("""
                    UPDATE question_understanding_content SET review_status='PENDING',reviewed_by=NULL,
                      reviewed_time=NULL,source_verification='PENDING',source_verified_by=NULL,
                      source_verified_time=NULL WHERE question_id=?
                    """, treeId);
        }
    }

    @Test void pendingContentAndVariantsAreUnavailableInPublicFlow() {
        jdbc.update("UPDATE question_understanding_content SET review_status='PENDING',reviewed_by=NULL,reviewed_time=NULL WHERE question_id=?", questionId);
        assertThrowsCode(1004, () -> service.question(USER, questionId));
        assertThrowsCode(1004, () -> service.submit(USER, questionId, body("A")));
        assertEquals(0, count("SELECT COUNT(*) FROM practice_record WHERE user_id=?", USER));
        jdbc.update("UPDATE question_understanding_content SET review_status='REVIEWED',reviewed_by='test-reviewer',reviewed_time=CURRENT_TIMESTAMP WHERE question_id=?", questionId);
        jdbc.update("UPDATE ai_variant_question v JOIN question_ai_asset a ON a.id=v.asset_id SET v.review_status='PENDING',v.reviewed_by=NULL,v.reviewed_time=NULL WHERE a.question_id=?", questionId);
        long origin = ((Number) service.submit(USER, questionId, body("A")).get("attemptId")).longValue();
        service.reveal(USER, origin);
        assertThrowsCode(1004, () -> service.variant(USER, origin));
    }

    @Test void rechecksPublicationCourseAccessAndFingerprint() throws Exception {
        jdbc.update("UPDATE course SET status=0 WHERE id=?", courseId);
        try { assertThrowsCode(1004, () -> service.question(USER, questionId)); }
        finally { jdbc.update("UPDATE course SET status=1 WHERE id=?", courseId); }
        jdbc.update("UPDATE question SET visibility='PRIVATE',owner_user_id=? WHERE id=?", OTHER, questionId);
        try { assertThrowsCode(1004, () -> service.question(USER, questionId)); }
        finally { jdbc.update("UPDATE question SET visibility='PUBLIC',owner_user_id=NULL WHERE id=?", questionId); }
        jdbc.update("UPDATE question_understanding_content SET review_status='WITHDRAWN' WHERE question_id=?", questionId);
        try { assertThrowsCode(1004, () -> service.question(USER, questionId)); }
        finally { jdbc.update("UPDATE question_understanding_content SET review_status='REVIEWED' WHERE question_id=?", questionId); }
        jdbc.update("UPDATE question SET content=CONCAT(content,'改') WHERE id=?", questionId);
        try { assertThrowsCode(1004, () -> service.submit(USER, questionId, body("A"))); }
        finally { jdbc.update("UPDATE question SET content=LEFT(content,CHAR_LENGTH(content)-1) WHERE id=?", questionId); }
        assertEquals(0, count("SELECT COUNT(*) FROM practice_record WHERE user_id=?", USER));
    }

    @Test void invalidGradingBasisAndWithdrawnAttemptDoNotLeakOrWriteFacts() throws Exception {
        long origin = ((Number) service.submit(USER, questionId, body("B")).get("attemptId")).longValue();
        jdbc.update("UPDATE question_understanding_content SET review_status='WITHDRAWN' WHERE question_id=?", questionId);
        try {
            assertThrowsCode(1004, () -> service.reveal(USER, origin));
            assertThrowsCode(1004, () -> service.variant(USER, origin));
        } finally {
            jdbc.update("UPDATE question_understanding_content SET review_status='REVIEWED' WHERE question_id=?", questionId);
        }
        jdbc.update("UPDATE question_option SET is_correct=0 WHERE question_id=? AND option_label='A'", questionId);
        try {
            assertThrowsCode(1005, () -> service.submit(USER, questionId, body("B")));
        } finally {
            jdbc.update("UPDATE question_option SET is_correct=1 WHERE question_id=? AND option_label='A'", questionId);
        }
        assertEquals(1, count("SELECT COUNT(*) FROM practice_record WHERE user_id=? AND question_id=?", USER, questionId));
    }

    @Test void locksVariantAcrossConcurrentRetryAndStoresUnaidedEvent() throws Exception {
        long origin = ((Number) service.submit(USER, questionId, body("A")).get("attemptId")).longValue();
        service.reveal(USER, origin);
        long variant = ((Number) service.variant(USER, origin).get("variantId")).longValue();
        JsonNode request = variantBody(origin, "D", null);
        var latch = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<Map<String, Object>> first = pool.submit(() -> { latch.await(); return service.submitVariant(USER, variant, request); });
            Future<Map<String, Object>> second = pool.submit(() -> { latch.await(); return service.submitVariant(USER, variant, request); });
            latch.countDown();
            assertEquals(first.get(), second.get());
        }
        Map<String, Object> result = service.submitVariant(USER, variant, request);
        assertEquals("UNAIDED", result.get("assistance"));
        assertEquals(true, ((Map<?, ?>) result.get("review")).get("counted"));
        assertNotNull(((Map<?, ?>) result.get("review")).get("nextReviewDate"));
        assertEquals(2, count("SELECT COUNT(*) FROM fsrs_review_event WHERE user_id=? AND question_id=?", USER, questionId));
        assertEquals(true, result.get("correct"));
        assertEquals("D", result.get("correctAnswer"));
        assertEquals(1, count("SELECT COUNT(*) FROM question_understanding_variant_attempt WHERE origin_attempt_id=? AND assistance='UNAIDED'", origin));
        assertEquals(1, count("SELECT COUNT(*) FROM course_learning_event WHERE user_id=? AND event_source='UNDERSTANDING_VARIANT'", USER));
        assertThrowsCode(1005, () -> service.submitVariant(USER, variant,
                variantBody(origin, "A", null)));
        assertThrowsCode(1001, () -> service.submitVariant(USER, variant,
                variantBody(origin, "D", "HINTED")));
    }

    @Test void reviewedHintsGateSolutionAndMarkVariantAided() throws Exception {
        jdbc.update("UPDATE question_understanding_hint SET review_status='REVIEWED',reviewed_by='test-reviewer',reviewed_time=CURRENT_TIMESTAMP WHERE content_id IN (SELECT id FROM question_understanding_content WHERE question_id=?)", questionId);
        long origin = ((Number) service.submit(USER, questionId, body("B")).get("attemptId")).longValue();
        assertEquals("HINT", ((Map<?, ?>) service.tutorNext(USER, origin).get("action")).get("type"));
        assertThrowsCode(1005, () -> service.reveal(USER, origin));
        assertThrowsCode(1004, () -> service.tutorHint(OTHER, origin, hintBody("00000000-0000-4000-8000-000000000001")));
        JsonNode firstRequest = hintBody("00000000-0000-4000-8000-000000000001");
        var latch = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<Map<String, Object>> first = pool.submit(() -> { latch.await(); return service.tutorHint(USER, origin, firstRequest); });
            Future<Map<String, Object>> retry = pool.submit(() -> { latch.await(); return service.tutorHint(USER, origin, firstRequest); });
            latch.countDown();
            assertEquals(first.get(), retry.get());
        }
        assertEquals(1, count("SELECT COUNT(*) FROM question_understanding_hint_receipt WHERE attempt_id=?", origin));
        assertEquals(1, service.tutorHint(USER, origin, firstRequest).get("level"));
        assertThrowsCode(1001, () -> service.tutorHint(USER, origin, hintBody("invalid")));
        assertEquals(2, service.tutorHint(USER, origin, hintBody("00000000-0000-4000-8000-000000000002")).get("level"));
        assertThrowsCode(1005, () -> service.reveal(USER, origin));
        assertEquals(3, service.tutorHint(USER, origin, hintBody("00000000-0000-4000-8000-000000000003")).get("level"));
        assertThrowsCode(1005, () -> service.tutorHint(USER, origin, hintBody("00000000-0000-4000-8000-000000000004")));
        Map<?, ?> hintSource = (Map<?, ?>) service.tutorHint(USER, origin, firstRequest).get("source");
        assertEquals("AI_GENERATED", hintSource.get("kind"));
        assertEquals("REVIEWED", hintSource.get("reviewStatus"));
        assertThrowsCode(1004, () -> service.tutorVizView(USER, origin, vizBody("SOLUTION")));
        service.tutorVizView(USER, origin, vizBody("MISCONCEPTION"));
        service.tutorVizView(USER, origin, vizBody("MISCONCEPTION"));
        assertEquals(1, count("SELECT COUNT(*) FROM question_understanding_viz_view WHERE attempt_id=? AND context='MISCONCEPTION'", origin));
        assertEquals("A", service.reveal(USER, origin).get("correctAnswer"));
        long variant = ((Number) service.variant(USER, origin).get("variantId")).longValue();
        JsonNode statsBeforeAided = json.valueToTree(reviewQueries.getReviewStats(USER));
        var aided = service.submitVariant(USER, variant, variantBody(origin, "D", null));
        assertEquals(statsBeforeAided, json.valueToTree(reviewQueries.getReviewStats(USER)));
        assertEquals("AIDED", aided.get("assistance"));
        assertEquals(false, ((Map<?, ?>) aided.get("review")).get("counted"));
        var originalDate = jdbc.queryForObject("SELECT next_review_date FROM question_review_schedule WHERE user_id=? AND question_id=?", java.time.LocalDate.class, USER, questionId);
        assertEquals(originalDate, ((Map<?, ?>) aided.get("review")).get("nextReviewDate"));
        jdbc.update("UPDATE question_review_schedule SET next_review_date='2030-04-05' WHERE user_id=? AND question_id=?", USER, questionId);
        var retry = service.submitVariant(USER, variant, variantBody(origin, "D", null));
        assertEquals(java.time.LocalDate.of(2030, 4, 5), ((Map<?, ?>) retry.get("review")).get("nextReviewDate"));
        jdbc.update("UPDATE question_review_schedule SET deleted=1 WHERE user_id=? AND question_id=?", USER, questionId);
        var withoutCard = service.submitVariant(USER, variant, variantBody(origin, "D", null));
        assertNull(((Map<?, ?>) withoutCard.get("review")).get("nextReviewDate"));
        assertEquals(false, ((Map<?, ?>) withoutCard.get("review")).get("counted"));
        assertEquals(1, count("SELECT COUNT(*) FROM question_understanding_variant_attempt WHERE origin_attempt_id=? AND assistance='AIDED'", origin));
        assertEquals(1, count("SELECT COUNT(*) FROM fsrs_review_event WHERE user_id=? AND question_id=?", USER, questionId));
    }

    @Test void pendingHintsKeepLegacyRevealAndAreNotPublic() {
        long origin = ((Number) service.submit(USER, questionId, body("B")).get("attemptId")).longValue();
        assertThrowsCode(1004, () -> service.tutorHint(USER, origin, hintBody("00000000-0000-4000-8000-000000000005")));
        assertFalse(service.tutorNext(USER, origin).toString().contains("hint_text"));
        jdbc.update("UPDATE question_understanding_hint SET review_status='REVIEWED',reviewed_by='test-reviewer',reviewed_time=CURRENT_TIMESTAMP WHERE content_id IN (SELECT id FROM question_understanding_content WHERE question_id=?)", questionId);
        assertThrowsCode(1004, () -> service.tutorHint(USER, origin, hintBody("00000000-0000-4000-8000-000000000006")));
        assertEquals("A", service.reveal(USER, origin).get("correctAnswer"));
    }

    @Test void wrongQuestionShowsCurrentChosenMisconceptionAndFsrsDueDate() {
        service.submit(USER, questionId, body("B"));
        var page = wrongQuestions.getWrongQuestions(USER, 1, 10, null, null, null, null);
        var row = page.getRecords().stream().filter(item -> item.getQuestionId() == questionId).findFirst().orElseThrow();
        assertNotNull(row.getNextReviewDate());
        assertEquals("以为能越过 2 直接弹出 1", row.getMisconception().get("title"));
        jdbc.update("UPDATE question_review_schedule SET next_review_date=CURRENT_DATE WHERE user_id=? AND question_id=?",
                USER, questionId);
        var overview = overviews.getOverview(USER, courseId);
        assertTrue(overview.getDueReviewCount() > 0);
        assertTrue(overview.getRecommendedTargets().stream().anyMatch(target -> "DUE_REVIEW".equals(target.getType())
                && questionId == target.getQuestionId()));
        jdbc.update("UPDATE question SET content=CONCAT(content,'已改') WHERE id=?", questionId);
        try {
            var changed = wrongQuestions.getWrongQuestions(USER, 1, 10, null, null, null, null);
            assertNull(changed.getRecords().getFirst().getMisconception());
        } finally {
            jdbc.update("UPDATE question SET content=LEFT(content,CHAR_LENGTH(content)-2) WHERE id=?", questionId);
        }
    }

    private JsonNode body(String answer) { return json.createObjectNode().put("answer", answer); }
    private JsonNode hintBody(String requestId) { return json.createObjectNode().put("requestId", requestId); }
    private JsonNode vizBody(String context) { return json.createObjectNode().put("context", context); }
    private JsonNode variantBody(long origin, String answer, String assistance) {
        var node = json.createObjectNode().put("originAttemptId", origin).put("answer", answer);
        if (assistance != null) node.put("assistance", assistance);
        return node;
    }
    private int count(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }
    private void assertThrowsCode(int code, Runnable action) {
        BusinessException exception = assertThrows(BusinessException.class, action::run);
        assertEquals(code, exception.getCode());
    }
}
