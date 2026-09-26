package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.service.tutor.TutorAgentExecutionState;
import com.learnplatform.service.tutor.TutorAgentReply;
import com.learnplatform.dto.TutorAgentActionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import com.learnplatform.security.CustomUserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
class TutorAgentRunStateIntegrationTest extends IntegrationTestBase {
    private static final long USER = 989101L;
    private static final long COURSE = 989110L;
    private static final long CONTENT = 989120L;
    @Autowired private TutorAgentRunStateService states;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    private String sessionKey;

    @BeforeEach void prepare() {
        cleanUp();
        sessionKey = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO user (id,username,password,role,status,deleted) VALUES (?,?,'test','USER',1,0)",
                USER, "tutor-agent-run-state");
        jdbc.update("INSERT INTO course (id,name,status,deleted) VALUES (?,'Agent run 状态测试',1,0)", COURSE);
        jdbc.update("INSERT INTO user_course (user_id,course_id) VALUES (?,?)", USER, COURSE);
        jdbc.update("""
                INSERT INTO tutor_content (id,knowledge_point_id,content_key,content_version,review_status,title,lesson_json,check_json)
                VALUES (?,989130,'agent-run-state',1,'REVIEWED','Agent run 状态','{}',
                    '{"prompt":"检查","options":[{"id":"A","text":"A"}],"correctOptionId":"A"}')
                """, CONTENT);
        jdbc.update("""
                INSERT INTO tutor_session
                (session_key, user_id, course_id, knowledge_point_id, tutor_content_id)
                VALUES (?, ?, ?, 989130, ?)
                """, sessionKey, USER, COURSE, CONTENT);
    }

    @AfterEach void cleanUpAfter() { cleanUp(); }

    @Test void activeExecutionRejectsConcurrentQuestionsAndPersistsOnePair() {
        var execution = states.begin(USER, COURSE, sessionKey);
        assertNotNull(execution.executionKey());
        assertEquals("RUNNING", viewStatus(execution));
        assertThrows(BusinessException.class, () -> resume(execution));
        var result = states.complete(execution, "问题", new TutorAgentReply("回答", List.of()));
        assertEquals("WAITING_USER", result.getStatus());
        assertEquals(2, result.getMessages().size());
        assertThrows(BusinessException.class, () -> states.complete(execution, "重复", new TutorAgentReply("重复", List.of())));
        assertEquals(2, messageCount(execution));
    }

    @Test void expiredExecutionCanBeReclaimedWithoutAcceptingOldSuccessOrFailure() {
        var old = states.begin(USER, COURSE, sessionKey);
        expire(old);
        assertEquals("FAILED", viewStatus(old));
        var current = resume(old);
        assertEquals(old.runId(), current.runId());
        assertNotEquals(old.executionKey(), current.executionKey());
        assertThrows(BusinessException.class, () -> states.complete(old, "旧问题", new TutorAgentReply("旧回答", List.of())));
        states.fail(old);
        assertEquals("RUNNING", viewStatus(current));
        assertEquals(0, messageCount(current));
        assertEquals("新回答", states.complete(current, "新问题", new TutorAgentReply("新回答", List.of())).getMessages().get(1).getContent());
    }

    @Test void expiryRejectsCompletionEvenBeforeAnotherWorkerClaimsTheRun() {
        var execution = states.begin(USER, COURSE, sessionKey);
        expire(execution);
        assertThrows(BusinessException.class, () -> states.complete(execution, "问题", new TutorAgentReply("回答", List.of())));
        assertEquals(0, messageCount(execution));
    }

    @Test void failedExecutionResumesWithHistoryAndAFreshIdentity() {
        var first = states.begin(USER, COURSE, sessionKey);
        states.complete(first, "第一问", new TutorAgentReply("第一答", List.of()));
        var failed = resume(first);
        states.fail(failed);
        var retried = resume(failed);
        assertNotEquals(failed.executionKey(), retried.executionKey());
        assertEquals(2, retried.history().size());
        assertEquals(3, retried.nextSequence());
        states.fail(failed);
        assertEquals(4, states.complete(retried, "第二问", new TutorAgentReply("第二答", List.of())).getMessages().size());
    }

    @Test void onlyOneConcurrentRetryCanClaimAnExpiredRun() throws Exception {
        var old = states.begin(USER, COURSE, sessionKey);
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
        var execution = states.begin(USER, COURSE, sessionKey);
        assertThrows(RuntimeException.class, () -> states.complete(execution, "问题", new TutorAgentReply(null, List.of())));
        assertEquals("RUNNING", viewStatus(execution));
        assertEquals(0, messageCount(execution));
        var result = states.complete(execution, "问题", new TutorAgentReply("回答", List.of()));
        assertEquals(1, result.getMessages().getFirst().getSequence());
    }

    @Test void checkActionSurvivesRecoveryAndSharesTheCompletedMessageTransaction() throws Exception {
        var execution = states.begin(USER, COURSE, sessionKey);
        var actions = List.of(new TutorAgentActionVO("CHECK"));
        states.complete(execution, "检查理解", new TutorAgentReply("请自己作答", actions));
        var restored = states.get(USER, COURSE, sessionKey, execution.runId().toString());
        assertEquals(List.of(), restored.getMessages().getFirst().getActions());
        assertEquals(actions, restored.getMessages().get(1).getActions());
        assertEquals(actions, resume(execution).history().get(1).actions());
        assertEquals(2, messageCount(execution));
        mvc.perform(get("/api/my-courses/{courseId}/tutor-sessions/{sessionKey}/agent-runs/{runKey}",
                        COURSE, sessionKey, execution.runId()).with(authentication(learner(USER))))
                .andExpect(jsonPath("$.data.messages[1].actions[0].type").value("CHECK"))
                .andExpect(jsonPath("$.data.messages[0].actions").isEmpty());
    }

    @Test void recoveryStillRequiresTheOriginalOwnerAndSession() {
        var execution = states.begin(USER, COURSE, sessionKey);
        expire(execution);
        assertThrows(BusinessException.class,
                () -> states.resume(USER + 1, COURSE, sessionKey, execution.runId().toString()));
        assertThrows(BusinessException.class,
                () -> states.resume(USER, COURSE + 1, sessionKey, execution.runId().toString()));
        assertEquals("FAILED", viewStatus(execution));
        assertNotNull(resume(execution));
    }

    @Test void preLeaseRunsCanBeRecoveredWithoutInventingAnOldExecutionIdentity() {
        var execution = states.begin(USER, COURSE, sessionKey);
        jdbc.update("UPDATE tutor_agent_run SET execution_key=NULL, lease_until=NULL WHERE id=?", execution.id());
        assertEquals("FAILED", viewStatus(execution));
        var recovered = resume(execution);
        assertFalse(recovered.executionKey().isBlank());
        assertEquals("RUNNING", viewStatus(recovered));
    }

    @Test void latestEndpointRecoversTheNewestRunWithoutAClientSideIdentifier() throws Exception {
        var older = states.begin(USER, COURSE, sessionKey);
        states.complete(older, "旧问题", new TutorAgentReply("旧回答", List.of()));
        var latest = states.begin(USER, COURSE, sessionKey);
        mvc.perform(get("/api/my-courses/{courseId}/tutor-sessions/{sessionKey}/agent-runs/latest", COURSE, sessionKey)
                        .with(authentication(learner(USER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.runKey").value(latest.runId().toString()))
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.messages").isEmpty())
                .andExpect(jsonPath("$.data.executionKey").doesNotExist());
    }

    @Test void latestEndpointReturnsAnEmptyResultForAnOwnedSessionWithoutRuns() throws Exception {
        mvc.perform(get("/api/my-courses/{courseId}/tutor-sessions/{sessionKey}/agent-runs/latest", COURSE, sessionKey)
                        .with(authentication(learner(USER))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test void latestEndpointRejectsOtherOwnersCoursesAndAnonymousAccess() throws Exception {
        states.begin(USER, COURSE, sessionKey);
        String endpoint = "/api/my-courses/" + COURSE + "/tutor-sessions/{sessionKey}/agent-runs/latest";
        mvc.perform(get(endpoint, sessionKey)).andExpect(status().isUnauthorized());
        mvc.perform(get(endpoint, sessionKey).with(authentication(learner(USER + 1))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1004));
        mvc.perform(get("/api/my-courses/{courseId}/tutor-sessions/{sessionKey}/agent-runs/latest", COURSE + 1, sessionKey)
                        .with(authentication(learner(USER))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1004));
    }

    @Test void removedCourseMembershipOrWithdrawnContentBlocksReadsClaimsAndLateCompletion() {
        var execution = states.begin(USER, COURSE, sessionKey);
        jdbc.update("DELETE FROM user_course WHERE user_id=? AND course_id=?", USER, COURSE);
        assertThrows(BusinessException.class, () -> states.get(USER, COURSE, sessionKey, execution.runId().toString()));
        assertThrows(BusinessException.class, () -> resume(execution));
        assertThrows(BusinessException.class,
                () -> states.complete(execution, "不应保存", new TutorAgentReply("不应保存", List.of())));
        assertEquals(0, messageCount(execution));

        jdbc.update("INSERT INTO user_course (user_id,course_id) VALUES (?,?)", USER, COURSE);
        jdbc.update("UPDATE tutor_content SET review_status='REVIEW_PENDING' WHERE id=?", CONTENT);
        assertThrows(BusinessException.class, () -> states.get(USER, COURSE, sessionKey, execution.runId().toString()));
        assertThrows(BusinessException.class, () -> states.latest(USER, COURSE, sessionKey));
    }

    private UsernamePasswordAuthenticationToken learner(Long userId) {
        return new UsernamePasswordAuthenticationToken(new CustomUserDetails(userId, "learner", "USER"), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
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
        return states.resume(USER, COURSE, sessionKey, execution.runId().toString());
    }

    private String viewStatus(TutorAgentExecutionState execution) {
        return states.get(USER, COURSE, sessionKey, execution.runId().toString()).getStatus();
    }

    private void expire(TutorAgentExecutionState execution) {
        jdbc.update("UPDATE tutor_agent_run SET lease_until=DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 SECOND) WHERE id=?",
                execution.id());
    }

    private int messageCount(TutorAgentExecutionState execution) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tutor_agent_message WHERE run_id=?", Integer.class, execution.id());
    }

    private void cleanUp() {
        if (jdbc == null) return;
        jdbc.update("DELETE FROM tutor_agent_message WHERE run_id IN (SELECT id FROM tutor_agent_run WHERE tutor_session_id IN (SELECT id FROM tutor_session WHERE user_id=?))", USER);
        jdbc.update("DELETE FROM tutor_agent_run WHERE tutor_session_id IN (SELECT id FROM tutor_session WHERE user_id=?)", USER);
        jdbc.update("DELETE FROM tutor_session WHERE user_id=?", USER);
        jdbc.update("DELETE FROM tutor_content WHERE id=?", CONTENT);
        jdbc.update("DELETE FROM user_course WHERE user_id=?", USER);
        jdbc.update("DELETE FROM course WHERE id=?", COURSE);
        jdbc.update("DELETE FROM user WHERE id=?", USER);
    }
}
