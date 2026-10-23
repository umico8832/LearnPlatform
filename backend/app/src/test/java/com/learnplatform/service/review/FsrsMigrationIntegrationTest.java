package com.learnplatform.service.review;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.mysql.MySQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("integration")
class FsrsMigrationIntegrationTest {
    @Test void migratesEmptyDatabase() { migrateAndCheck(null); }
    @Test void upgradesV107WithoutFabricatingFsrsHistory() { migrateAndCheck("107"); }

    private void migrateAndCheck(String initialVersion) {
        try (var database = new MySQLContainer("mysql:8.0")
                .withDatabaseName("learn_platform").withUsername("test").withPassword("test")
                .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci")) {
            database.start();
            var configuration = Flyway.configure().dataSource(database.getJdbcUrl(), "root", database.getPassword());
            if (initialVersion != null) {
                configuration.target(initialVersion).load().migrate();
            }
            Flyway.configure().dataSource(database.getJdbcUrl(), "root", database.getPassword())
                    .target("109").load().migrate();
            var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                    database.getJdbcUrl(), "root", database.getPassword()));
            assertEquals(109, jdbc.queryForObject(
                    "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history", Integer.class));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM question_review_schedule WHERE fsrs_card_json IS NOT NULL", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM fsrs_review_event", Integer.class));
        }
    }
}
