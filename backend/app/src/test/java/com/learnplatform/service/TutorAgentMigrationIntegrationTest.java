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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("integration")
@Testcontainers
class TutorAgentMigrationIntegrationTest {
    @Container private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("learn_platform").withUsername("test").withPassword("test")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");
    @Container private static final MySQLContainer<?> V101_MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("learn_platform").withUsername("test").withPassword("test")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    @Test void upgradesFromThePreviousSchemaAndProtectsMessageOrder() {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword()).target("99").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), "root", MYSQL.getPassword()));
        assertEquals(1, tableCount(jdbc, "tutor_agent_run"));
        jdbc.update("""
                INSERT INTO tutor_agent_run
                (id, run_key, tutor_session_id, user_id, status, next_sequence)
                VALUES (2, '69af726c-2a51-443f-a475-bab94530748f', 30, 7, 'RUNNING', 1)
                """);

        jdbc.update("""
                INSERT INTO tutor_agent_message (run_id, sequence_no, role, content)
                VALUES (2, 1, 'ASSISTANT', '已有回答')
                """);

        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword()).load().migrate();
        assertEquals(1, tableCount(jdbc, "tutor_agent_run"));
        assertEquals(1, tableCount(jdbc, "tutor_agent_message"));
        assertNull(jdbc.queryForObject("SELECT actions_json FROM tutor_agent_message WHERE run_id=2", String.class));
        assertEquals("已有回答", jdbc.queryForObject("SELECT content FROM tutor_agent_message WHERE run_id=2", String.class));
        assertNull(jdbc.queryForObject("SELECT execution_key FROM tutor_agent_run WHERE id=2", String.class));
        assertNull(jdbc.queryForObject("SELECT lease_until FROM tutor_agent_run WHERE id=2", java.sql.Timestamp.class));
        assertEquals("RUNNING", jdbc.queryForObject("SELECT status FROM tutor_agent_run WHERE id=2", String.class));

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

    @Test void upgradesV100ToV101AndEnforcesAttemptUniqueness() {
        Flyway.configure().dataSource(V101_MYSQL.getJdbcUrl(), "root", V101_MYSQL.getPassword())
                .target("100").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                V101_MYSQL.getJdbcUrl(), "root", V101_MYSQL.getPassword()));
        jdbc.update("""
                INSERT INTO tutor_agent_run (id,run_key,tutor_session_id,user_id,status,next_sequence)
                VALUES (1,'69af726c-2a51-443f-a475-bab94530748a',30,7,'WAITING_USER',3)
                """);
        jdbc.update("""
                INSERT INTO tutor_agent_message (id,run_id,sequence_no,role,content,actions_json)
                VALUES (1,1,1,'ASSISTANT','已存动作','[{"type":"CHECK"}]'),
                       (2,1,2,'ASSISTANT','旧消息',NULL)
                """);

        Flyway.configure().dataSource(V101_MYSQL.getJdbcUrl(), "root", V101_MYSQL.getPassword()).load().migrate();
        assertEquals(1, tableCount(jdbc, "tutor_agent_practice_attempt"));
        assertEquals(1, jdbc.queryForObject("""
                SELECT JSON_CONTAINS(actions_json, JSON_OBJECT('type', 'CHECK'))
                FROM tutor_agent_message WHERE id=1
                """, Integer.class));
        assertNull(jdbc.queryForObject("SELECT actions_json FROM tutor_agent_message WHERE id=2", String.class));
        jdbc.update("""
                INSERT INTO tutor_agent_practice_attempt (message_id, question_id, practice_record_id, question_json, result_json)
                VALUES (1, 9, 10, '{"id":9,"content":"题干","questionType":"SINGLE_CHOICE","options":[]}', '{"recordId":10}')
                """);
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO tutor_agent_practice_attempt (message_id, question_id, practice_record_id, question_json, result_json)
                VALUES (1, 9, 11, '{"id":9,"content":"题干","questionType":"SINGLE_CHOICE","options":[]}', '{"recordId":11}')
                """));
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO tutor_agent_practice_attempt (message_id, question_id, practice_record_id, question_json, result_json)
                VALUES (2, 9, 10, '{"id":9,"content":"题干","questionType":"SINGLE_CHOICE","options":[]}', '{"recordId":10}')
                """));
    }

    private int tableCount(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema=DATABASE() AND table_name=?", Integer.class, table);
    }
}
