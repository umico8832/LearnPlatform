package com.learnplatform.service.gamification;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("integration")
class GamificationMigrationIntegrationTest {
    @Test
    void upgradesFromPreviousVersionAndCreatesRewardConstraints() {
        try (MySQLContainer<?> database = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("learn_platform").withUsername("test").withPassword("test")) {
            database.start();
            Flyway.configure().dataSource(database.getJdbcUrl(), "root", database.getPassword()).target("104").load().migrate();
            Flyway.configure().dataSource(database.getJdbcUrl(), "root", database.getPassword()).load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(database.getJdbcUrl(), "root", database.getPassword()));
            jdbc.update("INSERT INTO gamification_user_profile (user_id) VALUES (1)");
            jdbc.update("INSERT INTO gamification_xp_ledger (user_id,event_id,course_id,subject_type,subject_id,event_type,event_date,xp,reward_reason,occurred_time) "
                    + "VALUES (1,2,3,'QUESTION',4,'PRACTICE_ANSWERED','2026-09-01',10,'CORRECT_ANSWER','2026-09-01 10:00:00')");
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM gamification_xp_ledger WHERE event_id=2", Integer.class));
            jdbc.update("INSERT INTO gamification_achievement (user_id,achievement_code,unlocked_at,trigger_event_id) VALUES (1,'FIRST_ANSWER',NOW(),2)");
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM gamification_achievement WHERE user_id=1 AND achievement_code='FIRST_ANSWER'", Integer.class));
        }
    }
}
