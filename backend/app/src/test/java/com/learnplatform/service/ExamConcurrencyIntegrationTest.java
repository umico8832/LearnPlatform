package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.exception.ExamTimedOutException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.ExamRecordVO;
import com.learnplatform.dto.ExamSubmitRequest;
import com.learnplatform.mapper.ExamPaperMapper;
import com.learnplatform.mapper.ExamRecordMapper;
import com.learnplatform.support.MySqlRaceSupport;
import com.learnplatform.support.MySqlRaceSupport.StatementObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.learnplatform.support.MySqlRaceSupport.awaitLatch;
import static com.learnplatform.support.MySqlRaceSupport.connectionId;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
@Import(ExamConcurrencyIntegrationTest.Configuration.class)
class ExamConcurrencyIntegrationTest extends IntegrationTestBase {
    private static final String RECORD_LOCK = "ExamRecordMapper.selectByIdForUpdate";
    private static final Instant START = Instant.parse("2026-09-08T02:00:00Z");

    @Autowired ExamService service;
    @Autowired ExamPaperViewService papers;
    @Autowired JdbcTemplate jdbc;
    @Autowired MySqlRaceSupport race;
    @Autowired MutableExamClock clock;

    private ExamConcurrencyFixture fixture;

    @BeforeEach
    void setUp() {
        clock.now = START;
        fixture = new ExamConcurrencyFixture(jdbc);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void simultaneousStartsReuseOneCommittedActiveRecord(boolean expiredPredecessor) throws Exception {
        Long oldId = expiredPredecessor ? start().getId() : null;
        if (expiredPredecessor) {
            clock.now = START.plusSeconds(3601);
        }
        RaceResults results = expiredPredecessor
                ? startWhileExpiringExistingRecord()
                : startFromTwoSimultaneousInsertAttempts();
        ExamRecordVO firstResult = (ExamRecordVO) results.first();
        ExamRecordVO secondResult = (ExamRecordVO) results.second();
        assertEquals(firstResult.getId(), secondResult.getId());
        assertEquals(0, firstResult.getStatus());
        assertEquals(0, secondResult.getStatus());
        assertEquals(1, count("SELECT COUNT(*) FROM exam_record WHERE user_id = ? AND status = 0", fixture.userId));
        assertEquals(expiredPredecessor ? 2 : 1,
                count("SELECT COUNT(*) FROM exam_record WHERE user_id = ?", fixture.userId));
        if (expiredPredecessor) {
            var old = jdbc.queryForMap("SELECT status, active_exam_key FROM exam_record WHERE id = ?", oldId);
            assertEquals(2, ((Number) old.get("status")).intValue());
            assertNull(old.get("active_exam_key"));
            assertNotEquals(oldId, firstResult.getId());
        }
    }

    private RaceResults startFromTwoSimultaneousInsertAttempts() throws Exception {
        CyclicBarrier bothRead = new CyclicBarrier(2);
        CyclicBarrier bothInsert = new CyclicBarrier(2);
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        StatementObserver observer = new StatementObserver() {
            @Override
            public void before(String statement, Connection connection) throws Exception {
                if (statement.endsWith("ExamRecordMapper.insert")) {
                    bothInsert.await(15, TimeUnit.SECONDS);
                }
            }

            @Override
            public void after(String statement, Connection connection) throws Exception {
                if (statement.endsWith("ExamRecordMapper.selectByActiveExamKey")) {
                    bothRead.await(15, TimeUnit.SECONDS);
                }
                if (statement.endsWith("ExamRecordMapper.insert")) {
                    inserted.countDown();
                    awaitLatch(release);
                }
            }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> race.observe(observer, this::start));
            var second = executor.submit(() -> race.observe(observer, this::start));
            try {
                awaitLatch(inserted);
                assertFalse(first.isDone());
                assertFalse(second.isDone());
            } finally {
                release.countDown();
            }
            return new RaceResults(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        }
    }

    private RaceResults startWhileExpiringExistingRecord() throws Exception {
        CountDownLatch firstLockHeld = new CountDownLatch(1);
        CountDownLatch contenderRequestedLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicLong firstConnection = new AtomicLong();
        AtomicLong contenderConnection = new AtomicLong();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> race.observe(new StatementObserver() {
                @Override
                public void after(String statement, Connection connection) throws Exception {
                    if (statement.endsWith(RECORD_LOCK)) {
                        firstConnection.set(connectionId(connection));
                        firstLockHeld.countDown();
                        awaitLatch(releaseFirst);
                    }
                }
            }, this::start));
            try {
                awaitLatch(firstLockHeld);
                var second = executor.submit(() -> race.observe(new StatementObserver() {
                    @Override
                    public void before(String statement, Connection connection) throws Exception {
                        if (statement.endsWith(RECORD_LOCK)) {
                            contenderConnection.set(connectionId(connection));
                            contenderRequestedLock.countDown();
                        }
                    }
                }, this::start));
                awaitLatch(contenderRequestedLock);
                race.awaitDatabaseLock(firstConnection.get(), contenderConnection.get());
                releaseFirst.countDown();
                return new RaceResults(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            } finally {
                releaseFirst.countDown();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"A", "B"})
    void simultaneousDifferentAnswersKeepOnlyTheLockWinnersResult(String winningAnswer) throws Exception {
        long recordId = start().getId();
        fixture.seedLearningState();
        ExamSubmitRequest winner = fixture.request(recordId, winningAnswer, winningAnswer);
        String losingAnswer = "A".equals(winningAnswer) ? "B" : "A";
        var results = compete(RECORD_LOCK,
                () -> service.submitExam(winner, fixture.userId),
                () -> assertThrows(BusinessException.class, () -> service.submitExam(
                        fixture.request(recordId, losingAnswer, losingAnswer), fixture.userId)), () -> { });

        ExamRecordVO result = (ExamRecordVO) results.first();
        assertEquals("考试已结束", ((BusinessException) results.second()).getMessage());
        assertEquals("A".equals(winningAnswer) ? 20 : 0, result.getScore());
        assertEquals(2, result.getAnswers().size());
        assertTrue(result.getAnswers().stream().allMatch(answer -> winningAnswer.equals(answer.getUserAnswer())));
        assertCommittedResult(recordId, winningAnswer, winningAnswer);
        var committed = fixture.facts(recordId);
        assertThrows(BusinessException.class, () -> service.submitExam(winner, fixture.userId));
        assertEquals(committed, fixture.facts(recordId));
        assertEquals(result.getScore(), service.getExamResult(recordId, fixture.userId).getScore());
    }

    @Test
    void manualSubmissionCommittedAcrossDeadlineCannotBeOverwrittenByTimeout() throws Exception {
        long recordId = start().getId();
        var results = compete("ExamRecordMapper.updateById",
                () -> service.submitExam(fixture.request(recordId, "A", "A"), fixture.userId),
                () -> service.getExamSession(recordId, fixture.userId),
                () -> clock.now = START.plusSeconds(3601));

        assertEquals(1, ((ExamRecordVO) results.first()).getStatus());
        assertEquals(1, ((ExamRecordVO) results.second()).getStatus());
        assertEquals(20, service.getExamResult(recordId, fixture.userId).getScore());
        assertEquals(2, count("SELECT COUNT(*) FROM exam_answer WHERE exam_record_id = ?", recordId));
    }

    @Test
    void timeoutWinningTheLockRejectsConcurrentManualSubmissionWithoutLearningWrites() throws Exception {
        long recordId = start().getId();
        clock.now = START.plusSeconds(3600);
        var results = compete(RECORD_LOCK,
                () -> service.getExamSession(recordId, fixture.userId),
                () -> assertThrows(BusinessException.class, () -> service.submitExam(
                        fixture.request(recordId, "A", "A"), fixture.userId)), () -> { });

        assertEquals(2, ((ExamRecordVO) results.first()).getStatus());
        assertEquals("考试已结束", ((BusinessException) results.second()).getMessage());
        assertTimedOutWithoutAnswers(recordId);
    }

    @Test
    void lateSubmissionExceptionCommitsTimeoutBeforeTheWaitingSessionReadsIt() throws Exception {
        long recordId = start().getId();
        clock.now = START.plusSeconds(3600);
        var results = compete(RECORD_LOCK,
                () -> assertThrows(ExamTimedOutException.class, () -> service.submitExam(
                        fixture.request(recordId, "A", "A"), fixture.userId)),
                () -> service.getExamSession(recordId, fixture.userId), () -> { });

        assertInstanceOf(ExamTimedOutException.class, results.first());
        assertEquals(2, ((ExamRecordVO) results.second()).getStatus());
        assertTimedOutWithoutAnswers(recordId);
    }

    @Test
    void waitingRetrySeesActiveRecordAfterTheFirstSubmissionRollsBack() throws Exception {
        long recordId = start().getId();
        fixture.seedLearningState();
        var before = fixture.facts(recordId);
        var results = compete("ExamRecordMapper.updateById",
                () -> assertThrows(RuntimeException.class, () -> service.submitExam(
                        fixture.request(recordId, "B", "B"), fixture.userId)),
                () -> service.submitExam(fixture.request(recordId, "A", "A"), fixture.userId),
                () -> assertEquals(before, fixture.facts(recordId)),
                () -> { throw new IllegalStateException("injected before commit"); });

        assertInstanceOf(RuntimeException.class, results.first());
        assertEquals(20, ((ExamRecordVO) results.second()).getScore());
        assertCommittedResult(recordId, "A", "A");
    }

    @ParameterizedTest
    @CsvSource({
            "ExamAnswerMapper.insert, 2, A",
            "CourseLearningEventMapper.insert, 2, B",
            "WrongQuestionMapper.insert, 1, B",
            "ExamRecordMapper.updateById, 1, A",
            "ExamRecordMapper.updateById, 1, B"
    })
    void failureAfterRealWritesRollsBackAllFactsAndRetryAccumulatesOnce(
            String failureStatement, int occurrence, String firstAnswer) throws Exception {
        long recordId = start().getId();
        fixture.seedLearningState();
        var before = fixture.facts(recordId);
        AtomicInteger seen = new AtomicInteger();
        AtomicBoolean injected = new AtomicBoolean();
        StatementObserver failure = new StatementObserver() {
            @Override
            public void after(String statement, Connection connection) throws Exception {
                if (statement.endsWith(failureStatement) && seen.incrementAndGet() == occurrence) {
                    try (var query = connection.prepareStatement(
                            "SELECT COUNT(*) FROM exam_answer WHERE exam_record_id = ?")) {
                        query.setLong(1, recordId);
                        try (var rows = query.executeQuery()) {
                            rows.next();
                            assertEquals(2, rows.getInt(1), "Failure must occur after both real answer inserts");
                        }
                    }
                    injected.set(true);
                    throw new IllegalStateException("injected after exam write");
                }
            }
        };
        ExamSubmitRequest request = fixture.request(recordId, firstAnswer, "B");
        assertThrows(RuntimeException.class, () -> race.observe(failure,
                () -> service.submitExam(request, fixture.userId)));
        assertTrue(injected.get());
        assertEquals(before, fixture.facts(recordId), "A separate connection must see the exact pre-submit state");

        service.submitExam(request, fixture.userId);
        assertCommittedResult(recordId, firstAnswer, "B");
        var committed = fixture.facts(recordId);
        assertThrows(BusinessException.class, () -> service.submitExam(request, fixture.userId));
        assertEquals(committed, fixture.facts(recordId));
    }

    @Test
    void ownerAndAnswerBoundariesHoldOnCommittedPrivateExam() {
        long recordId = start().getId();
        assertNull(service.getExamSession(recordId, fixture.userId).getAnswers());
        assertThrows(BusinessException.class, () -> service.getExamResult(recordId, fixture.userId));
        var detail = papers.getAccessiblePublishedById(fixture.paperId, fixture.userId);
        detail.getQuestions().forEach(question -> {
            question.getOptions().forEach(option -> assertNull(option.getIsCorrect()));
        });
        var before = fixture.facts(recordId);
        assertThrows(BusinessException.class, () -> service.startExam(fixture.paperId, fixture.otherUserId));
        assertThrows(BusinessException.class, () -> papers.getAccessiblePublishedById(fixture.paperId, fixture.otherUserId));
        assertThrows(BusinessException.class, () -> service.getExamSession(recordId, fixture.otherUserId));
        BusinessException forbidden = assertThrows(BusinessException.class, () -> service.submitExam(
                fixture.request(recordId, "A", "A"), fixture.otherUserId));
        assertEquals(ResultCode.FORBIDDEN.getCode(), forbidden.getCode());
        ExamSubmitRequest foreignQuestion = fixture.request(recordId, "A", "B");
        foreignQuestion.getAnswers().get(1).setQuestionId(Long.MAX_VALUE);
        assertThrows(BusinessException.class, () -> service.submitExam(foreignQuestion, fixture.userId));
        assertEquals(before, fixture.facts(recordId));

        ExamRecordVO result = service.submitExam(fixture.request(recordId, "A", "B"), fixture.userId);
        assertEquals(10, result.getScore());
        assertThrows(BusinessException.class, () -> service.getExamResult(recordId, fixture.otherUserId));
    }

    @Test
    void duplicateQuestionWithNullFirstAnswerIsRejectedWithoutWritingFacts() {
        long recordId = start().getId();
        var before = fixture.facts(recordId);
        ExamSubmitRequest request = fixture.request(recordId, null, "B");
        request.setAnswers(List.of(ExamConcurrencyFixture.answer(fixture.firstQuestion, null),
                ExamConcurrencyFixture.answer(fixture.firstQuestion, "A")));
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.submitExam(request, fixture.userId));
        assertEquals(ResultCode.VALIDATION_ERROR.getCode(), exception.getCode());
        assertEquals(before, fixture.facts(recordId));
    }

    private ExamRecordVO start() {
        return service.startExam(fixture.paperId, fixture.userId);
    }

    private void assertCommittedResult(long recordId, String firstAnswer, String secondAnswer) {
        ExamRecordVO result = service.getExamResult(recordId, fixture.userId);
        assertEquals(1, result.getStatus());
        assertEquals(("A".equals(firstAnswer) ? 10 : 0) + ("A".equals(secondAnswer) ? 10 : 0), result.getScore());
        assertEquals(20, result.getTotalScore());
        assertNull(jdbc.queryForMap("SELECT active_exam_key FROM exam_record WHERE id = ?", recordId).get("active_exam_key"));
        assertEquals(2, count("SELECT COUNT(*) FROM exam_answer WHERE exam_record_id = ?", recordId));
        assertEquals(2, count("SELECT COUNT(*) FROM course_learning_event WHERE user_id = ?", fixture.userId));
        assertEquals(0, count("""
                SELECT COUNT(*) FROM course_learning_event e
                LEFT JOIN exam_answer a ON e.source_record_id = a.id
                WHERE e.user_id = ? AND (a.id IS NULL OR e.event_source <> 'EXAM'
                    OR e.event_type <> 'EXAM_ANSWERED' OR e.subject_id <> a.question_id
                    OR e.idempotency_key <> CONCAT('EXAM:', a.id)
                    OR JSON_UNQUOTE(JSON_EXTRACT(e.payload_json, '$.isCorrect')) <> IF(a.is_correct = 1, 'true', 'false'))
                """, fixture.userId));
        var wrong = jdbc.queryForMap("SELECT wrong_count, deleted FROM wrong_question WHERE user_id = ? AND question_id = ?",
                fixture.userId, fixture.firstQuestion);
        assertEquals("A".equals(firstAnswer) ? 2 : 3, ((Number) wrong.get("wrong_count")).intValue());
        assertEquals("A".equals(firstAnswer) ? 1 : 0, ((Number) wrong.get("deleted")).intValue());
        assertEquals("A".equals(secondAnswer) ? 0 : 1, count(
                "SELECT COUNT(*) FROM wrong_question WHERE user_id = ? AND question_id = ? AND wrong_count = 1 AND deleted = 0",
                fixture.userId, fixture.secondQuestion));
        assertEquals(3, count("SELECT total_reviews FROM question_review_schedule WHERE user_id = ?", fixture.userId));
    }

    private void assertTimedOutWithoutAnswers(long recordId) {
        var record = jdbc.queryForMap("SELECT status, score, active_exam_key FROM exam_record WHERE id = ?", recordId);
        assertEquals(2, ((Number) record.get("status")).intValue());
        assertEquals(0, ((Number) record.get("score")).intValue());
        assertNull(record.get("active_exam_key"));
        assertEquals(0, count("SELECT COUNT(*) FROM exam_answer WHERE exam_record_id = ?", recordId));
        for (String table : List.of("wrong_question", "question_review_schedule", "course_learning_event")) {
            assertEquals(0, count("SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", fixture.userId));
        }
        assertThrows(BusinessException.class, () -> service.getExamResult(recordId, fixture.userId));
    }

    private int count(String sql, Object... parameters) {
        return jdbc.queryForObject(sql, Integer.class, parameters);
    }

    private RaceResults compete(String pauseAfter, Callable<?> first, Callable<?> second,
                                Runnable whilePaused) throws Exception {
        return compete(pauseAfter, first, second, whilePaused, () -> { });
    }

    private RaceResults compete(String pauseAfter, Callable<?> first, Callable<?> second,
                                Runnable whilePaused, Runnable afterRelease) throws Exception {
        CountDownLatch holdingLock = new CountDownLatch(1);
        CountDownLatch requestingLock = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicLong blocker = new AtomicLong();
        AtomicLong requester = new AtomicLong();
        StatementObserver holder = new StatementObserver() {
            @Override
            public void after(String statement, Connection connection) throws Exception {
                if (statement.endsWith(pauseAfter)) {
                    blocker.set(connectionId(connection));
                    holdingLock.countDown();
                    awaitLatch(release);
                    afterRelease.run();
                }
            }
        };
        StatementObserver contender = new StatementObserver() {
            @Override
            public void before(String statement, Connection connection) throws Exception {
                if (statement.endsWith(RECORD_LOCK)) {
                    requester.set(connectionId(connection));
                    requestingLock.countDown();
                }
            }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var winner = executor.submit(() -> race.observe(holder, first));
            try {
                awaitLatch(holdingLock);
                whilePaused.run();
                var loser = executor.submit(() -> race.observe(contender, second));
                awaitLatch(requestingLock);
                assertNotEquals(blocker.get(), requester.get());
                race.awaitDatabaseLock(blocker.get(), requester.get());
                release.countDown();
                return new RaceResults(winner.get(30, TimeUnit.SECONDS), loser.get(30, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
        }
    }

    private record RaceResults(Object first, Object second) { }

    static final class MutableExamClock extends Clock {
        volatile Instant now = START;

        @Override public ZoneId getZone() { return ExamSessionService.EXAM_ZONE; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Configuration {
        @Bean MySqlRaceSupport examRaceSupport(Environment environment) { return new MySqlRaceSupport(environment); }
        @Bean MutableExamClock examClock() { return new MutableExamClock(); }

        @Bean
        @Primary
        ExamSessionService timedExamSessions(ExamRecordMapper records, ExamPaperMapper papers,
                                             ExamRecordViewService views, MutableExamClock clock) {
            return new ExamSessionService(records, papers, views, clock);
        }

        @Bean
        @Primary
        ExamSubmissionService timedExamSubmissions(ExamRecordMapper records, ExamPaperMapper papers,
                                                   ExamAnswerSubmissionService answers, CacheEvictService cache,
                                                   MutableExamClock clock) {
            return new ExamSubmissionService(records, papers, answers, cache, clock);
        }
    }
}
