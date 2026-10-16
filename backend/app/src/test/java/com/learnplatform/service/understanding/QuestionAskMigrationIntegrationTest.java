package com.learnplatform.service.understanding;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("integration")
class QuestionAskMigrationIntegrationTest {
    @Test void upgradesPreviousMainAndProtectsUniqueRequests() {
        try (var mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("learn_platform")
                .withUsername("test").withPassword("test")
                .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci")) {
            mysql.start();
            Flyway.configure().dataSource(mysql.getJdbcUrl(), "root", mysql.getPassword()).target("113").load().migrate();
            var db = new JdbcTemplate(new DriverManagerDataSource(mysql.getJdbcUrl(), "root", mysql.getPassword()));
            int existing = db.queryForObject("SELECT COUNT(*) FROM question_understanding_content", Integer.class);
            Flyway.configure().dataSource(mysql.getJdbcUrl(), "root", mysql.getPassword()).load().migrate();
            assertEquals(existing, db.queryForObject("SELECT COUNT(*) FROM question_understanding_content", Integer.class));
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM question_ask_record", Integer.class));
            String insert = "INSERT INTO question_ask_record(user_id,question_id,attempt_id,request_id,message,expires_at) VALUES(1,1,1,'00000000-0000-4000-8000-000000000001','测试',CURRENT_TIMESTAMP)";
            db.update(insert);
            assertThrows(DuplicateKeyException.class, () -> db.update(insert));
        }
    }
}
