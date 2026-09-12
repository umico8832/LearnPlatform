package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.support.MySqlRaceSupport;
import com.learnplatform.dto.CourseOverviewVO;
import com.learnplatform.dto.CourseStageAssessmentCreateRequest;
import com.learnplatform.dto.CourseStageAssessmentSubmitRequest;
import com.learnplatform.dto.CourseStageAssessmentVO;
import com.learnplatform.dto.CourseStageAssessmentSummaryVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
@Import(CourseStageAssessmentIntegrationTest.RaceConfiguration.class)
class CourseStageAssessmentIntegrationTest extends IntegrationTestBase {
    private static final String USERNAME = "stage-assessment-test";

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private CourseStageAssessmentService service;
    @Autowired private CourseOverviewService overviewService;
    @Autowired private MySqlRaceSupport raceSupport;

    @AfterEach
    void cleanUp() {
        String usernamePattern = USERNAME + "%";
        Integer users = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user WHERE username LIKE ?",
                Integer.class, usernamePattern);
        if (users == null || users == 0) return;
        jdbcTemplate.update("DELETE FROM course_learning_event WHERE user_id IN "
                + "(SELECT id FROM user WHERE username LIKE ?)", usernamePattern);
        jdbcTemplate.update("DELETE FROM question_review_schedule WHERE user_id IN "
                + "(SELECT id FROM user WHERE username LIKE ?)", usernamePattern);
        jdbcTemplate.update("DELETE FROM wrong_question WHERE user_id IN "
                + "(SELECT id FROM user WHERE username LIKE ?)", usernamePattern);
        jdbcTemplate.update("DELETE q FROM course_stage_assessment_question q "
                + "JOIN course_stage_assessment a ON a.id = q.assessment_id "
                + "WHERE a.user_id IN (SELECT id FROM user WHERE username LIKE ?)", usernamePattern);
        jdbcTemplate.update("DELETE FROM course_stage_assessment WHERE user_id IN "
                + "(SELECT id FROM user WHERE username LIKE ?)", usernamePattern);
        jdbcTemplate.update("DELETE FROM user_course WHERE user_id IN "
                + "(SELECT id FROM user WHERE username LIKE ?)", usernamePattern);
        jdbcTemplate.update("DELETE FROM question_knowledge_point WHERE knowledge_point_id IN "
                + "(SELECT id FROM knowledge_point WHERE name LIKE '测试限定知识点%' AND course_id IN (1,2))");
        jdbcTemplate.update("DELETE FROM question_option WHERE question_id IN "
                + "(SELECT id FROM question WHERE content LIKE '测试限定知识点%')");
        jdbcTemplate.update("DELETE FROM question WHERE content LIKE '测试限定知识点%'");
        jdbcTemplate.update("DELETE FROM knowledge_point WHERE name LIKE '测试限定知识点%'");
        jdbcTemplate.update("DELETE FROM user WHERE username LIKE ?", usernamePattern);
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
        Page<CourseStageAssessmentSummaryVO> history = service.listCompleted(userId, 1L, 1, 10);
        assertEquals(1, history.getTotal());
        assertEquals(completed.getId(), history.getRecords().get(0).getId());
        assertEquals("A", service.getCompleted(completed.getId(), userId)
                .getQuestions().get(0).getCorrectAnswer());
        CourseOverviewVO overview = overviewService.getOverview(userId, 1L);
        assertEquals(completed.getId(), overview.getLatestStageAssessment().getId());
        CourseStageAssessmentVO restarted = service.start(userId, 1L, create);
        assertNotEquals(completed.getId(), restarted.getId());
        assertEquals("IN_PROGRESS", restarted.getStatus());
    }

    @Test
    void restrictsScopedAssessmentToReviewedKnowledgePointQuestions() {
        jdbcTemplate.update("INSERT INTO user (username,password,role,status,deleted) VALUES (?,'test','USER',1,0)",
                USERNAME);
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user WHERE username = ?", Long.class, USERNAME);
        jdbcTemplate.update("INSERT INTO user_course (user_id,course_id) VALUES (?,1)", userId);

        Long kpId = insertKnowledgePoint(1L, "REVIEWED");
        Long kpOtherCourse = insertKnowledgePoint(2L, "REVIEWED");
        Long kpUnreviewed = insertKnowledgePoint(1L, null);
        Long kpEmpty = insertKnowledgePoint(1L, "REVIEWED");
        Long qA = insertQuestion(1L, "限定范围单选题", "SINGLE_CHOICE", "A");
        Long qB = insertQuestion(1L, "限定范围判断题", "TRUE_FALSE", "A");
        Long qOutside = insertQuestion(1L, "范围外题目", "SINGLE_CHOICE", "B");
        jdbcTemplate.update("INSERT INTO question_knowledge_point (question_id,knowledge_point_id) VALUES (?,?)",
                qA, kpId);
        jdbcTemplate.update("INSERT INTO question_knowledge_point (question_id,knowledge_point_id) VALUES (?,?)",
                qB, kpId);

        CourseStageAssessmentCreateRequest scoped = new CourseStageAssessmentCreateRequest();
        scoped.setQuestionCount(5);
        scoped.setKnowledgePointId(kpId);
        CourseStageAssessmentVO started = service.start(userId, 1L, scoped);

        assertEquals(kpId, started.getTargetKnowledgePointId());
        assertEquals("测试限定知识点", started.getTargetKnowledgePointName());
        assertEquals(2, started.getQuestions().size());
        List<Long> selectedIds = started.getQuestions().stream()
                .map(question -> question.getQuestionId()).toList();
        assertTrue(selectedIds.contains(qA) && selectedIds.contains(qB));
        assertTrue(!selectedIds.contains(qOutside));
        started.getQuestions().forEach(question -> {
            assertEquals(1, question.getKnowledgePoints().size());
            assertEquals(kpId, question.getKnowledgePoints().get(0).getId());
            assertEquals("测试限定知识点", question.getKnowledgePoints().get(0).getName());
        });

        CourseStageAssessmentSubmitRequest submit = new CourseStageAssessmentSubmitRequest();
        submit.setAnswers(List.of(
                answer(started.getQuestions().get(0).getId(), "A"),
                answer(started.getQuestions().get(1).getId(), "TRUE")));
        CourseStageAssessmentVO completed = service.submit(started.getId(), userId, submit);
        assertEquals(2, completed.getCorrectCount());
        CourseStageAssessmentVO detail = service.getCompleted(started.getId(), userId);
        assertEquals(kpId, detail.getTargetKnowledgePointId());
        assertEquals("测试限定知识点", detail.getTargetKnowledgePointName());
        assertEquals(1, detail.getQuestions().get(0).getKnowledgePoints().size());
        assertEquals("测试限定知识点", detail.getQuestions().get(0).getKnowledgePoints().get(0).getName());
        assertEquals(1, detail.getKnowledgePointSummary().size());
        assertEquals(kpId, detail.getKnowledgePointSummary().get(0).getId());
        assertEquals("测试限定知识点", detail.getKnowledgePointSummary().get(0).getName());
        assertEquals(2, detail.getKnowledgePointSummary().get(0).getQuestionCount());
        assertEquals(2, detail.getKnowledgePointSummary().get(0).getCorrectCount());
        CourseOverviewVO overview = overviewService.getOverview(userId, 1L);
        assertEquals(1, overview.getLatestStageAssessment().getKnowledgePointSummary().size());
        assertEquals(kpId, overview.getLatestStageAssessment().getKnowledgePointSummary().get(0).getId());
        assertEquals("测试限定知识点", overview.getLatestStageAssessment().getKnowledgePointSummary().get(0).getName());
        assertEquals(2, overview.getLatestStageAssessment().getKnowledgePointSummary().get(0).getQuestionCount());
        Page<CourseStageAssessmentSummaryVO> kpHistory = service.listCompleted(userId, 1L, 1, 10, kpId);
        assertEquals(1, kpHistory.getTotal());
        assertEquals(started.getId(), kpHistory.getRecords().get(0).getId());
        Page<CourseStageAssessmentSummaryVO> otherKp = service.listCompleted(userId, 1L, 1, 10, 999999L);
        assertEquals(0, otherKp.getTotal());
        Page<CourseStageAssessmentSummaryVO> history = service.listCompleted(userId, 1L, 1, 10);
        assertEquals("测试限定知识点", history.getRecords().get(0).getTargetKnowledgePointName());

        CourseStageAssessmentCreateRequest crossCourse = new CourseStageAssessmentCreateRequest();
        crossCourse.setKnowledgePointId(kpOtherCourse);
        BusinessException crossCourseError = assertThrows(BusinessException.class,
                () -> service.start(userId, 1L, crossCourse));
        assertEquals("知识点不属于当前课程或尚未通过内容审查", crossCourseError.getMessage());

        CourseStageAssessmentCreateRequest unreviewed = new CourseStageAssessmentCreateRequest();
        unreviewed.setKnowledgePointId(kpUnreviewed);
        BusinessException unreviewedError = assertThrows(BusinessException.class,
                () -> service.start(userId, 1L, unreviewed));
        assertEquals("知识点不属于当前课程或尚未通过内容审查", unreviewedError.getMessage());

        CourseStageAssessmentCreateRequest noCandidate = new CourseStageAssessmentCreateRequest();
        noCandidate.setKnowledgePointId(kpEmpty);
        BusinessException noCandidateError = assertThrows(BusinessException.class,
                () -> service.start(userId, 1L, noCandidate));
        assertEquals("该知识点暂无可用于阶段测评的客观题", noCandidateError.getMessage());
    }

    @Test
    void userCourseLockMakesConcurrentStartsReturnOneActiveAssessment() throws Exception {
        Long userId = insertUser(USERNAME);
        jdbcTemplate.update("INSERT INTO user_course (user_id,course_id) VALUES (?,1)", userId);
        Long knowledgePointId = insertKnowledgePoint(1L, "REVIEWED");
        Long questionId = insertQuestion(1L, "并发启动题", "SINGLE_CHOICE", "A");
        jdbcTemplate.update("INSERT INTO question_knowledge_point (question_id,knowledge_point_id) VALUES (?,?)",
                questionId, knowledgePointId);
        CourseStageAssessmentCreateRequest request = scopedRequest(knowledgePointId, 1);
        CountDownLatch firstLockHeld = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch contenderRequestedLock = new CountDownLatch(1);
        AtomicLong firstConnection = new AtomicLong();
        AtomicLong contenderConnection = new AtomicLong();

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<CourseStageAssessmentVO> first = executor.submit(() -> raceSupport.observe(
                    new MySqlRaceSupport.StatementObserver() {
                        @Override
                        public void after(String statement, java.sql.Connection connection) throws Exception {
                            if (statement.endsWith("CourseStageAssessmentMapper.lockUserCourse")) {
                                firstConnection.set(MySqlRaceSupport.connectionId(connection));
                                firstLockHeld.countDown();
                                MySqlRaceSupport.awaitLatch(releaseFirst);
                            }
                        }
                    }, () -> service.start(userId, 1L, request)));
            try {
                MySqlRaceSupport.awaitLatch(firstLockHeld);
                Future<CourseStageAssessmentVO> second = executor.submit(() -> raceSupport.observe(
                        new MySqlRaceSupport.StatementObserver() {
                            @Override
                            public void before(String statement, java.sql.Connection connection) throws Exception {
                                if (statement.endsWith("CourseStageAssessmentMapper.lockUserCourse")) {
                                    contenderConnection.set(MySqlRaceSupport.connectionId(connection));
                                    contenderRequestedLock.countDown();
                                }
                            }
                        }, () -> service.start(userId, 1L, request)));
                MySqlRaceSupport.awaitLatch(contenderRequestedLock);
                raceSupport.awaitDatabaseLock(firstConnection.get(), contenderConnection.get());
                releaseFirst.countDown();

                CourseStageAssessmentVO firstStarted = first.get(30, TimeUnit.SECONDS);
                CourseStageAssessmentVO secondStarted = second.get(30, TimeUnit.SECONDS);
                assertEquals(firstStarted.getId(), secondStarted.getId());
            } finally {
                releaseFirst.countDown();
            }
        }

        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM course_stage_assessment "
                + "WHERE user_id = ? AND course_id = 1 AND active_session_key = 'ACTIVE'", Integer.class, userId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"A", "B"})
    void assessmentLockMakesConcurrentSubmissionsWriteFactsOnlyOnce(String winningAnswer) throws Exception {
        Long userId = insertUser(USERNAME);
        jdbcTemplate.update("INSERT INTO user_course (user_id,course_id) VALUES (?,1)", userId);
        Long knowledgePointId = insertKnowledgePoint(1L, "REVIEWED");
        insertQuestion(1L, "并发提交题", "SINGLE_CHOICE", "A");
        Long questionId = jdbcTemplate.queryForObject("SELECT id FROM question WHERE content = ?", Long.class,
                "测试限定知识点并发提交题");
        jdbcTemplate.update("INSERT INTO question_knowledge_point (question_id,knowledge_point_id) VALUES (?,?)",
                questionId, knowledgePointId);
        CourseStageAssessmentVO started = service.start(userId, 1L, scopedRequest(knowledgePointId, 1));
        CourseStageAssessmentSubmitRequest winningRequest = submission(started, winningAnswer);
        CourseStageAssessmentSubmitRequest contendingRequest = submission(started, oppositeAnswer(winningAnswer));
        CountDownLatch firstLockHeld = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch contenderRequestedLock = new CountDownLatch(1);
        AtomicLong firstConnection = new AtomicLong();
        AtomicLong contenderConnection = new AtomicLong();

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<CourseStageAssessmentVO> first = executor.submit(() -> raceSupport.observe(
                    holdFirstAssessmentLock(firstLockHeld, releaseFirst, firstConnection),
                    () -> service.submit(started.getId(), userId, winningRequest)));
            try {
                MySqlRaceSupport.awaitLatch(firstLockHeld);
                Future<CourseStageAssessmentVO> second = executor.submit(() -> raceSupport.observe(
                        observeContendingAssessmentLock(contenderRequestedLock, contenderConnection),
                        () -> service.submit(started.getId(), userId, contendingRequest)));
                MySqlRaceSupport.awaitLatch(contenderRequestedLock);
                raceSupport.awaitDatabaseLock(firstConnection.get(), contenderConnection.get());
                releaseFirst.countDown();

                CourseStageAssessmentVO firstCompleted = first.get(30, TimeUnit.SECONDS);
                CourseStageAssessmentVO secondCompleted = second.get(30, TimeUnit.SECONDS);
                assertEquals("COMPLETED", firstCompleted.getStatus());
                assertEquals(firstCompleted.getCorrectCount(), secondCompleted.getCorrectCount());
                assertEquals(firstCompleted.getQuestions().get(0).getUserAnswer(),
                        secondCompleted.getQuestions().get(0).getUserAnswer());
                assertEquals(winningAnswer, secondCompleted.getQuestions().get(0).getUserAnswer());
            } finally {
                releaseFirst.countDown();
            }
        }

        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM course_learning_event "
                + "WHERE user_id = ? AND event_source = 'STAGE_ASSESSMENT'", Integer.class, userId));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM question_review_schedule "
                + "WHERE user_id = ? AND question_id = ?", Integer.class, userId, questionId));
        assertEquals("A".equals(winningAnswer) ? 0 : 1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wrong_question WHERE user_id = ? AND question_id = ? AND deleted = 0",
                Integer.class, userId, questionId));
        if ("B".equals(winningAnswer)) {
            assertEquals(1, jdbcTemplate.queryForObject("SELECT wrong_count FROM wrong_question "
                    + "WHERE user_id = ? AND question_id = ?", Integer.class, userId, questionId));
        }
        Long snapshotId = started.getQuestions().get(0).getId();
        assertEquals(snapshotId, jdbcTemplate.queryForObject("SELECT source_record_id FROM course_learning_event "
                + "WHERE user_id = ? AND event_source = 'STAGE_ASSESSMENT'", Long.class, userId));
        assertEquals(Boolean.toString("A".equals(winningAnswer)), jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.isCorrect')) FROM course_learning_event "
                        + "WHERE user_id = ? AND event_source = 'STAGE_ASSESSMENT'",
                String.class, userId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CourseLearningEventMapper.insert", "CourseStageAssessmentMapper.complete"})
    void rollsBackAllStageFactsWhenFailureOccursThenCanRetry(String failureStatement) throws Exception {
        Long userId = insertUser(USERNAME);
        jdbcTemplate.update("INSERT INTO user_course (user_id,course_id) VALUES (?,1)", userId);
        Long knowledgePointId = insertKnowledgePoint(1L, "REVIEWED");
        Long firstQuestionId = insertQuestion(1L, "回滚第一题", "SINGLE_CHOICE", "A");
        Long secondQuestionId = insertQuestion(1L, "回滚第二题", "SINGLE_CHOICE", "A");
        jdbcTemplate.update("INSERT INTO question_knowledge_point (question_id,knowledge_point_id) VALUES (?,?)",
                firstQuestionId, knowledgePointId);
        jdbcTemplate.update("INSERT INTO question_knowledge_point (question_id,knowledge_point_id) VALUES (?,?)",
                secondQuestionId, knowledgePointId);
        jdbcTemplate.update("INSERT INTO wrong_question "
                        + "(user_id,question_id,wrong_count,mastery_level,last_wrong_answer,deleted) "
                        + "VALUES (?,?,3,0,'历史错误',0)", userId, firstQuestionId);
        jdbcTemplate.update("INSERT INTO question_review_schedule "
                        + "(user_id,question_id,ease_factor,interval_days,repetitions,next_review_date,total_reviews,deleted) "
                        + "VALUES (?,?,2.50,0,0,CURRENT_DATE,0,0)", userId, firstQuestionId);
        CourseStageAssessmentVO started = service.start(userId, 1L, scopedRequest(knowledgePointId, 2));
        CourseStageAssessmentSubmitRequest request = submission(started, "B");
        int expectedWrittenFacts = failureStatement.endsWith("complete") ? 2 : 1;

        RuntimeException failure = assertThrows(RuntimeException.class, () -> raceSupport.observe(
                new MySqlRaceSupport.StatementObserver() {
                    @Override
                    public void after(String statement, java.sql.Connection connection) throws Exception {
                        if (statement.endsWith(failureStatement)) {
                            assertEquals(expectedWrittenFacts, countRows(connection,
                                    "SELECT COUNT(*) FROM course_stage_assessment_question "
                                            + "WHERE assessment_id = ? AND user_answer IS NOT NULL", started.getId()));
                            assertEquals(expectedWrittenFacts, countRows(connection,
                                    "SELECT COUNT(*) FROM course_learning_event "
                                            + "WHERE user_id = ? AND event_source = 'STAGE_ASSESSMENT'", userId));
                            throw new IllegalStateException("test completion fault");
                        }
                    }
                }, () -> service.submit(started.getId(), userId, request)));
        assertEquals("test completion fault", rootMessage(failure));
        assertEquals("IN_PROGRESS", jdbcTemplate.queryForObject("SELECT status FROM course_stage_assessment "
                + "WHERE id = ?", String.class, started.getId()));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM course_stage_assessment_question "
                        + "WHERE assessment_id = ? AND (user_answer IS NOT NULL OR is_correct IS NOT NULL)",
                Integer.class, started.getId()));
        assertEquals(3, jdbcTemplate.queryForObject("SELECT wrong_count FROM wrong_question "
                + "WHERE user_id = ? AND question_id = ?", Integer.class, userId, firstQuestionId));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM wrong_question "
                + "WHERE user_id = ? AND question_id = ?", Integer.class, userId, secondQuestionId));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM question_review_schedule "
                + "WHERE user_id = ?", Integer.class, userId));
        assertEquals("2.50|0|0|0", reviewState(userId, firstQuestionId));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM course_learning_event "
                + "WHERE user_id = ? AND event_source = 'STAGE_ASSESSMENT'", Integer.class, userId));

        CourseStageAssessmentVO completed = service.submit(started.getId(), userId, request);

        assertEquals("COMPLETED", completed.getStatus());
        assertEquals(0, completed.getCorrectCount());
        assertEquals(4, jdbcTemplate.queryForObject("SELECT wrong_count FROM wrong_question "
                + "WHERE user_id = ? AND question_id = ?", Integer.class, userId, firstQuestionId));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT wrong_count FROM wrong_question "
                + "WHERE user_id = ? AND question_id = ?", Integer.class, userId, secondQuestionId));
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM question_review_schedule "
                + "WHERE user_id = ?", Integer.class, userId));
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM course_learning_event "
                + "WHERE user_id = ? AND event_source = 'STAGE_ASSESSMENT'", Integer.class, userId));
        assertEquals("2.50|0|0|0", reviewState(userId, firstQuestionId));

        CourseStageAssessmentVO replayed = service.submit(started.getId(), userId, submission(started, "A"));

        assertEquals(0, replayed.getCorrectCount());
        assertEquals(4, jdbcTemplate.queryForObject("SELECT wrong_count FROM wrong_question "
                + "WHERE user_id = ? AND question_id = ?", Integer.class, userId, firstQuestionId));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT wrong_count FROM wrong_question "
                + "WHERE user_id = ? AND question_id = ?", Integer.class, userId, secondQuestionId));
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM course_learning_event "
                + "WHERE user_id = ? AND event_source = 'STAGE_ASSESSMENT'", Integer.class, userId));
    }

    @Test
    void completedAssessmentKeepsQuestionAndKnowledgePointSnapshotsPrivateToItsOwner() {
        Long ownerId = insertUser(USERNAME);
        Long otherUserId = insertUser(USERNAME + "-other");
        jdbcTemplate.update("INSERT INTO user_course (user_id,course_id) VALUES (?,1)", ownerId);
        jdbcTemplate.update("INSERT INTO user_course (user_id,course_id) VALUES (?,1)", otherUserId);
        Long knowledgePointId = insertKnowledgePoint(1L, "REVIEWED");
        Long questionId = insertQuestion(1L, "快照隔离题", "SINGLE_CHOICE", "A");
        jdbcTemplate.update("INSERT INTO question_knowledge_point (question_id,knowledge_point_id) VALUES (?,?)",
                questionId, knowledgePointId);
        CourseStageAssessmentVO started = service.start(ownerId, 1L, scopedRequest(knowledgePointId, 1));
        jdbcTemplate.update("UPDATE question SET content = '测试限定知识点快照隔离题-已变更' WHERE id = ?", questionId);
        jdbcTemplate.update("UPDATE knowledge_point SET name = '测试限定知识点-已变更' WHERE id = ?", knowledgePointId);
        jdbcTemplate.update("UPDATE question_option SET is_correct = CASE option_label "
                + "WHEN 'A' THEN 0 ELSE 1 END WHERE question_id = ?", questionId);

        CourseStageAssessmentVO completed = service.submit(started.getId(), ownerId, submission(started, "A"));

        assertEquals(1, completed.getCorrectCount());
        assertEquals("测试限定知识点快照隔离题", completed.getQuestions().get(0).getContent());
        assertEquals("测试限定知识点", completed.getTargetKnowledgePointName());
        assertEquals("测试限定知识点", completed.getKnowledgePointSummary().get(0).getName());
        assertThrows(BusinessException.class, () -> service.getCompleted(started.getId(), otherUserId));
        assertEquals(0, service.listCompleted(otherUserId, 1L, 1, 10).getTotal());
    }

    private MySqlRaceSupport.StatementObserver holdFirstAssessmentLock(
            CountDownLatch firstLockHeld, CountDownLatch releaseFirst, AtomicLong connectionId) {
        return new MySqlRaceSupport.StatementObserver() {
            @Override
            public void after(String statement, java.sql.Connection connection) throws Exception {
                if (statement.endsWith("CourseStageAssessmentMapper.selectOwnedForUpdate")) {
                    connectionId.set(MySqlRaceSupport.connectionId(connection));
                    firstLockHeld.countDown();
                    MySqlRaceSupport.awaitLatch(releaseFirst);
                }
            }
        };
    }

    private MySqlRaceSupport.StatementObserver observeContendingAssessmentLock(
            CountDownLatch contenderRequestedLock, AtomicLong connectionId) {
        return new MySqlRaceSupport.StatementObserver() {
            @Override
            public void before(String statement, java.sql.Connection connection) throws Exception {
                if (statement.endsWith("CourseStageAssessmentMapper.selectOwnedForUpdate")) {
                    connectionId.set(MySqlRaceSupport.connectionId(connection));
                    contenderRequestedLock.countDown();
                }
            }
        };
    }

    private Long insertUser(String username) {
        jdbcTemplate.update("INSERT INTO user (username,password,role,status,deleted) VALUES (?,'test','USER',1,0)",
                username);
        return jdbcTemplate.queryForObject("SELECT id FROM user WHERE username = ?", Long.class, username);
    }

    private CourseStageAssessmentCreateRequest scopedRequest(Long knowledgePointId, int questionCount) {
        CourseStageAssessmentCreateRequest request = new CourseStageAssessmentCreateRequest();
        request.setKnowledgePointId(knowledgePointId);
        request.setQuestionCount(questionCount);
        return request;
    }

    private CourseStageAssessmentSubmitRequest submission(CourseStageAssessmentVO assessment, String answerValue) {
        CourseStageAssessmentSubmitRequest request = new CourseStageAssessmentSubmitRequest();
        request.setAnswers(assessment.getQuestions().stream()
                .map(question -> answer(question.getId(), answerValue)).toList());
        return request;
    }

    private String oppositeAnswer(String answer) {
        return "A".equals(answer) ? "B" : "A";
    }

    private String reviewState(Long userId, Long questionId) {
        return jdbcTemplate.queryForObject("SELECT CONCAT(ease_factor, '|', interval_days, '|', repetitions, '|', "
                        + "total_reviews) FROM question_review_schedule WHERE user_id = ? AND question_id = ?",
                String.class, userId, questionId);
    }

    private String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    private int countRows(java.sql.Connection connection, String sql, Long parameter) throws Exception {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, parameter);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private Long insertKnowledgePoint(Long courseId, String reviewStatus) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO knowledge_point "
                            + "(name, description, course_id, parent_id, content_review_status, sort_order, deleted) "
                            + "VALUES ('测试限定知识点', '集成测试用', ?, 0, ?, 99, 0)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, courseId);
            statement.setString(2, reviewStatus);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    private Long insertQuestion(Long courseId, String content, String questionType, String correctLabel) {
        boolean isTrueFalse = "TRUE_FALSE".equals(questionType);
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO question "
                            + "(content, question_type, course_id, difficulty, analysis, tags, score, status, create_by, deleted) "
                            + "VALUES (?, ?, ?, 2, '测试解析', '集成测试', 2, 1, 1, 0)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, "测试限定知识点" + content);
            statement.setString(2, questionType);
            statement.setLong(3, courseId);
            return statement;
        }, keys);
        Long questionId = keys.getKey().longValue();
        jdbcTemplate.update("INSERT INTO question_option "
                        + "(question_id, content, option_label, is_correct, sort_order) VALUES (?, ?, 'A', ?, 1)",
                questionId, isTrueFalse ? "正确" : "正确选项", "A".equals(correctLabel) ? 1 : 0);
        jdbcTemplate.update("INSERT INTO question_option "
                        + "(question_id, content, option_label, is_correct, sort_order) VALUES (?, ?, 'B', ?, 2)",
                questionId, isTrueFalse ? "错误" : "干扰选项", "B".equals(correctLabel) ? 1 : 0);
        return questionId;
    }

    private CourseStageAssessmentSubmitRequest.Answer answer(Long id, String value) {
        CourseStageAssessmentSubmitRequest.Answer answer = new CourseStageAssessmentSubmitRequest.Answer();
        answer.setAssessmentQuestionId(id);
        answer.setUserAnswer(value);
        return answer;
    }

    @TestConfiguration
    static class RaceConfiguration {
        @Bean
        MySqlRaceSupport mySqlRaceSupport(Environment environment) {
            return new MySqlRaceSupport(environment);
        }
    }
}
