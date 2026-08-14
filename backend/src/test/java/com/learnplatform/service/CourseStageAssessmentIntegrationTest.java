package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.dto.CourseStageAssessmentCreateRequest;
import com.learnplatform.dto.CourseStageAssessmentSubmitRequest;
import com.learnplatform.dto.CourseStageAssessmentVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
class CourseStageAssessmentIntegrationTest extends IntegrationTestBase {
    private static final String USERNAME = "stage-assessment-test";

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private CourseStageAssessmentService service;

    @AfterEach
    void cleanUp() {
        Long userId = jdbcTemplate.query("SELECT id FROM user WHERE username = ?",
                result -> result.next() ? result.getLong(1) : null, USERNAME);
        if (userId == null) return;
        jdbcTemplate.update("DELETE FROM course_learning_event WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM question_review_schedule WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM wrong_question WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE q FROM course_stage_assessment_question q "
                + "JOIN course_stage_assessment a ON a.id = q.assessment_id WHERE a.user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM course_stage_assessment WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM user_course WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM user WHERE id = ?", userId);
    }

    @Test
    void createsSnapshotGradesOnceAndWritesCourseFacts() {
        jdbcTemplate.update("INSERT INTO user (username,password,role,status,deleted) VALUES (?,'test','USER',1,0)",
                USERNAME);
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user WHERE username = ?", Long.class, USERNAME);
        jdbcTemplate.update("INSERT INTO user_course (user_id,course_id) VALUES (?,1)", userId);
        CourseStageAssessmentCreateRequest create = new CourseStageAssessmentCreateRequest();
        create.setQuestionCount(3);

        CourseStageAssessmentVO started = service.start(userId, 1L, create);

        assertEquals("COURSE_SEQUENCE_FALLBACK", started.getSelectionStrategy());
        assertEquals(3, started.getQuestions().size());
        started.getQuestions().forEach(question -> {
            assertNull(question.getCorrectAnswer());
            assertNull(question.getAnalysis());
        });
        CourseStageAssessmentVO resumed = service.start(userId, 1L, create);
        assertEquals(started.getId(), resumed.getId());

        CourseStageAssessmentSubmitRequest submit = new CourseStageAssessmentSubmitRequest();
        submit.setAnswers(List.of(
                answer(started.getQuestions().get(0).getId(), "A"),
                answer(started.getQuestions().get(1).getId(), "B"),
                answer(started.getQuestions().get(2).getId(), "TRUE")));
        CourseStageAssessmentVO completed = service.submit(started.getId(), userId, submit);

        assertEquals("COMPLETED", completed.getStatus());
        assertEquals(2, completed.getCorrectCount());
        assertEquals(3, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM course_learning_event WHERE user_id = ? "
                        + "AND event_source = 'STAGE_ASSESSMENT'", Integer.class, userId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wrong_question WHERE user_id = ? AND deleted = 0", Integer.class, userId));
        assertEquals(3, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question_review_schedule WHERE user_id = ?", Integer.class, userId));
        CourseStageAssessmentVO restarted = service.start(userId, 1L, create);
        assertNotEquals(completed.getId(), restarted.getId());
        assertEquals("IN_PROGRESS", restarted.getStatus());
    }

    private CourseStageAssessmentSubmitRequest.Answer answer(Long id, String value) {
        CourseStageAssessmentSubmitRequest.Answer answer = new CourseStageAssessmentSubmitRequest.Answer();
        answer.setAssessmentQuestionId(id);
        answer.setUserAnswer(value);
        return answer;
    }
}
