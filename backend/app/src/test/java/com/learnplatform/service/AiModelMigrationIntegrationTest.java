package com.learnplatform.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
@Testcontainers
class AiModelMigrationIntegrationTest {
    @Container private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("learn_platform").withUsername("test").withPassword("test")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    @Test void upgradesExistingAuditWithoutInventingHistoricalMetadata() {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword()).target("92").load().migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword()));
        jdbc.update("INSERT INTO ai_call_log (user_id, function_type, status, tokens_used) VALUES (7, 'legacy', 1, 20)");
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword()).target("93").load().migrate();
        var row = jdbc.queryForMap("SELECT * FROM ai_call_log WHERE function_type = 'legacy'");
        assertEquals(20, row.get("tokens_used"));
        assertNull(row.get("call_id"));
        assertNull(row.get("outcome"));
        assertNull(row.get("requested_model"));
        assertEquals("CHAT", row.get("call_kind"));
    }
}
