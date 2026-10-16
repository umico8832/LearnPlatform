package com.learnplatform.service.understanding;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
class QuestionContentCorrectionMigrationIntegrationTest {
    @Test void migratesEmptyDatabaseWithPendingCorrectedSeeds() { migrateAndCheck(false); }
    @Test void upgradesV109WithoutChangingOtherContentOrLearningFacts() { migrateAndCheck(true); }

    private void migrateAndCheck(boolean upgrade) {
        try (var database = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("learn_platform").withUsername("test").withPassword("test")
                .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci")) {
            database.start();
            var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                    database.getJdbcUrl(), "root", database.getPassword()));
            if (upgrade) {
                Flyway.configure().dataSource(database.getJdbcUrl(), "root", database.getPassword())
                        .target("109").load().migrate();
                insertUnrelatedContentAndLearningFacts(jdbc);
            }
            Flyway.configure().dataSource(database.getJdbcUrl(), "root", database.getPassword()).load().migrate();
            assertEquals(2, count(jdbc, """
                    SELECT COUNT(*) FROM question_understanding_content
                    WHERE content_version=1 AND review_status='PENDING' AND solution_content_source='AI_GENERATED'
                      AND reviewed_by IS NULL AND reviewed_time IS NULL
                      AND source_verification='PENDING' AND source_verified_by IS NULL AND source_verified_time IS NULL
                    """));
            assertEquals(6, count(jdbc, "SELECT COUNT(*) FROM question_understanding_misconception WHERE content_source='AI_GENERATED'"));
            assertEquals(6, count(jdbc, """
                    SELECT COUNT(*) FROM question_understanding_hint
                    WHERE review_status='PENDING' AND content_source='AI_GENERATED'
                      AND reviewed_by IS NULL AND reviewed_time IS NULL
                    """));
            assertEquals(2, count(jdbc, """
                    SELECT COUNT(*) FROM ai_variant_question WHERE changed_condition IS NOT NULL
                      AND review_status='PENDING' AND review_note IS NULL AND reviewed_by IS NULL AND reviewed_time IS NULL
                    """));
            String hint = jdbc.queryForObject("""
                    SELECT h.hint_text FROM question_understanding_hint h
                    JOIN question_understanding_content c ON c.id=h.content_id
                    WHERE c.source_title='系统种子生成：合法出栈序列' AND h.hint_level=3
                    """, String.class);
            assertTrue(hint.contains("甲、乙、丙"));
            assertFalse(hint.contains("弹出 3"));
            String changed = jdbc.queryForObject("""
                    SELECT v.changed_condition FROM ai_variant_question v
                    JOIN question_ai_asset a ON a.id=v.asset_id
                    JOIN question_understanding_content c ON c.question_id=a.question_id
                    WHERE c.source_title='2026 年 408 真题 第 3 题'
                    """, String.class);
            assertTrue(changed.contains("仍求后序遍历"));
            assertFalse(changed.contains("所求的后序都不变"));
            assertEquals(110, count(jdbc, "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history"));
            if (upgrade) {
                assertEquals(1, count(jdbc, """
                        SELECT COUNT(*) FROM question_understanding_content WHERE source_title='unrelated-test-content'
                          AND content_version=2 AND review_status='REVIEWED' AND reviewed_by='test-reviewer'
                          AND solution_explanation='unrelated solution' AND solution_content_source='CURATED'
                          AND source_verification='VERIFIED' AND source_verified_by='test-reviewer'
                        """));
                assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM question_understanding_attempt WHERE user_id=991100 AND solution_revealed_time IS NOT NULL"));
                assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM question_understanding_hint_receipt WHERE request_id='00000000-0000-4000-8000-000000991100'"));
                assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM question_understanding_viz_view WHERE context='MISCONCEPTION'"));
                assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM question_understanding_variant_attempt WHERE user_id=991100 AND assistance='UNAIDED' AND is_correct=1"));
            }
        }
    }

    private void insertUnrelatedContentAndLearningFacts(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO question_understanding_content
                (question_id,content_version,review_status,reviewed_by,reviewed_time,question_fingerprint,
                 knowledge_point_id,solution_explanation,solution_content_source,source_title,
                 source_verification,source_verified_by,source_verified_time)
                SELECT question_id,2,'REVIEWED','test-reviewer',CURRENT_TIMESTAMP,question_fingerprint,
                  knowledge_point_id,'unrelated solution','CURATED','unrelated-test-content',
                  'VERIFIED','test-reviewer',CURRENT_TIMESTAMP
                FROM question_understanding_content WHERE source_title='系统种子生成：合法出栈序列'
                """);
        jdbc.update("""
                INSERT INTO question_understanding_attempt
                (user_id,question_id,content_id,variant_id,practice_record_id,solution_revealed_time)
                SELECT 991100,c.question_id,c.id,v.id,991100,CURRENT_TIMESTAMP
                FROM question_understanding_content c
                JOIN question_ai_asset a ON a.question_id=c.question_id
                JOIN ai_variant_question v ON v.asset_id=a.id
                WHERE c.source_title='系统种子生成：合法出栈序列'
                """);
        long attempt = jdbc.queryForObject("SELECT id FROM question_understanding_attempt WHERE user_id=991100", Long.class);
        jdbc.update("""
                INSERT INTO question_understanding_hint_receipt(attempt_id,hint_id,hint_level,request_id)
                SELECT ?,h.id,1,'00000000-0000-4000-8000-000000991100'
                FROM question_understanding_hint h JOIN question_understanding_attempt a ON a.content_id=h.content_id
                WHERE a.id=? AND h.hint_level=1
                """, attempt, attempt);
        jdbc.update("INSERT INTO question_understanding_viz_view(attempt_id,context) VALUES (?,'MISCONCEPTION')", attempt);
        jdbc.update("""
                INSERT INTO question_understanding_variant_attempt
                (origin_attempt_id,user_id,variant_id,user_answer,is_correct,assistance)
                SELECT id,user_id,variant_id,'D',1,'UNAIDED' FROM question_understanding_attempt WHERE id=?
                """, attempt);
    }

    private int count(JdbcTemplate jdbc, String sql) { return jdbc.queryForObject(sql, Integer.class); }
}
