package com.learnplatform.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.mysql.MySQLContainer;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("integration")
class BusinessTimeMigrationIntegrationTest {
    @Test void migratesEmptyDatabase() { migrate(false); }
    @Test void upgradesV117AcrossWinterSummerAndBothDstTransitions() { migrate(true); }

    @Test void failedConversionRollsBackDataAndCanResumeAfterRepair() {
        try (var database = new MySQLContainer("mysql:8.0")
                .withDatabaseName("learn_platform").withUsername("test").withPassword("test")) {
            database.start();
            var flyway = Flyway.configure().dataSource(database.getJdbcUrl(), "root", database.getPassword());
            flyway.target("117").load().migrate();
            var db = new JdbcTemplate(new DriverManagerDataSource(database.getJdbcUrl(), "root", database.getPassword()));
            db.update("INSERT INTO exam_record(id,user_id,exam_paper_id,start_time) VALUES(90000,1,1,'2026-07-01 12:30:00')");
            db.execute("CREATE TRIGGER reject_time_conversion BEFORE UPDATE ON question FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test conversion failure'");
            assertThrows(org.flywaydb.core.api.FlywayException.class, () -> flyway.target("118").load().migrate());
            assertEquals(LocalDateTime.parse("2026-07-01T12:30:00"), db.queryForObject("SELECT start_time FROM exam_record WHERE id=90000", LocalDateTime.class));
            assertNull(db.queryForObject("SELECT start_instant_utc FROM exam_record WHERE id=90000", LocalDateTime.class));
            db.execute("DROP TRIGGER reject_time_conversion");
            flyway.load().repair();
            flyway.target("118").load().migrate();
            assertEquals(LocalDateTime.parse("2026-07-01T00:30:00"), db.queryForObject("SELECT start_time FROM exam_record WHERE id=90000", LocalDateTime.class));
        }
    }

    private void migrate(boolean upgrade) {
        try (var database = new MySQLContainer("mysql:8.0")
                .withDatabaseName("learn_platform").withUsername("test").withPassword("test")
                .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci")) {
            database.start();
            var db = new JdbcTemplate(new DriverManagerDataSource(
                    database.getJdbcUrl(), "root", database.getPassword()));
            var flyway = Flyway.configure().dataSource(database.getJdbcUrl(), "root", database.getPassword());
            List<String> times = List.of("2026-01-01T08:30:00", "2026-07-01T12:30:00",
                    "2026-03-08T14:59:59", "2026-03-08T15:00:00",
                    "2026-11-01T13:59:59", "2026-11-01T14:00:00");
            if (upgrade) {
                flyway.target("117").load().migrate();
                for (int i = 0; i < times.size(); i++) {
                    db.update("INSERT INTO exam_record(id,user_id,exam_paper_id,start_time,create_time,update_time)"
                            + " VALUES(?,1,1,?,?,?)", 90000 + i, times.get(i), times.get(i), times.get(i));
                }
                db.update("INSERT INTO question_review_schedule(id,user_id,question_id,next_review_date,fsrs_card_json)"
                        + " VALUES(90000,90000,1,'2026-07-02','{\"due\":\"2026-07-02T03:00:00Z\"}')");
                db.update("INSERT INTO fsrs_review_event(schedule_id,user_id,question_id,source_type,source_id,rating,reviewed_at,create_time)"
                        + " VALUES(90000,90000,1,'PRACTICE',90000,'GOOD','2026-07-01 12:30:00','2026-07-01 12:30:00')");
                db.update("INSERT INTO gamification_user_profile(user_id,total_xp,last_answer_date,current_streak,max_streak)"
                        + " VALUES(90000,30,'2026-07-03',3,3)");
                for (int i = 0; i < 3; i++) {
                    db.update("INSERT INTO gamification_xp_ledger(user_id,event_id,course_id,subject_type,subject_id,event_type,event_date,xp,reward_reason,occurred_time)"
                            + " VALUES(90000,?,1,'QUESTION',1,'PRACTICE_ANSWERED',?,10,'CORRECT_ANSWER',?)",
                            90000 + i, "2026-07-0" + (i + 1),
                            List.of("2026-07-01 11:30:00", "2026-07-02 12:30:00", "2026-07-03 11:30:00").get(i));
                }
            }
            flyway.target("118").load().migrate();
            assertEquals(118, db.queryForObject("SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history", Integer.class));
            if (!upgrade) return;
            for (int i = 0; i < times.size(); i++) {
                LocalDateTime original = LocalDateTime.parse(times.get(i));
                LocalDateTime converted = original.atZone(ZoneId.of("Asia/Shanghai"))
                        .withZoneSameInstant(ZoneId.of("America/New_York")).toLocalDateTime();
                for (String column : List.of("start_time", "create_time", "update_time")) {
                    assertEquals(converted, db.queryForObject("SELECT " + column + " FROM exam_record WHERE id=?",
                            LocalDateTime.class, 90000 + i), column);
                }
                assertEquals(original.minusHours(8), db.queryForObject(
                        "SELECT start_instant_utc FROM exam_record WHERE id=?", LocalDateTime.class, 90000 + i));
                assertNull(db.queryForObject("SELECT end_time FROM exam_record WHERE id=?", LocalDateTime.class, 90000 + i));
            }
            assertEquals("2026-07-02", db.queryForObject("SELECT CAST(next_review_date AS CHAR) FROM question_review_schedule WHERE id=90000", String.class));
            assertEquals("2026-07-02T03:00:00Z", db.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(fsrs_card_json,'$.due')) FROM question_review_schedule WHERE id=90000", String.class));
            assertEquals(LocalDateTime.parse("2026-07-01T12:30:00"), db.queryForObject("SELECT reviewed_at FROM fsrs_review_event WHERE user_id=90000", LocalDateTime.class));
            assertEquals(LocalDateTime.parse("2026-07-01T00:30:00"), db.queryForObject("SELECT create_time FROM fsrs_review_event WHERE user_id=90000", LocalDateTime.class));
            assertEquals(List.of("2026-06-30", "2026-07-02", "2026-07-02"), db.queryForList("SELECT CAST(event_date AS CHAR) FROM gamification_xp_ledger WHERE user_id=90000 ORDER BY event_id", String.class));
            assertEquals(30, db.queryForObject("SELECT total_xp FROM gamification_user_profile WHERE user_id=90000", Integer.class));
            assertEquals(1, db.queryForObject("SELECT current_streak FROM gamification_user_profile WHERE user_id=90000", Integer.class));
            assertEquals(1, db.queryForObject("SELECT max_streak FROM gamification_user_profile WHERE user_id=90000", Integer.class));
            assertEquals("2026-07-02", db.queryForObject("SELECT CAST(last_answer_date AS CHAR) FROM gamification_user_profile WHERE user_id=90000", String.class));
        }
    }
}
