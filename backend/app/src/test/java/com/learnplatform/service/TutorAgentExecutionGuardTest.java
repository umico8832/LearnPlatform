package com.learnplatform.service;

import com.learnplatform.ai.model.Cancellation;
import com.learnplatform.ai.model.ModelRequest;
import com.learnplatform.ai.model.ModelResult;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.TutorAgentMessageRequest;
import com.learnplatform.entity.AiCallLog;
import com.learnplatform.service.ai.AiCallTicket;
import com.learnplatform.service.ai.AiProvider;
import com.learnplatform.service.tutor.TutorAgentExecutionState;
import com.learnplatform.service.tutor.TutorAgentRuntime;
import com.learnplatform.service.tutor.TutorAgentToolExecutor;
import com.learnplatform.service.tutor.TutorMemoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TutorAgentExecutionGuardTest {
    private final AiProvider provider = mock(AiProvider.class);
    private final AiCallGovernanceService governance = mock(AiCallGovernanceService.class);
    private final TutorAgentRunStateService states = mock(TutorAgentRunStateService.class);
    private final TutorAgentToolExecutor tools = mock(TutorAgentToolExecutor.class);
    private final TutorMemoryService memory = mock(TutorMemoryService.class);
    private final AiCallTicket ticket = new AiCallTicket(new AiCallLog(), Map.of());
    private final TutorAgentExecutionState state = new TutorAgentExecutionState(
            5L, UUID.randomUUID(), "execution", 1, List.of());
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final ModelRequest.ToolCall lesson = new ModelRequest.ToolCall("lesson", "read_tutor_lesson", "{}");
    private TutorAgentService service;

    @BeforeEach void setUp() {
        when(provider.defaultOptions()).thenReturn(new ModelRequest.Options("test", 300, 0.2));
        when(governance.begin(any(), any())).thenReturn(ticket);
        when(memory.promptContext(7L, 10L)).thenReturn("{}");
        when(states.begin(7L, 10L, "session")).thenReturn(state);
        doAnswer(call -> {
            if (!active.get()) { throw new BusinessException(ResultCode.RATE_LIMITED); }
            return null;
        }).when(states).requireActiveExecution(state);
        var runtime = new TutorAgentRuntime(new AiInvocationService(provider, governance), tools, memory);
        service = new TutorAgentService(states, runtime);
    }

    @Test void staleExecutionCannotReadMemoryOrReserveItsFirstModelCall() {
        active.set(false);
        assertThrows(BusinessException.class, this::start);
        verifyNoInteractions(memory, provider, governance);
        verify(states).fail(state);
        verify(states, never()).complete(any(), any(), any());
    }

    @Test void leaseLostDuringModelCallStopsItsToolsAndPreservesUsageAsFailed() {
        var result = requestedTools(lesson);
        when(provider.complete(any(), any())).thenAnswer(call -> {
            active.set(false);
            return result;
        });
        assertThrows(BusinessException.class, this::start);
        verify(tools, never()).execute(any(), any(), any(), any());
        verify(provider).complete(any(), any());
        verify(governance).finish(eq(ticket), same(result), eq("FAILED"), anyLong());
        verify(states, never()).complete(any(), any(), any());
        verify(states).fail(state);
    }

    @Test void leaseLostWhileReadingMemoryPreventsTheFirstModelCall() {
        when(memory.promptContext(7L, 10L)).thenAnswer(call -> {
            active.set(false);
            return "{}";
        });
        assertThrows(BusinessException.class, this::start);
        verifyNoInteractions(provider, governance);
        verify(states, never()).complete(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void leaseLostInsideAToolPreventsTheRemainingBatchOrNextModelCall(boolean moreTools) {
        var search = new ModelRequest.ToolCall("search", "search_course_knowledge", "{\"query\":\"栈\"}");
        when(tools.supportsKnowledgeSearch()).thenReturn(true);
        var result = moreTools ? requestedTools(lesson, search) : requestedTools(lesson);
        when(provider.complete(any(), any())).thenReturn(result);
        when(tools.execute(7L, 10L, "session", lesson)).thenAnswer(call -> {
            active.set(false);
            return "{}";
        });
        assertThrows(BusinessException.class, this::start);
        verify(tools, never()).execute(any(), any(), any(), eq(search), any());
        verify(provider).complete(any(), any());
        verify(governance).finish(eq(ticket), same(result), eq("SUCCEEDED"), anyLong());
        verify(states, never()).complete(any(), any(), any());
    }

    @Test void cancellationFromTheFirstModelCallRemainsEffectiveBeforeTheNextRound() {
        AtomicReference<Cancellation> cancellation = new AtomicReference<>();
        when(provider.complete(any(), any())).thenAnswer(call -> {
            cancellation.set(call.getArgument(1));
            return requestedTools(lesson);
        });
        when(tools.execute(7L, 10L, "session", lesson)).thenAnswer(call -> {
            cancellation.get().cancel();
            return "{}";
        });
        BusinessException failure = assertThrows(BusinessException.class, this::start);
        assertEquals("Tutor Agent 请求已取消", failure.getMessage());
        verify(provider).complete(any(), any());
        verify(states, never()).complete(any(), any(), any());
        verify(states).fail(state);
    }

    @Test void interruptionAfterModelResponsePreventsToolsAndRecordsCancellation() {
        var result = requestedTools(lesson);
        when(provider.complete(any(), any())).thenAnswer(call -> {
            Thread.currentThread().interrupt();
            return result;
        });
        when(tools.execute(7L, 10L, "session", lesson)).thenReturn("{}");
        try {
            BusinessException failure = assertThrows(BusinessException.class, this::start);
            assertEquals("Tutor Agent 请求已取消", failure.getMessage());
        } finally {
            Thread.interrupted();
        }
        verify(tools, never()).execute(any(), any(), any(), any());
        verify(provider).complete(any(), any());
        verify(governance).finish(eq(ticket), same(result), eq("CANCELLED"), anyLong());
    }

    private void start() {
        TutorAgentMessageRequest request = new TutorAgentMessageRequest();
        request.setMessage("解释栈");
        service.start(7L, 10L, "session", request);
    }

    private ModelResult requestedTools(ModelRequest.ToolCall... calls) {
        return new ModelResult(null, List.of(calls), "test", "response", ModelResult.Finish.TOOL_CALLS,
                new ModelResult.Usage(12, 8, 20));
    }
}
