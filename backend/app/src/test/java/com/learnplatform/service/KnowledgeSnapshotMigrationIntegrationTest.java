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

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
@Testcontainers
class KnowledgeSnapshotMigrationIntegrationTest {
    @Container private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("learn_platform").withUsername("test").withPassword("test");

    @Test void upgradesExistingDatabaseAndProtectsVersionAndReviewState() {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword()).target("95").load().migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword()));
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword()).target("96").load().migrate();
        String sql = "INSERT INTO knowledge_content_bundle "
                + "(course_key,bundle_version,manifest_hash,source_revision,source_quality_status,chunk_count,imported_by) "
                + "VALUES ('course','v1',?,'revision','reviewed',1,7)";
        jdbc.update(sql, "a".repeat(64));
        assertEquals("PENDING", jdbc.queryForObject("SELECT review_status FROM knowledge_content_bundle", String.class));
        assertThrows(DataAccessException.class, () -> jdbc.update(sql, "b".repeat(64)));
        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE knowledge_content_bundle SET review_status='OPEN'"));
    }
}
