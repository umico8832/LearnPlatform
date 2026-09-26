package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.service.tutor.TutorAgentExecutionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
class TutorAgentRunStateIntegrationTest extends IntegrationTestBase {
    @Autowired private TutorAgentRunStateService states;
    @Autowired private JdbcTemplate jdbc;
    private String sessionKey;

    @BeforeEach void prepare() {
        sessionKey = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO tutor_session
                (session_key, user_id, course_id, knowledge_point_id, tutor_content_id)
                VALUES (?, 7, 10, 20, 30)
                """, sessionKey);
    }

    @Test void activeExecutionRejectsConcurrentQuestionsAndPersistsOnePair() {
        var execution = states.begin(7L, 10L, sessionKey);
        assertNotNull(execution.executionKey());
        assertEquals("RUNNING", viewStatus(execution));
        assertThrows(BusinessException.class, () -> resume(execution));
        var result = states.complete(execution, "问题", "回答");
        assertEquals("WAITING_USER", result.getStatus());
        assertEquals(2, result.getMessages().size());
        assertThrows(BusinessException.class, () -> states.complete(execution, "重复", "重复"));
        assertEquals(2, messageCount(execution));
    }

    @Test void expiredExecutionCanBeReclaimedWithoutAcceptingOldSuccessOrFailure() {
        var old = states.begin(7L, 10L, sessionKey);
        expire(old);
        assertEquals("FAILED", viewStatus(old));
        var current = resume(old);
        assertEquals(old.runId(), current.runId());
        assertNotEquals(old.executionKey(), current.executionKey());
        assertThrows(BusinessException.class, () -> states.complete(old, "旧问题", "旧回答"));
        states.fail(old);
        assertEquals("RUNNING", viewStatus(current));
        assertEquals(0, messageCount(current));
        assertEquals("新回答", states.complete(current, "新问题", "新回答").getMessages().get(1).getContent());
    }

    @Test void expiryRejectsCompletionEvenBeforeAnotherWorkerClaimsTheRun() {
        var execution = states.begin(7L, 10L, sessionKey);
        expire(execution);
        assertThrows(BusinessException.class, () -> states.complete(execution, "问题", "回答"));
        assertEquals(0, messageCount(execution));
    }

    @Test void failedExecutionResumesWithHistoryAndAFreshIdentity() {
        var first = states.begin(7L, 10L, sessionKey);
        states.complete(first, "第一问", "第一答");
        var failed = resume(first);
        states.fail(failed);
        var retried = resume(failed);
        assertNotEquals(failed.executionKey(), retried.executionKey());
        assertEquals(2, retried.history().size());
        assertEquals(3, retried.nextSequence());
        states.fail(failed);
        assertEquals(4, states.complete(retried, "第二问", "第二答").getMessages().size());
    }

    @Test void onlyOneConcurrentRetryCanClaimAnExpiredRun() throws Exception {
        var old = states.begin(7L, 10L, sessionKey);
        expire(old);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var first = CompletableFuture.supplyAsync(() -> tryResume(old, ready, start));
        var second = CompletableFuture.supplyAsync(() -> tryResume(old, ready, start));
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        assertEquals("RUNNING", viewStatus(old));
    }

    @Test void messageFailureRollsBackStatusSequenceAndBothMessages() {
        var execution = states.begin(7L, 10L, sessionKey);
        assertThrows(RuntimeException.class, () -> states.complete(execution, "问题", null));
        assertEquals("RUNNING", viewStatus(execution));
        assertEquals(0, messageCount(execution));
        var result = states.complete(execution, "问题", "回答");
        assertEquals(1, result.getMessages().getFirst().getSequence());
    }

    @Test void recoveryStillRequiresTheOriginalOwnerAndSession() {
        var execution = states.begin(7L, 10L, sessionKey);
        expire(execution);
        assertThrows(BusinessException.class,
                () -> states.resume(8L, 10L, sessionKey, execution.runId().toString()));
        assertThrows(BusinessException.class,
                () -> states.resume(7L, 11L, sessionKey, execution.runId().toString()));
        assertEquals("FAILED", viewStatus(execution));
        assertNotNull(resume(execution));
    }

    @Test void preLeaseRunsCanBeRecoveredWithoutInventingAnOldExecutionIdentity() {
        var execution = states.begin(7L, 10L, sessionKey);
        jdbc.update("UPDATE tutor_agent_run SET execution_key=NULL, lease_until=NULL WHERE id=?", execution.id());
        assertEquals("FAILED", viewStatus(execution));
        var recovered = resume(execution);
        assertFalse(recovered.executionKey().isBlank());
        assertEquals("RUNNING", viewStatus(recovered));
    }

    private boolean tryResume(TutorAgentExecutionState old, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Retry start timed out");
            resume(old);
            return true;
        } catch (BusinessException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private TutorAgentExecutionState resume(TutorAgentExecutionState execution) {
        return states.resume(7L, 10L, sessionKey, execution.runId().toString());
    }

    private String viewStatus(TutorAgentExecutionState execution) {
        return states.get(7L, 10L, sessionKey, execution.runId().toString()).getStatus();
    }

    private void expire(TutorAgentExecutionState execution) {
        jdbc.update("UPDATE tutor_agent_run SET lease_until=DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 SECOND) WHERE id=?",
                execution.id());
    }

    private int messageCount(TutorAgentExecutionState execution) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tutor_agent_message WHERE run_id=?", Integer.class, execution.id());
    }
}
