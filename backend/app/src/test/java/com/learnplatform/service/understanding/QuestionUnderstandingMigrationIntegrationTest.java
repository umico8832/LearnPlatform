package com.learnplatform.service.understanding;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.mysql.MySQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("integration")
class QuestionUnderstandingMigrationIntegrationTest {
    @Test void migratesEmptyDatabaseAndReadsSeeds() { migrateAndCheck(null); }
    @Test void upgradesFromPreviousVersionAndReadsSeeds() { migrateAndCheck("107"); }

    private void migrateAndCheck(String initialVersion) {
        try (var database = new MySQLContainer("mysql:8.0")
                .withDatabaseName("learn_platform").withUsername("test").withPassword("test")
                .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci")) {
            database.start();
            var configuration = Flyway.configure().dataSource(database.getJdbcUrl(), "root", database.getPassword());
            if (initialVersion != null) configuration.target(initialVersion).load().migrate();
            Flyway.configure().dataSource(database.getJdbcUrl(), "root", database.getPassword())
                    .target("108").load().migrate();
            var jdbc = new JdbcTemplate(new DriverManagerDataSource(database.getJdbcUrl(), "root", database.getPassword()));
            assertEquals(108, jdbc.queryForObject("SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history", Integer.class));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM question_understanding_content WHERE review_status='REVIEWED'", Integer.class));
            assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM question_understanding_misconception", Integer.class));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM ai_variant_question WHERE changed_condition IS NOT NULL", Integer.class));
            assertNotNull(jdbc.queryForObject("SELECT JSON_EXTRACT(solution_viz_spec_json,'$.scene') FROM question_understanding_content LIMIT 1", String.class));
            assertEquals(1, jdbc.queryForObject("""
                    SELECT COUNT(*) FROM question_understanding_content
                    WHERE source_verification='VERIFIED' AND source_exam_question_id IS NOT NULL
                      AND source_verified_by='codex-check, claude-review' AND source_verified_time IS NOT NULL
                      AND source_title='2026 年 408 真题 第 3 题'
                    """, Integer.class));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM ai_variant_question WHERE review_note='Claude 内容复核通过'", Integer.class));
            assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM question_understanding_hint WHERE review_status='PENDING' AND content_source='AI_GENERATED'", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM question_understanding_hint WHERE review_status='REVIEWED'", Integer.class));
        }
    }
}
