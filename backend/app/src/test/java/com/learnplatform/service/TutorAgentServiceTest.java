package com.learnplatform.service;

import com.learnplatform.ai.model.ModelException;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.TutorAgentMessageRequest;
import com.learnplatform.dto.TutorAgentRunVO;
import com.learnplatform.service.tutor.TutorAgentExecutionState;
import com.learnplatform.service.tutor.TutorAgentReply;
import com.learnplatform.service.tutor.TutorAgentRuntime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TutorAgentServiceTest {
    private final TutorAgentRunStateService states = mock(TutorAgentRunStateService.class);
    private final TutorAgentRuntime runtime = mock(TutorAgentRuntime.class);
    private TutorAgentService service;
    private TutorAgentExecutionState state;

    @BeforeEach void setUp() {
        service = new TutorAgentService(states, runtime);
        state = new TutorAgentExecutionState(5L, UUID.randomUUID(), UUID.randomUUID().toString(), 1, List.of());
    }

    @Test void startsAndPersistsAWaitingUserTurn() {
        TutorAgentMessageRequest request = request("  为什么从右向左搬移？  ");
        TutorAgentRunVO expected = new TutorAgentRunVO();
        expected.setStatus("WAITING_USER");
        when(states.begin(7L, 10L, "session")).thenReturn(state);
        when(runtime.respond(eq(7L), eq(10L), eq("session"), eq(state.runId()), eq(List.of()),
                eq("为什么从右向左搬移？"), any(Runnable.class)))
                .thenReturn(new TutorAgentReply("为了避免覆盖尚未读取的元素。", List.of()));
        when(states.complete(state, "为什么从右向左搬移？", new TutorAgentReply("为了避免覆盖尚未读取的元素。", List.of())))
                .thenReturn(expected);

        assertSame(expected, service.start(7L, 10L, "session", request));
    }

    @Test void releasesFailedRunsAndReturnsAStableBusinessError() {
        when(states.resume(7L, 10L, "session", "run")).thenReturn(state);
        when(runtime.respond(eq(7L), eq(10L), eq("session"), eq(state.runId()), eq(List.of()),
                eq("继续"), any(Runnable.class)))
                .thenThrow(new ModelException(ModelException.Code.PROTOCOL));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.resume(7L, 10L, "session", "run", request("继续")));

        assertEquals("Tutor Agent 未能生成可验证的回答，请重试", exception.getMessage());
        verify(states).fail(state);
    }

    @Test void loadsOnlyThroughTheBoundSessionAndOwnerStateService() {
        TutorAgentRunVO expected = new TutorAgentRunVO();
        when(states.get(7L, 10L, "session", "run")).thenReturn(expected);
        assertSame(expected, service.get(7L, 10L, "session", "run"));
    }

    private TutorAgentMessageRequest request(String message) {
        TutorAgentMessageRequest request = new TutorAgentMessageRequest();
        request.setMessage(message);
        return request;
    }
}
