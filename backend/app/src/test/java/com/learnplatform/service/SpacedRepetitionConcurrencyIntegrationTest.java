package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.dto.ReviewSubmitRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
class SpacedRepetitionConcurrencyIntegrationTest extends IntegrationTestBase {

    private static final long USER_ID = 970001L;
    private static final long COURSE_ID = 970010L;
    private static final long QUESTION_ID = 970020L;
    private static final long SECOND_QUESTION_ID = 970021L;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SpacedRepetitionService spacedRepetitionService;

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbc.update("INSERT INTO course (id,name) VALUES (?, '并发复习测试')", COURSE_ID);
        jdbc.update("INSERT INTO question (id,content,question_type,course_id) "
                + "VALUES (?, '并发提交应累计两次复习', 'SINGLE_CHOICE', ?)", QUESTION_ID, COURSE_ID);
        jdbc.update("INSERT INTO question (id,content,question_type,course_id) "
                + "VALUES (?, '并发加入只应创建一个计划', 'SINGLE_CHOICE', ?)", SECOND_QUESTION_ID, COURSE_ID);
        jdbc.update("INSERT INTO question_option (question_id,content,option_label,is_correct,sort_order) "
                + "VALUES (?, '正确选项', 'A', 1, 1)", QUESTION_ID);
        jdbc.update("INSERT INTO question_review_schedule "
                + "(user_id,question_id,ease_factor,interval_days,repetitions,next_review_date,total_reviews) "
                + "VALUES (?,?,2.50,0,0,CURRENT_DATE,0)", USER_ID, QUESTION_ID);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    void concurrentReviewsSerializeScheduleUpdatesWithoutLosingOne() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        Callable<Void> submission = () -> {
            start.await();
            ReviewSubmitRequest request = new ReviewSubmitRequest();
            request.setQuestionId(QUESTION_ID);
            request.setUserAnswer("A");
            request.setSelfAssessedQuality(4);
            spacedRepetitionService.submitReview(request, USER_ID);
            return null;
        };

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Void>> futures = List.of(executor.submit(submission), executor.submit(submission));
            start.countDown();
            for (Future<Void> future : futures) {
                future.get();
            }
        }

        assertEquals(2, jdbc.queryForObject(
                "SELECT total_reviews FROM question_review_schedule WHERE user_id=? AND question_id=?",
                Integer.class, USER_ID, QUESTION_ID));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM practice_record WHERE user_id=? AND question_id=?",
                Integer.class, USER_ID, QUESTION_ID));
    }

    @Test
    void concurrentAdditionsCreateOneActiveSchedule() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        Callable<Void> addition = () -> {
            start.await();
            spacedRepetitionService.addToReviewPlan(USER_ID, SECOND_QUESTION_ID);
            return null;
        };

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Void>> futures = List.of(executor.submit(addition), executor.submit(addition));
            start.countDown();
            for (Future<Void> future : futures) {
                future.get();
            }
        }

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM question_review_schedule "
                        + "WHERE user_id=? AND question_id=? AND deleted=0",
                Integer.class, USER_ID, SECOND_QUESTION_ID));
    }

    private void cleanUp() {
        if (jdbc == null) {
            return;
        }
        jdbc.update("DELETE FROM course_learning_event WHERE user_id=?", USER_ID);
        jdbc.update("DELETE FROM practice_record WHERE user_id=? AND question_id=?", USER_ID, QUESTION_ID);
        jdbc.update("DELETE FROM wrong_question WHERE user_id=? AND question_id=?", USER_ID, QUESTION_ID);
        jdbc.update("DELETE FROM question_review_schedule WHERE user_id=? AND question_id=?", USER_ID, QUESTION_ID);
        jdbc.update("DELETE FROM question_review_schedule WHERE user_id=? AND question_id=?", USER_ID, SECOND_QUESTION_ID);
        jdbc.update("DELETE FROM question_option WHERE question_id=?", QUESTION_ID);
        jdbc.update("DELETE FROM question WHERE id=?", QUESTION_ID);
        jdbc.update("DELETE FROM question WHERE id=?", SECOND_QUESTION_ID);
        jdbc.update("DELETE FROM course WHERE id=?", COURSE_ID);
    }
}
