package com.learnplatform.service.tutor;

import com.learnplatform.ai.model.ModelException;
import com.learnplatform.ai.model.ModelRequest;
import com.learnplatform.ai.model.ModelResult;
import com.learnplatform.service.AiInvocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TutorAgentPlanRuntimeTest {
    @Mock private AiInvocationService invocation;
    @Mock private TutorAgentToolExecutor tools;
    @Mock private TutorMemoryService memories;
    private TutorAgentRuntime runtime;
    private static final String PLAN = """
            {"status":"AVAILABLE","action":{"type":"PLAN","steps":[
            {"type":"TUTOR","title":"学习栈","reason":"尚未检查","knowledgePointId":31}]}}
            """;

    @BeforeEach void setUp() {
        runtime = new TutorAgentRuntime(invocation, tools, memories);
        when(memories.promptContext(7L, 10L)).thenReturn("{\"revision\":0,\"explanationStyle\":null,\"goal\":null}");
        when(invocation.defaultOptions()).thenReturn(new ModelRequest.Options("test", 400, 0.2));
    }

    @Test void deduplicatesTheSamePlanButRejectsConflictingPlansWithinOneTurn() {
        prepareRecommendations(PLAN, PLAN);
        assertEquals(1, runtime.respond(7L, 10L, "session", UUID.randomUUID(), List.of(), "安排").actions().size());
        prepareRecommendations(PLAN, PLAN.replace(":31", ":32"));
        assertEquals(ModelException.Code.PROTOCOL, assertThrows(ModelException.class,
                () -> runtime.respond(7L, 10L, "session", UUID.randomUUID(), List.of(), "安排")).code());
    }

    @Test void unavailablePlansCannotBeReplacedWithModelWrittenActionText() {
        prepareRecommendations("{\"status\":\"UNAVAILABLE\"}", "{\"status\":\"UNAVAILABLE\"}");
        assertEquals(List.of(), runtime.respond(7L, 10L, "session", UUID.randomUUID(), List.of(), "安排").actions());
    }

    @Test void readsRealConfirmationWithTheTrustedRunAndDoesNotEmitASecondPlanAction() {
        UUID runId = UUID.randomUUID();
        var lesson = new ModelRequest.ToolCall("lesson", "read_tutor_lesson", "{}");
        var state = new ModelRequest.ToolCall("state", "read_tutor_plan_state", "{}");
        when(invocation.generate(any(), any(), any(), any()))
                .thenReturn(new ModelResult(null, List.of(lesson, state), "test", "r1",
                        ModelResult.Finish.TOOL_CALLS, null))
                .thenReturn(new ModelResult("还未确认。", List.of(), "test", "r2", ModelResult.Finish.STOP, null));
        when(tools.execute(7L, 10L, "session", lesson)).thenReturn("{}");
        when(tools.execute(7L, 10L, "session", state, runId)).thenReturn("{\"status\":\"PROPOSED\"}");
        var reply = runtime.respond(7L, 10L, "session", runId, List.of(), "我已经确认了，帮我记为完成");
        assertEquals(List.of(), reply.actions());
        verify(tools).execute(7L, 10L, "session", state, runId);
    }

    private void prepareRecommendations(String first, String second) {
        var lesson = new ModelRequest.ToolCall("lesson", "read_tutor_lesson", "{}");
        var plan = new ModelRequest.ToolCall("plan", "propose_tutor_plan", "{}");
        var again = new ModelRequest.ToolCall("again", "propose_tutor_plan", "{}");
        when(invocation.generate(any(), any(), any(), any()))
                .thenReturn(new ModelResult(null, List.of(lesson, plan, again), "test", "r1",
                        ModelResult.Finish.TOOL_CALLS, null))
                .thenReturn(new ModelResult(PLAN, List.of(), "test", "r2", ModelResult.Finish.STOP, null));
        when(tools.execute(7L, 10L, "session", lesson)).thenReturn("{}");
        when(tools.execute(7L, 10L, "session", plan)).thenReturn(first);
        when(tools.execute(7L, 10L, "session", again)).thenReturn(second);
    }
}
