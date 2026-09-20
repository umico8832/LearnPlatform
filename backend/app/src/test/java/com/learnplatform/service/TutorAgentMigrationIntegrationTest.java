package com.learnplatform.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("integration")
@Testcontainers
class TutorAgentMigrationIntegrationTest {
    @Container private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("learn_platform").withUsername("test").withPassword("test")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    @Test void upgradesFromThePreviousSchemaAndProtectsMessageOrder() {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword()).target("94").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), "root", MYSQL.getPassword()));
        assertEquals(0, tableCount(jdbc, "tutor_agent_run"));

        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword()).load().migrate();
        assertEquals(1, tableCount(jdbc, "tutor_agent_run"));
        assertEquals(1, tableCount(jdbc, "tutor_agent_message"));

        jdbc.update("""
                INSERT INTO tutor_agent_run
                (id, run_key, tutor_session_id, user_id, status, next_sequence)
                VALUES (1, '69af726c-2a51-443f-a475-bab94530748e', 30, 7, 'WAITING_USER', 3)
                """);
        jdbc.update("""
                INSERT INTO tutor_agent_message (run_id, sequence_no, role, content)
                VALUES (1, 1, 'USER', '问题')
                """);
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO tutor_agent_message (run_id, sequence_no, role, content)
                VALUES (1, 1, 'ASSISTANT', '重复序号')
                """));
    }

    private int tableCount(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema=DATABASE() AND table_name=?", Integer.class, table);
    }
}
