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

import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("integration")
@Testcontainers
class KnowledgeIndexMigrationIntegrationTest {
    @Container private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("learn_platform").withUsername("test").withPassword("test");

    @Test void addsReviewAuditAndPreventsDuplicateIndexKeys() {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword()).target("97").load().migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword()));
        jdbc.update("INSERT INTO knowledge_content_bundle (course_key,bundle_version,manifest_hash,source_revision,"
                + "source_quality_status,chunk_count,imported_by) VALUES ('course','v1',?,'rev','reviewed',1,7)",
                "a".repeat(64));
        jdbc.update("INSERT INTO knowledge_content_index (bundle_id,index_key,model,dimensions,collection_name,"
                + "vector_endpoint_hash,status) VALUES (1,?,'model',3,'collection',?,'FAILED')",
                "b".repeat(64), "c".repeat(64));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO knowledge_content_index (bundle_id,index_key,model,dimensions,collection_name,"
                        + "vector_endpoint_hash,status) VALUES (1,?,'model',3,'collection',?,'FAILED')",
                "b".repeat(64), "c".repeat(64)));
    }
}
