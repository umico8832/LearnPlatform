package com.learnplatform.service.gamification;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.mysql.MySQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("integration")
class GamificationDailyIndexMigrationIntegrationTest {
    @Test void migratesAnEmptyDatabaseToV120() {
        try (var mysql = database()) {
            mysql.start();
            Flyway.configure().dataSource(mysql.getJdbcUrl(), "root", mysql.getPassword()).target("120").load().migrate();
            var db = new JdbcTemplate(new DriverManagerDataSource(mysql.getJdbcUrl(), "root", mysql.getPassword()));
            assertEquals(3, columns(db));
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM gamification_xp_ledger", Integer.class));
        }
    }

    @Test void upgradesV119WithoutChangingExistingLedgerOrItsUniqueEvents() {
        try (var mysql = database()) {
            mysql.start();
            var db = new JdbcTemplate(new DriverManagerDataSource(mysql.getJdbcUrl(), "root", mysql.getPassword()));
            Flyway.configure().dataSource(mysql.getJdbcUrl(), "root", mysql.getPassword()).target("119").load().migrate();
            db.update("""
                    INSERT INTO gamification_xp_ledger(user_id,event_id,course_id,subject_type,subject_id,
                    event_type,event_date,xp,reward_reason,occurred_time)
                    VALUES(987071,987071,1,'QUESTION',1,'PRACTICE_ANSWERED','2026-10-23',0,'TEST','2026-10-23 10:00:00')
                    """);
            var before = db.queryForMap("SELECT * FROM gamification_xp_ledger WHERE event_id=987071");
            Flyway.configure().dataSource(mysql.getJdbcUrl(), "root", mysql.getPassword()).target("120").load().migrate();
            assertEquals(before, db.queryForMap("SELECT * FROM gamification_xp_ledger WHERE event_id=987071"));
            assertEquals(3, columns(db));
            assertEquals(1, db.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
                      AND table_name='gamification_xp_ledger' AND index_name='uk_gamification_xp_event' AND non_unique=0
                    """, Integer.class));
        }
    }

    private MySQLContainer database() {
        return new MySQLContainer("mysql:8.0").withDatabaseName("learn_platform")
                .withUsername("test").withPassword("test")
                .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");
    }

    private int columns(JdbcTemplate db) {
        return db.queryForObject("""
                SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
                AND table_name='gamification_xp_ledger' AND index_name='idx_gamification_xp_user_date_xp'
                """, Integer.class);
    }
}
