package com.learnplatform.service.understanding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.IntegrationTestBase;
import com.learnplatform.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
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
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    private long questionId;
    private long courseId;

    @BeforeEach void setUp() {
        questionId = jdbc.queryForObject("SELECT question_id FROM question_understanding_content WHERE source_title='系统种子生成：合法出栈序列'", Long.class);
        courseId = jdbc.queryForObject("SELECT course_id FROM question WHERE id=?", Long.class, questionId);
        jdbc.update("INSERT INTO user (id,username,password,role,status,deleted) VALUES (?,?,'test','USER',1,0), (?,?,'test','USER',1,0)",
                USER, "understanding-owner", OTHER, "understanding-other");
        jdbc.update("INSERT INTO user_course(user_id,course_id) VALUES(?,?)", USER, courseId);
    }

    @AfterEach void tearDown() {
        jdbc.update("DELETE FROM course_learning_event WHERE user_id IN (?,?)", USER, OTHER);
        jdbc.update("DELETE FROM question_understanding_variant_attempt WHERE user_id IN (?,?)", USER, OTHER);
        jdbc.update("DELETE FROM question_understanding_attempt WHERE user_id IN (?,?)", USER, OTHER);
        jdbc.update("DELETE FROM practice_record WHERE user_id IN (?,?)", USER, OTHER);
        jdbc.update("DELETE FROM wrong_question WHERE user_id IN (?,?)", USER, OTHER);
        jdbc.update("DELETE FROM question_review_schedule WHERE user_id IN (?,?)", USER, OTHER);
        jdbc.update("DELETE FROM user_course WHERE user_id IN (?,?)", USER, OTHER);
        jdbc.update("DELETE FROM user WHERE id IN (?,?)", USER, OTHER);
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
                WHERE source_exam_question_id IS NOT NULL AND source_verification='VERIFIED'
                """, Long.class);
        Map<String, Object> view = service.question(USER, treeId);
        Map<?, ?> source = (Map<?, ?>) view.get("source");
        assertEquals("OFFICIAL_EXAM", source.get("kind"));
        assertEquals("VERIFIED", source.get("verification"));
        assertEquals("2026 年 408 真题 第 3 题", source.get("title"));
        assertEquals("树和二叉树", ((Map<?, ?>) view.get("position")).get("chapterName"));
        jdbc.update("UPDATE question_understanding_content SET source_verified_by=NULL WHERE question_id=?", treeId);
        try {
            Map<?, ?> unreviewed = (Map<?, ?>) service.question(USER, treeId).get("source");
            assertEquals("MANUAL", unreviewed.get("kind"));
        } finally {
            jdbc.update("UPDATE question_understanding_content SET source_verified_by='codex-check, claude-review' WHERE question_id=?", treeId);
        }
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
        assertEquals(true, result.get("correct"));
        assertEquals("D", result.get("correctAnswer"));
        assertEquals(1, count("SELECT COUNT(*) FROM question_understanding_variant_attempt WHERE origin_attempt_id=? AND assistance='UNAIDED'", origin));
        assertEquals(1, count("SELECT COUNT(*) FROM course_learning_event WHERE user_id=? AND event_source='UNDERSTANDING_VARIANT'", USER));
        assertThrowsCode(1005, () -> service.submitVariant(USER, variant,
                variantBody(origin, "A", null)));
        assertThrowsCode(1001, () -> service.submitVariant(USER, variant,
                variantBody(origin, "D", "HINTED")));
    }

    private JsonNode body(String answer) { return json.createObjectNode().put("answer", answer); }
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
