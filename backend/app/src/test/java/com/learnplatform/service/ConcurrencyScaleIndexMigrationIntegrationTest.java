package com.learnplatform.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.mysql.MySQLContainer;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
class ConcurrencyScaleIndexMigrationIntegrationTest {
    @Test void emptySchemaHasBothScaleIndexes() {
        try (var mysql = database()) {
            mysql.start();
            migrate(mysql, "122");
            var db = jdbc(mysql);
            assertEquals(2, db.queryForObject("""
                    SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
                    WHERE table_schema=DATABASE() AND index_name IN
                    ('idx_practice_user_question_correct','idx_question_course_listing')
                    """, Integer.class));
        }
    }

    @Test void upgradePreservesFactsAndUsesCoveringAggregationAndOrderedCourseRange() {
        try (var mysql = database()) {
            mysql.start();
            migrate(mysql, "121");
            var db = jdbc(mysql);
            var questions = new ArrayList<Object[]>();
            var answers = new ArrayList<Object[]>();
            for (int i = 0; i < 2000; i++) {
                questions.add(new Object[] {900122000L + i, "Scale fixture " + i, i % 2});
                answers.add(new Object[] {900122L, 900122000L + i, i % 2});
            }
            db.batchUpdate("""
                    INSERT INTO question(id,content,question_type,course_id,visibility,status,deleted)
                    VALUES(?,?,'SINGLE_CHOICE',900122,'PUBLIC',1,?)
                    """, questions);
            db.batchUpdate("""
                    INSERT INTO practice_record(user_id,question_id,user_answer,is_correct)
                    VALUES(?,?,'A',?)
                    """, answers);
            var before = db.queryForMap("SELECT * FROM question WHERE id=900122000");
            migrate(mysql, "122");
            assertEquals(before, db.queryForMap("SELECT * FROM question WHERE id=900122000"));
            assertEquals(2000, db.queryForObject("SELECT COUNT(*) FROM practice_record WHERE user_id=900122", Integer.class));
            db.execute("ANALYZE TABLE practice_record,question");
            String aggregation = db.queryForObject("""
                    EXPLAIN FORMAT=JSON SELECT COUNT(*),COALESCE(SUM(is_correct=1),0)
                    FROM practice_record WHERE user_id=900122
                    AND question_id NOT IN(SELECT id FROM question WHERE visibility='DRAFT')
                    """, String.class);
            assertTrue(aggregation.contains("idx_practice_user_question_correct"), aggregation);
            assertTrue(aggregation.contains("using_index"), aggregation);
            String listing = db.queryForObject("""
                    EXPLAIN FORMAT=JSON SELECT id FROM question WHERE course_id=900122
                    AND deleted=0 AND status=1 AND visibility='PUBLIC'
                    ORDER BY create_time DESC,id DESC LIMIT 20
                    """, String.class);
            assertTrue(listing.contains("idx_question_course_listing"), listing);
            assertFalse(tools.jackson.databind.json.JsonMapper.builder().build().readTree(listing)
                    .path("query_block").path("ordering_operation").path("using_filesort").asBoolean(), listing);
        }
    }

    private MySQLContainer database() {
        return new MySQLContainer("mysql:8.0").withDatabaseName("learn_platform")
                .withUsername("test").withPassword("test");
    }

    private JdbcTemplate jdbc(MySQLContainer mysql) {
        return new JdbcTemplate(new DriverManagerDataSource(mysql.getJdbcUrl(), "root", mysql.getPassword()));
    }

    private void migrate(MySQLContainer mysql, String target) {
        Flyway.configure().dataSource(mysql.getJdbcUrl(), "root", mysql.getPassword()).target(target).load().migrate();
    }
}
