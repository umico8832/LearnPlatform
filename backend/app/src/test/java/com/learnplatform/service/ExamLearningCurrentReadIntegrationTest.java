package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.dto.ExamLearningAnswerRequest;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.learnplatform.support.MySqlRaceSupport.awaitLatch;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** A first consistent read must not hide an answer committed before the session row is locked. */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
@Import(ExamLearningCurrentReadIntegrationTest.Configuration.class)
class ExamLearningCurrentReadIntegrationTest extends IntegrationTestBase {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ExamPaperLearningService learning;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private MySqlRaceSupport race;

    private Long courseId;
    private Long userId;
    private Long paperId;
    private Long questionId;
    private Long sessionId;
    private Long secondPaperId;
    private Long secondSessionId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
        userId = 88120L;
            jdbc.update("INSERT INTO user(id,username,password,role,status,deleted) VALUES (?,?,'test','USER',1,0)",
                    userId, "rrpaper-" + java.util.UUID.randomUUID());
            jdbc.update("INSERT INTO course(name,status,deleted) VALUES ('RR owned learning course',1,0)");
            courseId = lastId();
            jdbc.update("INSERT INTO user_course(user_id,course_id) VALUES (?,?)", userId, courseId);
            jdbc.update("""
                    INSERT INTO exam_paper(title,course_id,total_score,duration,question_count,status,create_by,owner_user_id,
                      visibility,paper_type,source_reference,source_verified,import_status,deleted)
                    VALUES ('RR 当前读学习试卷',?,1,60,1,1,?,?, 'PRIVATE','USER_PRIVATE','rr-current-read',0,'CONFIRMED',0)
                    """, courseId, userId, userId);
            paperId = lastId();
            jdbc.update("""
                    INSERT INTO question(content,question_type,course_id,difficulty,analysis,score,status,create_by,owner_user_id,
                      visibility,source_type,source_reference,review_rounds,deleted)
                    VALUES ('RR 当前读题','SINGLE_CHOICE',?,1,'解析',1,1,?,?,'PRIVATE','USER_PRIVATE_IMPORT','rr-current-read',0,0)
                    """, courseId, userId, userId);
            questionId = lastId();
            jdbc.update("""
                    INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order,deleted)
                    VALUES (?,'正确','A',1,1,0),(?,'错误','B',0,2,0)
                    """, questionId, questionId);
            jdbc.update("INSERT INTO exam_question(exam_paper_id,question_id,sort_order,score,display_number) VALUES (?,?,1,1,'1')",
                    paperId, questionId);
            sessionId = learning.startSession(paperId, userId).getId();
        });
    }

    @AfterEach
    void cleanUp() {
        if (questionId == null) { return; }
        for (String table : List.of("gamification_achievement", "gamification_xp_ledger", "gamification_user_profile", "fsrs_review_event")) {
            jdbc.update("DELETE FROM " + table + " WHERE user_id=?", userId);
        }
        jdbc.update("DELETE FROM course_learning_event WHERE user_id=?", userId);
        jdbc.update("DELETE FROM wrong_question WHERE question_id=?", questionId);
        jdbc.update("DELETE FROM question_review_schedule WHERE question_id=?", questionId);
        if (secondSessionId != null) {
            jdbc.update("DELETE FROM exam_learning_answer WHERE session_id=?", secondSessionId);
            jdbc.update("DELETE FROM exam_learning_session WHERE id=?", secondSessionId);
            jdbc.update("DELETE FROM exam_question WHERE exam_paper_id=?", secondPaperId);
            jdbc.update("DELETE FROM exam_paper WHERE id=?", secondPaperId);
        }
        jdbc.update("DELETE FROM exam_learning_answer WHERE session_id=?", sessionId);
        jdbc.update("DELETE FROM exam_learning_session WHERE id=?", sessionId);
        jdbc.update("DELETE FROM exam_question WHERE exam_paper_id=?", paperId);
        jdbc.update("DELETE FROM question_option WHERE question_id=?", questionId);
        jdbc.update("DELETE FROM question WHERE id=?", questionId);
        jdbc.update("DELETE FROM exam_paper WHERE id=?", paperId);
        jdbc.update("DELETE FROM user_course WHERE user_id=?", userId);
        jdbc.update("DELETE FROM course WHERE id=?", courseId);
        jdbc.update("DELETE FROM user WHERE id=?", userId);
    }

    @Test
    void concurrentAnswersSeeCommittedAttemptBeforeAssigningNextAttemptNumber() throws Exception {
        CountDownLatch previewRead = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        TransactionTemplate repeatableRead = new TransactionTemplate(transactions);
        repeatableRead.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> race.observe(new MySqlRaceSupport.StatementObserver() {
                @Override public void after(String statement, Connection connection) throws Exception {
                    if (statement.endsWith("ExamLearningSessionMapper.selectById")) {
                        previewRead.countDown();
                        awaitLatch(releaseFirst);
                    }
                }
            }, () -> repeatableRead.execute(status -> learning.submitAnswer(sessionId, answer(), userId))));
            try {
                awaitLatch(previewRead);
                learning.submitAnswer(sessionId, answer(), userId);
                releaseFirst.countDown();
                first.get(30, TimeUnit.SECONDS);
            } finally {
                releaseFirst.countDown();
            }
        }
        assertEquals(List.of(1, 2), jdbc.queryForList(
                "SELECT attempt_no FROM exam_learning_answer WHERE session_id=? AND question_id=? ORDER BY attempt_no",
                Integer.class, sessionId, questionId));
    }

    @Test
    void differentLearningSessionsCanBothCreateTheirFirstAttemptWithoutGapLockDeadlock() throws Exception {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.update("""
                    INSERT INTO exam_paper(title,course_id,total_score,duration,question_count,status,create_by,owner_user_id,
                      visibility,paper_type,source_reference,source_verified,import_status,deleted)
                    VALUES ('RR second owned paper',?,1,60,1,1,?,?, 'PRIVATE','USER_PRIVATE','rr-current-read',0,'CONFIRMED',0)
                    """, courseId, userId, userId);
            secondPaperId = lastId();
            jdbc.update("INSERT INTO exam_question(exam_paper_id,question_id,sort_order,score,display_number) VALUES (?,?,1,1,'1')",
                    secondPaperId, questionId);
            secondSessionId = learning.startSession(secondPaperId, userId).getId();
        });
        var bothReadEmpty = new CountDownLatch(2);
        var observer = new MySqlRaceSupport.StatementObserver() {
            @Override public void after(String statement, Connection connection) throws Exception {
                if (statement.endsWith("ExamLearningAnswerMapper.selectLatestAttemptNoForShare")) {
                    bothReadEmpty.countDown();
                    awaitLatch(bothReadEmpty);
                }
            }
        };
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> race.observe(observer, () -> learning.submitAnswer(sessionId, answer(), userId)));
            var second = pool.submit(() -> race.observe(observer, () -> learning.submitAnswer(secondSessionId, answer(), userId)));
            assertEquals(1, first.get(30, TimeUnit.SECONDS).getAttemptNo());
            assertEquals(1, second.get(30, TimeUnit.SECONDS).getAttemptNo());
        }
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM exam_learning_answer WHERE question_id=?",
                Integer.class, questionId));
    }

    private ExamLearningAnswerRequest answer() {
        ExamLearningAnswerRequest request = new ExamLearningAnswerRequest();
        request.setQuestionId(questionId);
        request.setUserAnswer("A");
        return request;
    }

    private Long lastId() {
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Configuration {
        @Bean MySqlRaceSupport examLearningCurrentReadRaceSupport(Environment environment) {
            return new MySqlRaceSupport(environment);
        }
    }
}
