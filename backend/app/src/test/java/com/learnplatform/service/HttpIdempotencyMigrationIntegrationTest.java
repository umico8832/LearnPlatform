package com.learnplatform.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.mysql.MySQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** V119 must work both on an empty schema and as an upgrade from the V118 production shape. */
@Tag("integration")
class HttpIdempotencyMigrationIntegrationTest {
    @Test void migratesAnEmptyDatabaseToV119() {
        try (var mysql = new MySQLContainer("mysql:8.0").withDatabaseName("learn_platform")
                .withUsername("test").withPassword("test")) {
            mysql.start();
            Flyway.configure().dataSource(mysql.getJdbcUrl(), "root", mysql.getPassword()).target("119").load().migrate();
            var db = new JdbcTemplate(new DriverManagerDataSource(mysql.getJdbcUrl(), "root", mysql.getPassword()));
            assertEquals(1, db.queryForObject("SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics "
                    + "WHERE table_schema=DATABASE() AND table_name='http_idempotency_record' AND index_name='uk_http_idempotency'", Integer.class));
        }
    }

    @Test void upgradesV118WithoutChangingOldRowsAndEnforcesScopedKeys() {
        try (var mysql = new MySQLContainer("mysql:8.0").withDatabaseName("learn_platform")
                .withUsername("test").withPassword("test")) {
            mysql.start();
            var admin = new JdbcTemplate(new DriverManagerDataSource(mysql.getJdbcUrl(), "root", mysql.getPassword()));
            Flyway.configure().dataSource(mysql.getJdbcUrl(), "root", mysql.getPassword()).target("118").load().migrate();
            admin.update("INSERT INTO practice_record(user_id,question_id,user_answer,is_correct) VALUES(900119,1,'A',1)");
            long oldRecord = admin.queryForObject("SELECT MAX(id) FROM practice_record WHERE user_id=900119", Long.class);
            Flyway.configure().dataSource(mysql.getJdbcUrl(), "root", mysql.getPassword()).target("119").load().migrate();
            assertEquals(oldRecord, admin.queryForObject("SELECT id FROM practice_record WHERE user_id=900119", Long.class));
            String insert = "INSERT INTO http_idempotency_record(user_id,operation,request_key,request_hash,response_json) VALUES(900119,'practice.submit','00000000-0000-4000-8000-000000000119','h','{}')";
            admin.update(insert);
            assertThrows(DuplicateKeyException.class, () -> admin.update(insert));
            admin.update(insert.replace("900119,'practice.submit'", "900120,'practice.submit'"));
            admin.update(insert.replace("'practice.submit'", "'review.submit'"));
            assertEquals(3, admin.queryForObject("SELECT COUNT(*) FROM http_idempotency_record", Integer.class));
        }
    }
}
