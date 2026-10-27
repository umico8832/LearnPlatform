package com.learnplatform.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.mysql.MySQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("integration")
class LearnerListIndexMigrationIntegrationTest {
    @Test
    void emptySchemaBuildsAllLearnerListIndexes() {
        try (var mysql = database()) {
            mysql.start();
            migrate(mysql, "123");
            assertEquals(3, indexCount(jdbc(mysql)));
        }
    }

    @Test
    void upgradeKeepsExistingFavoritesAndSubmissionsIntact() {
        try (var mysql = database()) {
            mysql.start();
            migrate(mysql, "122");
            JdbcTemplate db = jdbc(mysql);
            db.update("INSERT INTO user_favorite_question(user_id,question_id) VALUES (900123,900123)");
            db.update("INSERT INTO question_submission(user_id,content,question_type,course_id,status) "
                    + "VALUES (900123,'迁移保留样本','SINGLE_CHOICE',900123,0)");
            var favorite = db.queryForMap("SELECT * FROM user_favorite_question WHERE user_id=900123");
            var submission = db.queryForMap("SELECT * FROM question_submission WHERE user_id=900123");
            assertEquals(0, indexCount(db));
            migrate(mysql, "123");
            assertEquals(3, indexCount(db));
            assertEquals(favorite, db.queryForMap("SELECT * FROM user_favorite_question WHERE user_id=900123"));
            assertEquals(submission, db.queryForMap("SELECT * FROM question_submission WHERE user_id=900123"));
        }
    }

    private int indexCount(JdbcTemplate db) {
        return db.queryForObject("""
                SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
                WHERE table_schema=DATABASE() AND index_name IN
                ('idx_favorite_user_created','idx_submission_user_created','idx_submission_user_filters')
                """, Integer.class);
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
