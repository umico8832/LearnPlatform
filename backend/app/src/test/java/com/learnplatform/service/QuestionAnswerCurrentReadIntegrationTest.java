package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.dto.PracticeResultVO;
import com.learnplatform.dto.PracticeSubmitRequest;
import com.learnplatform.dto.QuestionCreateRequest;
import com.learnplatform.dto.ReviewScheduleVO;
import com.learnplatform.dto.ReviewSubmitRequest;
import com.learnplatform.support.MySqlRaceSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Connection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.learnplatform.support.MySqlRaceSupport.awaitLatch;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Locks the question after a repeatable-read snapshot, then verifies grading uses current options. */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
@Import(QuestionAnswerCurrentReadIntegrationTest.RaceConfiguration.class)
class QuestionAnswerCurrentReadIntegrationTest extends IntegrationTestBase {

    @Autowired private PracticeAnswerService practiceAnswers;
    @Autowired private SpacedRepetitionService reviews;
    @Autowired private QuestionService questions;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MySqlRaceSupport race;

    private Long userId;
    private Long courseId;
    private Long questionId;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO user(id,username,password,role,status,deleted) VALUES (88121,?,'test','USER',1,0)",
                "d-current-read-user");
        userId = jdbc.queryForObject("SELECT id FROM user WHERE username=?", Long.class, "d-current-read-user");
        jdbc.update("INSERT INTO course(name,status,deleted) VALUES ('D 当前读判分课程',1,0)");
        courseId = jdbc.queryForObject(
                "SELECT id FROM course WHERE name='D 当前读判分课程' ORDER BY id DESC LIMIT 1", Long.class);
        jdbc.update("INSERT INTO user_course(user_id,course_id) VALUES (?,?)", userId, courseId);
        jdbc.update("""
                INSERT INTO question(content,question_type,course_id,difficulty,analysis,score,status,visibility,deleted)
                VALUES ('D 当前读判分题','SINGLE_CHOICE',?,1,'fixture',1,1,'PUBLIC',0)
                """, courseId);
        questionId = jdbc.queryForObject(
                "SELECT id FROM question WHERE course_id=? ORDER BY id DESC LIMIT 1", Long.class, courseId);
        jdbc.update("INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order) VALUES "
                + "(?,'原正确答案','A',1,1),(?,'原错误答案','B',0,2)", questionId, questionId);
    }

    @AfterEach
    void cleanUp() {
        if (userId == null) {
            return;
        }
        for (String table : List.of("gamification_achievement", "gamification_xp_ledger", "gamification_user_profile")) {
            jdbc.update("DELETE FROM " + table + " WHERE user_id=?", userId);
        }
        jdbc.update("DELETE FROM course_learning_event WHERE user_id=?", userId);
        jdbc.update("DELETE FROM fsrs_review_event WHERE user_id=?", userId);
        jdbc.update("DELETE FROM practice_record WHERE user_id=?", userId);
        jdbc.update("DELETE FROM wrong_question WHERE user_id=?", userId);
        jdbc.update("DELETE FROM question_review_schedule WHERE user_id=?", userId);
        if (questionId != null) {
            jdbc.update("DELETE FROM question_review_record WHERE question_id=?", questionId);
            jdbc.update("DELETE FROM question_version WHERE question_id=?", questionId);
            jdbc.update("DELETE FROM question_knowledge_point WHERE question_id=?", questionId);
            jdbc.update("DELETE FROM question_option WHERE question_id=?", questionId);
            jdbc.update("DELETE FROM question WHERE id=?", questionId);
        }
        if (courseId != null) {
            jdbc.update("DELETE FROM user_course WHERE user_id=? AND course_id=?", userId, courseId);
            jdbc.update("DELETE FROM course WHERE id=?", courseId);
        }
        jdbc.update("DELETE FROM user WHERE id=?", userId);
    }

    @Test
    void practiceGradesAgainstOptionsCommittedBeforeQuestionShareLock() throws Exception {
        PracticeSubmitRequest request = new PracticeSubmitRequest();
        request.setQuestionId(questionId);
        request.setUserAnswer("B");

        PracticeResultVO result = updateOptionsWhileQuestionShareIsPaused(
                () -> practiceAnswers.submitAnswer(request, userId));

        assertTrue(result.getCorrect(), "练习必须使用管理员已提交的 B 选项判分");
    }

    @Test
    void reviewGradesAgainstOptionsCommittedBeforeQuestionShareLock() throws Exception {
        jdbc.update("""
                INSERT INTO question_review_schedule
                  (user_id,question_id,ease_factor,interval_days,repetitions,next_review_date,total_reviews)
                VALUES (?,?,2.50,0,0,CURRENT_DATE,0)
                """, userId, questionId);
        ReviewSubmitRequest request = new ReviewSubmitRequest();
        request.setQuestionId(questionId);
        request.setUserAnswer("B");
        request.setSelfAssessedQuality(4);

        ReviewScheduleVO result = updateOptionsWhileQuestionShareIsPaused(
                () -> reviews.submitReview(request, userId));

        assertTrue(result.getCorrect(), "独立复习必须使用管理员已提交的 B 选项判分");
    }

    private <T> T updateOptionsWhileQuestionShareIsPaused(CheckedSupplier<T> answer) throws Exception {
        CountDownLatch shareRequested = new CountDownLatch(1);
        CountDownLatch releaseShare = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<T> answerFuture = executor.submit(() -> race.observe(new MySqlRaceSupport.StatementObserver() {
                @Override
                public void before(String statement, Connection connection) throws Exception {
                    if (statement.endsWith("QuestionMapper.selectActiveForShare")) {
                        shareRequested.countDown();
                        awaitLatch(releaseShare);
                    }
                }
            }, answer::get));
            try {
                awaitLatch(shareRequested);
                questions.updateQuestion(questionId, optionsWithBAsCorrect(), userId);
            } finally {
                releaseShare.countDown();
            }
            try {
                return answerFuture.get();
            } catch (ExecutionException exception) {
                throw unwrap(exception);
            }
        }
    }

    private QuestionCreateRequest optionsWithBAsCorrect() {
        QuestionCreateRequest request = new QuestionCreateRequest();
        request.setOptions(List.of(option("A", "管理员已改为错误", 0, 1),
                option("B", "管理员已改为正确", 1, 2)));
        return request;
    }

    private QuestionCreateRequest.OptionItem option(String label, String content, int correct, int sortOrder) {
        QuestionCreateRequest.OptionItem item = new QuestionCreateRequest.OptionItem();
        item.setOptionLabel(label);
        item.setContent(content);
        item.setIsCorrect(correct);
        item.setSortOrder(sortOrder);
        return item;
    }

    private Exception unwrap(ExecutionException exception) throws Exception {
        if (exception.getCause() instanceof Exception cause) {
            return cause;
        }
        throw exception;
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RaceConfiguration {
        @Bean
        MySqlRaceSupport questionAnswerCurrentReadRace(Environment environment) {
            return new MySqlRaceSupport(environment);
        }
    }
}
