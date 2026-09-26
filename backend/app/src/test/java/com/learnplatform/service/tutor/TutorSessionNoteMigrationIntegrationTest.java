package com.learnplatform.service.tutor;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("integration")
class TutorSessionNoteMigrationIntegrationTest {
    @Test void upgradesV103WithoutChangingMemoryAndEnforcesOneVersionedNotePerSession() {
        try (var database = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("learn_platform").withUsername("test").withPassword("test")
                .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci")) {
            database.start();
            Flyway.configure().dataSource(database.getJdbcUrl(), "root", database.getPassword())
                    .target("103").load().migrate();
            var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                    database.getJdbcUrl(), "root", database.getPassword()));
            jdbc.update("INSERT INTO tutor_course_memory (user_id,course_id,revision,goal) VALUES (1,2,1,'既有记忆')");

            Flyway.configure().dataSource(database.getJdbcUrl(), "root", database.getPassword()).load().migrate();
            assertEquals("既有记忆", jdbc.queryForObject(
                    "SELECT goal FROM tutor_course_memory WHERE user_id=1 AND course_id=2", String.class));
            jdbc.update("INSERT INTO tutor_session_note (session_id,revision,note) VALUES (11,1,'复盘')");
            assertThrows(DataAccessException.class, () -> jdbc.update(
                    "INSERT INTO tutor_session_note (session_id,revision,note) VALUES (11,2,'重复')"));
            assertThrows(DataAccessException.class, () -> jdbc.update(
                    "INSERT INTO tutor_session_note (session_id,revision,note) VALUES (12,0,'非法版本')"));
            assertThrows(DataAccessException.class, () -> jdbc.update(
                    "INSERT INTO tutor_session_note (session_id,revision,note) VALUES (13,1,REPEAT('长',501))"));
        }
    }
}
