package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.ai.model.Cancellation;
import com.learnplatform.ai.model.ModelRequest;
import com.learnplatform.ai.model.ModelResult;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.TutorAgentMessageRequest;
import com.learnplatform.service.ai.AiProvider;
import com.learnplatform.service.tutor.TutorAgentExecutionState;
import com.learnplatform.service.tutor.TutorAgentReply;
import com.learnplatform.service.tutor.TutorAgentRuntime;
import com.learnplatform.service.tutor.TutorAgentToolExecutor;
import com.learnplatform.service.tutor.TutorMemoryService;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired private AiCallGovernanceService governance;
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
        assertDoesNotThrow(() -> states.requireActiveExecution(old));
        expire(old);
        assertThrows(BusinessException.class, () -> states.requireActiveExecution(old));
        assertEquals("FAILED", viewStatus(old));
        var current = resume(old);
        assertEquals(old.runId(), current.runId());
        assertNotEquals(old.executionKey(), current.executionKey());
        assertThrows(BusinessException.class, () -> states.requireActiveExecution(old));
        assertDoesNotThrow(() -> states.requireActiveExecution(current));
        assertThrows(BusinessException.class, () -> states.requireActiveExecution(new TutorAgentExecutionState(
                current.id(), current.runId(), current.executionKey(), current.nextSequence() + 2, current.history())));
        assertThrows(BusinessException.class, () -> states.complete(old, "旧问题", new TutorAgentReply("旧回答", List.of())));
        states.fail(old);
        assertEquals("RUNNING", viewStatus(current));
        assertEquals(0, messageCount(current));
        assertEquals("新回答", states.complete(current, "新问题", new TutorAgentReply("新回答", List.of())).getMessages().get(1).getContent());
        assertThrows(BusinessException.class, () -> states.requireActiveExecution(current));
    }

    @Test void expiredExecutionCannotRunToolsAfterAnotherWorkerClaimsIt() {
        AiProvider provider = provider();
        TutorAgentToolExecutor tools = mock(TutorAgentToolExecutor.class);
        TutorMemoryService memory = memory();
        TutorAgentService agent = agent(provider, tools, memory);
        AtomicReference<TutorAgentExecutionState> successor = new AtomicReference<>();

        when(provider.complete(any(), any())).thenAnswer(call -> {
            String runKey = states.latest(USER, COURSE, sessionKey).getRunKey();
            expireRun(runKey);
            successor.set(states.resume(USER, COURSE, sessionKey, runKey));
            return toolCall("read_tutor_lesson", "lesson");
        });

        assertThrows(BusinessException.class, () -> agent.start(USER, COURSE, sessionKey, request("读取本节")));

        verify(provider, times(1)).complete(any(), any(Cancellation.class));
        verify(tools, times(0)).execute(any(), any(), any(), any(ModelRequest.ToolCall.class));
        assertEquals(0, messageCount(successor.get()));
        assertEquals("RUNNING", viewStatus(successor.get()));
        assertEquals("FAILED", callOutcome(successor.get().runId()));
        assertEquals(5, callTokens(successor.get().runId()));
        assertEquals("新回答", states.complete(successor.get(), "重新提问",
                new TutorAgentReply("新回答", List.of())).getMessages().get(1).getContent());
    }

    @Test void expiryDuringFirstToolStopsLaterToolsAndTheNextModelRound() {
        AiProvider provider = provider();
        TutorAgentToolExecutor tools = mock(TutorAgentToolExecutor.class);
        TutorAgentService agent = agent(provider, tools, memory());
        AtomicReference<String> runKey = new AtomicReference<>();
        when(provider.complete(any(), any())).thenAnswer(call -> {
            runKey.set(states.latest(USER, COURSE, sessionKey).getRunKey());
            return new ModelResult(null, List.of(
                    new ModelRequest.ToolCall("lesson", "read_tutor_lesson", "{}"),
                    new ModelRequest.ToolCall("evidence", "read_learning_evidence", "{}")),
                    "test-model", "first", ModelResult.Finish.TOOL_CALLS, new ModelResult.Usage(3, 2, 5));
        });
        when(tools.execute(any(), any(), any(), any(ModelRequest.ToolCall.class))).thenAnswer(call -> {
            expireRun(runKey.get());
            return "{}";
        });

        assertThrows(BusinessException.class, () -> agent.start(USER, COURSE, sessionKey, request("读取本节")));

        verify(provider, times(1)).complete(any(), any(Cancellation.class));
        verify(tools, times(1)).execute(any(), any(), any(), any(ModelRequest.ToolCall.class));
        assertEquals(0, messageCount(UUID.fromString(runKey.get())));
        assertEquals("FAILED", runStatus(runKey.get()));
        assertEquals("SUCCEEDED", callOutcome(UUID.fromString(runKey.get())));
        assertEquals(5, callTokens(UUID.fromString(runKey.get())));
    }

    @Test void expiryRejectsCompletionEvenBeforeAnotherWorkerClaimsTheRun() {
        var execution = states.begin(USER, COURSE, sessionKey);
        expire(execution);
        assertThrows(BusinessException.class, () -> states.complete(execution, "问题", new TutorAgentReply("回答", List.of())));
        assertEquals(0, messageCount(execution));
    }

    @Test void activeExecutionRejectsAMissingLease() {
        var execution = states.begin(USER, COURSE, sessionKey);
        jdbc.update("UPDATE tutor_agent_run SET lease_until=NULL WHERE id=?", execution.id());
        assertThrows(BusinessException.class, () -> states.requireActiveExecution(execution));
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

    @Test void hintLevelAndCheckActionRecoverTogetherWithoutAdvancingOnFailure() throws Exception {
        var execution = states.begin(USER, COURSE, sessionKey);
        var hint = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue("{\"type\":\"HINT\",\"level\":1}", TutorAgentActionVO.class);
        var actions = List.of(new TutorAgentActionVO("CHECK"), hint);
        states.complete(execution, "提示一步", new TutorAgentReply("先回看概念", actions));
        var next = resume(execution);
        states.fail(next);
        var retried = resume(execution);
        assertEquals(actions, retried.history().get(1).actions());
        assertEquals(2, messageCount(execution));
        mvc.perform(get("/api/my-courses/{courseId}/tutor-sessions/{sessionKey}/agent-runs/{runKey}",
                        COURSE, sessionKey, execution.runId()).with(authentication(learner(USER))))
                .andExpect(jsonPath("$.data.messages[1].actions[0].type").value("CHECK"))
                .andExpect(jsonPath("$.data.messages[1].actions[0].level").doesNotExist())
                .andExpect(jsonPath("$.data.messages[1].actions[1].type").value("HINT"))
                .andExpect(jsonPath("$.data.messages[1].actions[1].level").value(1));
    }

    @Test void duplicateActionsRollBackTheEntireCompletedTurn() {
        var execution = states.begin(USER, COURSE, sessionKey);
        var check = new TutorAgentActionVO("CHECK");
        assertThrows(IllegalStateException.class, () -> states.complete(execution,
                "重复动作", new TutorAgentReply("不应保存", List.of(check, check))));
        assertEquals("RUNNING", viewStatus(execution));
        assertEquals(0, messageCount(execution));
    }

    @Test void answerSubmittedDuringHintGenerationPreventsSavingANewHint() throws Exception {
        var execution = states.begin(USER, COURSE, sessionKey);
        var hint = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue("{\"type\":\"HINT\",\"level\":1}", TutorAgentActionVO.class);
        jdbc.update("UPDATE tutor_session SET check_answer='A',check_correct=1 WHERE session_key=?", sessionKey);
        assertThrows(BusinessException.class, () -> states.complete(execution,
                "提示一步", new TutorAgentReply("迟到的提示", List.of(hint))));
        assertEquals(0, messageCount(execution));
    }

    @Test void hintCompletionWaitsForAnInFlightAnswerAndRechecksItsCommittedResult() throws Exception {
        var execution = states.begin(USER, COURSE, sessionKey);
        var hint = new TutorAgentActionVO("HINT", 1);
        CountDownLatch answerWritten = new CountDownLatch(1);
        CountDownLatch releaseAnswer = new CountDownLatch(1);
        var answering = CompletableFuture.runAsync(() ->
                new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
                    jdbc.update("UPDATE tutor_session SET check_answer='A',check_correct=1 WHERE session_key=?", sessionKey);
                    answerWritten.countDown();
                    try {
                        if (!releaseAnswer.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Answer transaction was not released");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                }));
        try {
            assertTrue(answerWritten.await(5, TimeUnit.SECONDS));
            CountDownLatch completionStarted = new CountDownLatch(1);
            var completion = CompletableFuture.supplyAsync(() -> {
                completionStarted.countDown();
                try {
                    states.complete(execution, "再提示", new TutorAgentReply("迟到的提示", List.of(hint)));
                    return true;
                } catch (BusinessException exception) {
                    return false;
                }
            });
            assertTrue(completionStarted.await(5, TimeUnit.SECONDS));
            assertThrows(java.util.concurrent.TimeoutException.class, () -> completion.get(250, TimeUnit.MILLISECONDS));
            releaseAnswer.countDown();
            answering.get(5, TimeUnit.SECONDS);
            assertFalse(completion.get(5, TimeUnit.SECONDS));
            assertEquals(0, messageCount(execution));
        } finally {
            releaseAnswer.countDown();
            answering.get(5, TimeUnit.SECONDS);
        }
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

    private TutorAgentService agent(AiProvider provider, TutorAgentToolExecutor tools, TutorMemoryService memory) {
        return new TutorAgentService(states, new TutorAgentRuntime(
                new AiInvocationService(provider, null, governance), tools, memory));
    }

    private AiProvider provider() {
        AiProvider provider = mock(AiProvider.class);
        when(provider.defaultOptions()).thenReturn(new ModelRequest.Options("test-model", 200, 0.2));
        return provider;
    }

    private TutorMemoryService memory() {
        TutorMemoryService memory = mock(TutorMemoryService.class);
        when(memory.promptContext(USER, COURSE)).thenReturn(
                "{\"revision\":0,\"explanationStyle\":null,\"goal\":null,\"sessionNotes\":[]}");
        return memory;
    }

    private ModelResult toolCall(String name, String id) {
        return new ModelResult(null, List.of(new ModelRequest.ToolCall(id, name, "{}")), "test-model", "first",
                ModelResult.Finish.TOOL_CALLS, new ModelResult.Usage(3, 2, 5));
    }

    private TutorAgentMessageRequest request(String message) {
        TutorAgentMessageRequest request = new TutorAgentMessageRequest();
        request.setMessage(message);
        return request;
    }

    private String callOutcome(UUID runId) {
        return jdbc.queryForObject("SELECT outcome FROM ai_call_log WHERE run_id=? ORDER BY id DESC LIMIT 1",
                String.class, runId.toString());
    }

    private Integer callTokens(UUID runId) {
        return jdbc.queryForObject("SELECT tokens_used FROM ai_call_log WHERE run_id=? ORDER BY id DESC LIMIT 1",
                Integer.class, runId.toString());
    }

    private String runStatus(String runKey) {
        return jdbc.queryForObject("SELECT status FROM tutor_agent_run WHERE run_key=?", String.class, runKey);
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

    private void expireRun(String runKey) {
        jdbc.update("UPDATE tutor_agent_run SET lease_until=DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 SECOND) WHERE run_key=?",
                runKey);
    }

    private int messageCount(TutorAgentExecutionState execution) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tutor_agent_message WHERE run_id=?", Integer.class, execution.id());
    }

    private int messageCount(UUID runId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tutor_agent_message WHERE run_id="
                + "(SELECT id FROM tutor_agent_run WHERE run_key=?)", Integer.class, runId.toString());
    }

    private void cleanUp() {
        if (jdbc == null) return;
        jdbc.update("DELETE FROM tutor_agent_message WHERE run_id IN (SELECT id FROM tutor_agent_run WHERE tutor_session_id IN (SELECT id FROM tutor_session WHERE user_id=?))", USER);
        jdbc.update("DELETE FROM tutor_agent_run WHERE tutor_session_id IN (SELECT id FROM tutor_session WHERE user_id=?)", USER);
        jdbc.update("DELETE FROM tutor_session WHERE user_id=?", USER);
        jdbc.update("DELETE FROM ai_call_log WHERE user_id=?", USER);
        jdbc.update("DELETE FROM tutor_content WHERE id=?", CONTENT);
        jdbc.update("DELETE FROM user_course WHERE user_id=?", USER);
        jdbc.update("DELETE FROM course WHERE id=?", COURSE);
        jdbc.update("DELETE FROM user WHERE id=?", USER);
    }
}
