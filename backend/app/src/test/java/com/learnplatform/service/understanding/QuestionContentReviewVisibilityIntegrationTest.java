package com.learnplatform.service.understanding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.IntegrationTestBase;
import com.learnplatform.service.QuestionService;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class QuestionContentReviewVisibilityIntegrationTest extends IntegrationTestBase {
    private static final long LEARNER = 991301L;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired QuestionUnderstandingService understanding;
    @Autowired QuestionService questions;
    @Autowired QuestionUnderstandingPreviewService preview;
    @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;

    @BeforeEach void createLearner() {
        db.update("INSERT INTO user(id,username,password,role,status,deleted) VALUES (?,'review-marks-learner','test','USER',1,0)", LEARNER);
    }

    @Test void learnersCanOpenEveryReviewedQuestionAndUseItsReviewedTeachingContent() {
        var rows = db.queryForList("SELECT c.question_id,q.visibility,c.review_status FROM question_understanding_content c JOIN question q ON q.id=c.question_id WHERE c.content_version=1");
        assertEquals(27, rows.size());
        for (var row : rows) {
            long id = ((Number) row.get("question_id")).longValue();
            assertEquals("REVIEWED", row.get("review_status"));
            assertEquals("PUBLIC", row.get("visibility"));
            assertEquals(id, understanding.question(LEARNER, id).get("questionId"));
            assertEquals(true, json.valueToTree(questions.getEnabledQuestionById(id, LEARNER)).path("hasUnderstanding").asBoolean());
            String correct = db.queryForObject("SELECT option_label FROM question_option WHERE question_id=? AND is_correct=1 AND deleted=0", String.class, id);
            String wrong = "A".equals(correct) ? "B" : "A";
            var result = understanding.submit(LEARNER, id, json.createObjectNode().put("answer", wrong));
            assertEquals("REVIEWED", json.valueToTree(result).path("misconception").path("source").path("reviewStatus").asText());
            long attempt = ((Number) result.get("attemptId")).longValue();
            for (int level = 1; level <= 3; level++) {
                var hint = understanding.tutorHint(LEARNER, attempt, json.createObjectNode().put("requestId", java.util.UUID.randomUUID().toString()));
                assertEquals("REVIEWED", json.valueToTree(hint).path("source").path("reviewStatus").asText());
            }
            var solution = understanding.reveal(LEARNER, attempt);
            assertEquals(correct, solution.get("correctAnswer"));
            assertEquals("AI_GENERATED", json.valueToTree(solution).path("explanationSource").path("kind").asText());
            assertNotNull(understanding.variant(LEARNER, attempt).get("variantId"));
        }
        long tree = db.queryForObject("SELECT question_id FROM question_understanding_content WHERE source_title='2026 年 408 真题 第 3 题'", Long.class);
        assertEquals("VERIFIED", json.valueToTree(understanding.question(LEARNER, tree)).path("source").path("verification").asText());
    }

    @Test void aPendingDraftStaysHiddenWhileAdminCanPreviewIt() {
        long id = db.queryForObject("SELECT id FROM question WHERE source_reference='m5-draft:ll-01'", Long.class);
        db.update("UPDATE question SET visibility='DRAFT' WHERE id=?", id);
        db.update("UPDATE question_understanding_content SET review_status='PENDING',reviewed_by=NULL,reviewed_time=NULL WHERE question_id=?", id);
        db.update("UPDATE question_understanding_hint SET review_status='PENDING' WHERE content_id IN (SELECT id FROM question_understanding_content WHERE question_id=?)", id);
        db.update("UPDATE ai_variant_question SET review_status='PENDING' WHERE asset_id IN (SELECT id FROM question_ai_asset WHERE question_id=?)", id);
        sqlSession.clearCache();
        assertThrows(com.learnplatform.common.exception.BusinessException.class, () -> understanding.question(LEARNER, id));
        assertFalse(questions.getEnabledQuestionPage(1, 50, null, null, null, id, null).getRecords().stream().anyMatch(q -> q.getId() == id));
        Map<String, Object> state = preview.preview(1L, "ADMIN", id, json.createObjectNode());
        assertEquals(true, state.get("preview"));
    }
}
