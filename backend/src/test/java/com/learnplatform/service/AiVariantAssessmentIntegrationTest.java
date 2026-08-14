package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.dto.AiVariantReviewRequest;
import com.learnplatform.dto.AiVariantReviewVO;
import com.learnplatform.dto.CourseStageAssessmentCreateRequest;
import com.learnplatform.dto.CourseStageAssessmentVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
class AiVariantAssessmentIntegrationTest extends IntegrationTestBase {
    private static final String USERNAME = "ai-variant-assessment-test";

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AiVariantReviewService reviewService;
    @Autowired private CourseStageAssessmentService assessmentService;

    private Long assetId;
    private Long variantId;
    private Long publishedQuestionId;

    @AfterEach
    void cleanUp() {
        Long userId = jdbcTemplate.query("SELECT id FROM user WHERE username = ?",
                result -> result.next() ? result.getLong(1) : null, USERNAME);
        if (userId != null) {
            jdbcTemplate.update("DELETE FROM course_learning_event WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM question_review_schedule WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM wrong_question WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE q FROM course_stage_assessment_question q "
                    + "JOIN course_stage_assessment a ON a.id = q.assessment_id WHERE a.user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM course_stage_assessment WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM user_course WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM user WHERE id = ?", userId);
        }
        if (publishedQuestionId != null) {
            jdbcTemplate.update("DELETE FROM question_version WHERE question_id = ?", publishedQuestionId);
            jdbcTemplate.update("DELETE FROM question_knowledge_point WHERE question_id = ?", publishedQuestionId);
            jdbcTemplate.update("DELETE FROM question_option WHERE question_id = ?", publishedQuestionId);
            jdbcTemplate.update("DELETE FROM question WHERE id = ?", publishedQuestionId);
        }
        if (variantId != null) jdbcTemplate.update("DELETE FROM ai_variant_question WHERE id = ?", variantId);
        if (assetId != null) jdbcTemplate.update("DELETE FROM question_ai_asset WHERE id = ?", assetId);
    }

    @Test
    void onlyApprovedVariantBecomesTraceableAssessmentCandidate() {
        Long motherQuestionId = jdbcTemplate.queryForObject("""
                SELECT q.id FROM question q
                LEFT JOIN question_ai_asset a ON a.question_id = q.id AND a.asset_type = 'VARIANT' AND a.deleted = 0
                WHERE q.course_id = 1 AND q.status = 1 AND q.deleted = 0 AND q.visibility = 'PUBLIC'
                  AND q.question_type = 'SINGLE_CHOICE' AND a.id IS NULL
                ORDER BY q.id LIMIT 1
                """, Long.class);
        jdbcTemplate.update("INSERT INTO question_ai_asset "
                + "(question_id,asset_type,content,model,deleted) VALUES (?,'VARIANT','结构化变式题','test-model',0)",
                motherQuestionId);
        assetId = jdbcTemplate.queryForObject("SELECT id FROM question_ai_asset WHERE question_id = ? "
                + "AND asset_type = 'VARIANT'", Long.class, motherQuestionId);
        jdbcTemplate.update("INSERT INTO ai_variant_question "
                        + "(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty) "
                        + "VALUES (?,'SINGLE_CHOICE','AI审查生成：栈的访问规则？',"
                        + "'[{\"label\":\"A\",\"content\":\"后进先出\"},{\"label\":\"B\",\"content\":\"先进先出\"}]',"
                        + "'A','栈遵循后进先出。',2)", assetId);
        variantId = jdbcTemplate.queryForObject("SELECT id FROM ai_variant_question WHERE asset_id = ?",
                Long.class, assetId);
        Long adminId = jdbcTemplate.queryForObject("SELECT id FROM user WHERE role = 'ADMIN' ORDER BY id LIMIT 1",
                Long.class);
        AiVariantReviewRequest review = new AiVariantReviewRequest();
        review.setDecision("APPROVE");
        review.setReviewNote("集成测试核验通过");

        AiVariantReviewVO approved = reviewService.review(variantId, review, adminId);
        publishedQuestionId = approved.getPublishedQuestionId();

        assertEquals("AI_GENERATED", jdbcTemplate.queryForObject(
                "SELECT source_type FROM question WHERE id = ?", String.class, publishedQuestionId));
        assertEquals(motherQuestionId, jdbcTemplate.queryForObject(
                "SELECT origin_question_id FROM question WHERE id = ?", Long.class, publishedQuestionId));
        assertEquals("AI_GENERATED", jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(snapshot_after, '$.sourceType')) "
                        + "FROM question_version WHERE question_id = ? AND change_type = 'CREATE'",
                String.class, publishedQuestionId));
        assertEquals(motherQuestionId, jdbcTemplate.queryForObject(
                "SELECT CAST(JSON_UNQUOTE(JSON_EXTRACT(snapshot_after, '$.originQuestionId')) AS UNSIGNED) "
                        + "FROM question_version WHERE question_id = ? AND change_type = 'CREATE'",
                Long.class, publishedQuestionId));
        jdbcTemplate.update("INSERT INTO user (username,password,role,status,deleted) VALUES (?,'test','USER',1,0)",
                USERNAME);
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user WHERE username = ?", Long.class, USERNAME);
        jdbcTemplate.update("INSERT INTO user_course (user_id,course_id) VALUES (?,1)", userId);
        CourseStageAssessmentCreateRequest create = new CourseStageAssessmentCreateRequest();
        create.setQuestionCount(20);

        CourseStageAssessmentVO assessment = assessmentService.start(userId, 1L, create);

        assertTrue(assessment.getQuestions().stream().anyMatch(question ->
                publishedQuestionId.equals(question.getQuestionId())
                        && "AI_GENERATED".equals(question.getSourceType())
                        && motherQuestionId.equals(question.getOriginQuestionId())));
    }
}
