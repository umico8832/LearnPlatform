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
class TutorMemoryMigrationIntegrationTest {
    @Test void upgradesV102WithoutChangingExistingPlansAndEnforcesMemoryUniqueness() {
        try (var database = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("learn_platform").withUsername("test").withPassword("test")
                .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci")) {
            database.start();
            Flyway.configure().dataSource(database.getJdbcUrl(), "root", database.getPassword())
                    .target("102").load().migrate();
            var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                    database.getJdbcUrl(), "root", database.getPassword()));
            jdbc.update("INSERT INTO tutor_agent_plan_confirmation (message_id) VALUES (999)");
            Flyway.configure().dataSource(database.getJdbcUrl(), "root", database.getPassword()).load().migrate();
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM tutor_agent_plan_confirmation", Integer.class));
            jdbc.update("INSERT INTO tutor_course_memory (user_id,course_id,revision,goal) VALUES (1,2,1,'目标')");
            assertThrows(DataAccessException.class, () -> jdbc.update(
                    "INSERT INTO tutor_course_memory (user_id,course_id,revision) VALUES (1,2,1)"));
            assertThrows(DataAccessException.class, () -> jdbc.update(
                    "INSERT INTO tutor_course_memory (user_id,course_id,revision) VALUES (2,2,0)"));
            assertThrows(DataAccessException.class, () -> jdbc.update(
                    "UPDATE tutor_course_memory SET explanation_style='UNSUPPORTED' WHERE user_id=1"));
        }
    }
}
