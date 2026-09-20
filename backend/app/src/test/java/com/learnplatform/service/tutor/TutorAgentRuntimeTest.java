package com.learnplatform.service.tutor;

import com.learnplatform.ai.model.Cancellation;
import com.learnplatform.ai.model.ModelException;
import com.learnplatform.ai.model.ModelRequest;
import com.learnplatform.ai.model.ModelResult;
import com.learnplatform.service.AiInvocationService;
import com.learnplatform.service.ai.AiCallContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TutorAgentRuntimeTest {
    @Mock private AiInvocationService invocation;
    @Mock private TutorAgentToolExecutor tools;
    private TutorAgentRuntime runtime;
    private final ModelRequest.Options options = new ModelRequest.Options("test", 400, 0.2);

    @BeforeEach void setUp() {
        runtime = new TutorAgentRuntime(invocation, tools);
        when(invocation.defaultOptions()).thenReturn(options);
    }

    @Test void executesReviewedLessonToolBeforeReturningAnAnswer() {
        UUID runId = UUID.randomUUID();
        var call = new ModelRequest.ToolCall("call-1", "read_tutor_lesson", "{}");
        when(invocation.generate(any(), any(), any(Cancellation.class), any()))
                .thenReturn(new ModelResult(null, List.of(call), "test", "r1",
                        ModelResult.Finish.TOOL_CALLS, null))
                .thenReturn(new ModelResult("从右向左搬移，避免覆盖尚未读取的元素。", List.of(),
                        "test", "r2", ModelResult.Finish.STOP, null));
        when(tools.execute(7L, 10L, "session", call)).thenReturn("{\"title\":\"ArrayStack\"}");

        String answer = runtime.respond(7L, 10L, "session", runId,
                List.of(new TutorAgentHistoryMessage(ModelRequest.Role.USER, "上一轮问题"),
                        new TutorAgentHistoryMessage(ModelRequest.Role.ASSISTANT, "上一轮回答")),
                "为什么要从右向左搬？");

        assertEquals("从右向左搬移，避免覆盖尚未读取的元素。", answer);
        ArgumentCaptor<AiCallContext> contexts = ArgumentCaptor.forClass(AiCallContext.class);
        ArgumentCaptor<ModelRequest> requests = ArgumentCaptor.forClass(ModelRequest.class);
        verify(invocation, times(2)).generate(
                contexts.capture(), requests.capture(), any(Cancellation.class), any());
        assertEquals(List.of(runId, runId), contexts.getAllValues().stream().map(AiCallContext::runId).toList());
        assertEquals("tutor_agent", contexts.getValue().function());
        ModelRequest resumed = requests.getAllValues().get(1);
        assertEquals(ModelRequest.Role.TOOL, resumed.messages().get(resumed.messages().size() - 1).role());
        assertEquals("call-1", resumed.messages().get(resumed.messages().size() - 1).toolCallId());
        verify(tools).execute(7L, 10L, "session", call);
    }

    @Test void rejectsAProducedAnswerThatSkippedTheReviewedLessonTool() {
        when(invocation.generate(any(), any(), any(Cancellation.class), any())).thenAnswer(invocation -> {
            ModelResult result = new ModelResult(
                    "未经工具读取的回答", List.of(), "test", "r1", ModelResult.Finish.STOP, null);
            return invocation.getArgument(3, java.util.function.Function.class).apply(result);
        });

        ModelException exception = assertThrows(ModelException.class, () -> runtime.respond(
                7L, 10L, "session", UUID.randomUUID(), List.of(), "解释一下"));

        assertEquals(ModelException.Code.PROTOCOL, exception.code());
    }

    @Test void stopsAnUnboundedToolLoop() {
        when(invocation.generate(any(), any(), any(Cancellation.class), any())).thenAnswer(invocation -> {
            return invocation.getArgument(3, java.util.function.Function.class)
                    .apply(toolCallResult("call-" + nextToolCallId++));
        });
        when(tools.execute(eq(7L), eq(10L), eq("session"), any())).thenReturn("{}");

        ModelException exception = assertThrows(ModelException.class, () -> runtime.respond(
                7L, 10L, "session", UUID.randomUUID(), List.of(), "解释一下"));

        assertEquals(ModelException.Code.PROTOCOL, exception.code());
        verify(invocation, times(4)).generate(any(), any(), any(Cancellation.class), any());
    }

    private ModelResult toolCallResult(String id) {
        return new ModelResult(null,
                List.of(new ModelRequest.ToolCall(id, "read_tutor_lesson", "{}")),
                "test", "r", ModelResult.Finish.TOOL_CALLS, null);
    }

    private int nextToolCallId = 1;
}
