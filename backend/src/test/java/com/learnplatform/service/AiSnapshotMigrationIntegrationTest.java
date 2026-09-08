package com.learnplatform.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("integration")
@Testcontainers
class AiSnapshotMigrationIntegrationTest {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("learn_platform")
            .withUsername("test")
            .withPassword("test")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    @Test
    void upgradePreservesLegacySnapshotsAndAllowsUngradedAnswers() {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword())
                .target("91").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), "root", MYSQL.getPassword()));
        assertEquals("NO", nullable(jdbc, "exam_learning_answer", "is_correct"));
        assertEquals("NO", nullable(jdbc, "exam_learning_answer", "score"));
        assertEquals("NO", nullable(jdbc, "exam_learning_ai_interaction", "answer_correct"));
        insertAnswer(jdbc, 81L, 0, 0);
        insertAnswer(jdbc, 82L, 1, 2);
        insertInteraction(jdbc, 81L, 0);
        insertInteraction(jdbc, 82L, 1);

        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword()).load().migrate();

        assertEquals("YES", nullable(jdbc, "exam_learning_answer", "is_correct"));
        assertEquals("YES", nullable(jdbc, "exam_learning_answer", "score"));
        assertEquals("YES", nullable(jdbc, "exam_learning_ai_interaction", "answer_correct"));
        assertEquals(0, jdbc.queryForObject(
                "SELECT is_correct FROM exam_learning_answer WHERE id=81", Integer.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT score FROM exam_learning_answer WHERE id=81", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT is_correct FROM exam_learning_answer WHERE id=82", Integer.class));
        assertEquals(2, jdbc.queryForObject(
                "SELECT score FROM exam_learning_answer WHERE id=82", Integer.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT answer_correct FROM exam_learning_ai_interaction WHERE answer_id=81", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT answer_correct FROM exam_learning_ai_interaction WHERE answer_id=82", Integer.class));
        insertAnswer(jdbc, 83L, null, null);
        insertInteraction(jdbc, 83L, null);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM exam_learning_answer "
                + "WHERE id=83 AND is_correct IS NULL AND score IS NULL", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM exam_learning_ai_interaction "
                + "WHERE answer_id=83 AND answer_correct IS NULL", Integer.class));
    }

    private String nullable(JdbcTemplate jdbc, String table, String column) {
        return jdbc.queryForObject("SELECT IS_NULLABLE FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND table_name=? AND column_name=?",
                String.class, table, column);
    }

    private void insertAnswer(JdbcTemplate jdbc, Long id, Integer correct, Integer score) {
        jdbc.update("""
                INSERT INTO exam_learning_answer
                (id, session_id, question_id, attempt_no, user_answer, is_correct, score)
                VALUES (?,30,10,?, 'answer',?,?)
                """, id, id - 80, correct, score);
    }

    private void insertInteraction(JdbcTemplate jdbc, Long answerId, Integer correct) {
        jdbc.update("""
                INSERT INTO exam_learning_ai_interaction
                (user_id, course_id, exam_paper_id, learning_session_id, question_id,
                 answer_id, answer_attempt_no, answer_correct, interaction_type, start_time)
                VALUES (7,20,2,30,10,?,1,?,'EXPLANATION',NOW())
                """, answerId, correct);
    }
}
