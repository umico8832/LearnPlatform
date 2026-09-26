package com.learnplatform.service.tutor;

import com.learnplatform.ai.model.Cancellation;
import com.learnplatform.ai.model.ModelException;
import com.learnplatform.ai.model.ModelRequest;
import com.learnplatform.ai.model.ModelResult;
import com.learnplatform.dto.TutorAgentActionVO;
import com.learnplatform.service.AiInvocationService;
import com.learnplatform.service.ai.AiCallContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.mockito.Mockito.never;
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

        TutorAgentReply answer = runtime.respond(7L, 10L, "session", runId,
                List.of(new TutorAgentHistoryMessage(ModelRequest.Role.USER, "上一轮问题"),
                        new TutorAgentHistoryMessage(ModelRequest.Role.ASSISTANT, "上一轮回答")),
                "为什么要从右向左搬？");

        assertEquals("从右向左搬移，避免覆盖尚未读取的元素。", answer.content());
        ArgumentCaptor<AiCallContext> contexts = ArgumentCaptor.forClass(AiCallContext.class);
        ArgumentCaptor<ModelRequest> requests = ArgumentCaptor.forClass(ModelRequest.class);
        verify(invocation, times(2)).generate(
                contexts.capture(), requests.capture(), any(Cancellation.class), any());
        assertEquals(List.of(runId, runId), contexts.getAllValues().stream().map(AiCallContext::runId).toList());
        assertEquals("tutor_agent", contexts.getValue().function());
        ModelRequest resumed = requests.getAllValues().get(1);
        assertEquals(List.of("read_tutor_lesson", "read_learning_evidence",
                        "present_tutor_check", "read_tutor_check_result", "request_tutor_hint",
                        "recommend_tutor_practice", "read_tutor_practice_result"),
                resumed.tools().stream().map(ModelRequest.Tool::name).toList());
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

    @Test void registersOptionalSearchAndAppendsOnlyReturnedSourceReferences() {
        UUID runId = UUID.randomUUID();
        when(tools.supportsKnowledgeSearch()).thenReturn(true);
        var lesson = new ModelRequest.ToolCall("lesson", "read_tutor_lesson", "{}");
        var search = new ModelRequest.ToolCall("search", "search_course_knowledge", "{\"query\":\"栈\"}");
        when(invocation.generate(any(), any(), any(Cancellation.class), any()))
                .thenReturn(new ModelResult(null, List.of(lesson, search), "test", "r1",
                        ModelResult.Finish.TOOL_CALLS, null))
                .thenReturn(new ModelResult("解释", List.of(), "test", "r2", ModelResult.Finish.STOP, null));
        when(tools.execute(7L, 10L, "session", lesson)).thenReturn("{}");
        when(tools.execute(7L, 10L, "session", search, runId)).thenReturn("""
                {"citations":[{"bundleId":3,"chunkId":"stack-core","title":"栈","version":"v1","text":"后进先出"}]}
                """);
        TutorAgentReply answer = runtime.respond(7L, 10L, "session", runId, List.of(), "解释栈");
        assertEquals("解释\n\n本轮检索资料：\n- 栈（版本 v1，片段 stack-core）", answer.content());
        ArgumentCaptor<ModelRequest> requests = ArgumentCaptor.forClass(ModelRequest.class);
        verify(invocation, times(2)).generate(any(), requests.capture(), any(), any());
        assertEquals(8, requests.getValue().tools().size());
        verify(tools).execute(7L, 10L, "session", search, runId);
    }

    @Test void rejectsAnInitialSearchToolCallBeforeExecutingIt() {
        when(tools.supportsKnowledgeSearch()).thenReturn(true);
        var search = new ModelRequest.ToolCall("search", "search_course_knowledge", "{\"query\":\"栈\"}");
        when(invocation.generate(any(), any(), any(Cancellation.class), any())).thenAnswer(call ->
                call.getArgument(3, java.util.function.Function.class).apply(
                        new ModelResult(null, List.of(search), "test", "r1", ModelResult.Finish.TOOL_CALLS, null)));

        ModelException exception = assertThrows(ModelException.class, () -> runtime.respond(
                7L, 10L, "session", UUID.randomUUID(), List.of(), "解释栈"));

        assertEquals(ModelException.Code.PROTOCOL, exception.code());
        verify(tools, never()).execute(eq(7L), eq(10L), eq("session"), any(ModelRequest.ToolCall.class));
        verify(tools, never()).execute(eq(7L), eq(10L), eq("session"), any(ModelRequest.ToolCall.class), any(UUID.class));
    }

    @Test void rejectsAnInitialBatchWhoseFirstToolIsSearchBeforeExecutingIt() {
        when(tools.supportsKnowledgeSearch()).thenReturn(true);
        var search = new ModelRequest.ToolCall("search", "search_course_knowledge", "{\"query\":\"栈\"}");
        var lesson = new ModelRequest.ToolCall("lesson", "read_tutor_lesson", "{}");
        when(invocation.generate(any(), any(), any(Cancellation.class), any())).thenAnswer(call ->
                call.getArgument(3, java.util.function.Function.class).apply(
                        new ModelResult(null, List.of(search, lesson), "test", "r1", ModelResult.Finish.TOOL_CALLS, null)));

        ModelException exception = assertThrows(ModelException.class, () -> runtime.respond(
                7L, 10L, "session", UUID.randomUUID(), List.of(), "解释栈"));

        assertEquals(ModelException.Code.PROTOCOL, exception.code());
        verify(tools, never()).execute(eq(7L), eq(10L), eq("session"), any(ModelRequest.ToolCall.class));
        verify(tools, never()).execute(eq(7L), eq(10L), eq("session"), any(ModelRequest.ToolCall.class), any(UUID.class));
    }

    @Test void returnsADeduplicatedServerActionOnlyAfterTheWholeTurnSucceeds() {
        var lesson = new ModelRequest.ToolCall("lesson", "read_tutor_lesson", "{}");
        var first = new ModelRequest.ToolCall("check-1", "present_tutor_check", "{}");
        var repeated = new ModelRequest.ToolCall("check-2", "present_tutor_check", "{}");
        when(invocation.generate(any(), any(), any(), any()))
                .thenReturn(new ModelResult(null, List.of(lesson, first, repeated), "test", "r1",
                        ModelResult.Finish.TOOL_CALLS, null))
                .thenReturn(new ModelResult("请自己选择答案并提交。", List.of(), "test", "r2",
                        ModelResult.Finish.STOP, null));
        when(tools.execute(7L, 10L, "session", lesson)).thenReturn("{}");
        when(tools.execute(7L, 10L, "session", first)).thenReturn("{\"action\":{\"type\":\"CHECK\"}}");
        when(tools.execute(7L, 10L, "session", repeated)).thenReturn("{\"action\":{\"type\":\"CHECK\"}}");

        var answer = runtime.respond(7L, 10L, "session", UUID.randomUUID(), List.of(), "检查一下我的理解");

        assertEquals(List.of(new TutorAgentActionVO("CHECK")), answer.actions());
        assertEquals("请自己选择答案并提交。", answer.content());
    }

    @Test void assignsOneServerHintLevelForRepeatedRequestsInTheSameTurn() {
        var lesson = new ModelRequest.ToolCall("lesson", "read_tutor_lesson", "{}");
        var first = new ModelRequest.ToolCall("hint-1", "request_tutor_hint", "{}");
        var repeated = new ModelRequest.ToolCall("hint-2", "request_tutor_hint", "{}");
        when(invocation.generate(any(), any(), any(), any()))
                .thenReturn(new ModelResult(null, List.of(lesson, first, repeated), "test", "r1",
                        ModelResult.Finish.TOOL_CALLS, null))
                .thenReturn(new ModelResult("回看本节步骤。", List.of(), "test", "r2",
                        ModelResult.Finish.STOP, null));
        when(tools.execute(7L, 10L, "session", lesson)).thenReturn("{}");
        when(tools.execute(7L, 10L, "session", first)).thenReturn("{\"status\":\"AVAILABLE\"}");
        when(tools.execute(7L, 10L, "session", repeated)).thenReturn("{\"status\":\"AVAILABLE\"}");

        var answer = runtime.respond(7L, 10L, "session", UUID.randomUUID(), List.of(), "给我一点提示");

        assertEquals(List.of(new TutorAgentActionVO("HINT", 1)), answer.actions());
    }

    @Test void limitsHintsUsingTheFullTrustedHistoryRatherThanThePromptWindow() {
        var lesson = new ModelRequest.ToolCall("lesson", "read_tutor_lesson", "{}");
        var hint = new ModelRequest.ToolCall("hint", "request_tutor_hint", "{}");
        when(invocation.generate(any(), any(), any(), any()))
                .thenReturn(new ModelResult(null, List.of(lesson, hint), "test", "r1",
                        ModelResult.Finish.TOOL_CALLS, null))
                .thenReturn(new ModelResult("本节提示已全部给出。", List.of(), "test", "r2",
                        ModelResult.Finish.STOP, null));
        when(tools.execute(7L, 10L, "session", lesson)).thenReturn("{}");
        when(tools.execute(7L, 10L, "session", hint)).thenReturn("{\"status\":\"AVAILABLE\"}");
        var history = new java.util.ArrayList<TutorAgentHistoryMessage>();
        history.add(new TutorAgentHistoryMessage(ModelRequest.Role.ASSISTANT, "最早提示",
                List.of(new TutorAgentActionVO("HINT", 3))));
        for (int index = 0; index < 12; index++) {
            history.add(new TutorAgentHistoryMessage(ModelRequest.Role.USER, "历史" + index));
        }

        var answer = runtime.respond(7L, 10L, "session", UUID.randomUUID(), history, "再给提示");

        assertEquals(List.of(), answer.actions());
        ArgumentCaptor<ModelRequest> requests = ArgumentCaptor.forClass(ModelRequest.class);
        verify(invocation, times(2)).generate(any(), requests.capture(), any(), any());
        assertEquals("{\"status\":\"LIMIT_REACHED\"}",
                requests.getAllValues().get(1).messages().get(requests.getAllValues().get(1).messages().size() - 1).content());
    }

    @Test void answeredHintToolResultsDoNotCreateAnAction() {
        var lesson = new ModelRequest.ToolCall("lesson", "read_tutor_lesson", "{}");
        var hint = new ModelRequest.ToolCall("hint", "request_tutor_hint", "{}");
        when(invocation.generate(any(), any(), any(), any()))
                .thenReturn(new ModelResult(null, List.of(lesson, hint), "test", "r1",
                        ModelResult.Finish.TOOL_CALLS, null))
                .thenReturn(new ModelResult("已作答。", List.of(), "test", "r2", ModelResult.Finish.STOP, null));
        when(tools.execute(7L, 10L, "session", lesson)).thenReturn("{}");
        when(tools.execute(7L, 10L, "session", hint)).thenReturn("{\"status\":\"ANSWERED\",\"result\":{\"correct\":true}}");

        assertEquals(List.of(), runtime.respond(7L, 10L, "session", UUID.randomUUID(), List.of(), "再提示").actions());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\":\"AVAILABLE\",\"level\":3}",
            "{\"status\":\"ANSWERED\"}", "{\"status\":\"UNKNOWN\"}"})
    void refusesMalformedHintToolResults(String output) {
        var lesson = new ModelRequest.ToolCall("lesson", "read_tutor_lesson", "{}");
        var hint = new ModelRequest.ToolCall("hint", "request_tutor_hint", "{}");
        when(invocation.generate(any(), any(), any(), any()))
                .thenReturn(new ModelResult(null, List.of(lesson, hint), "test", "r1",
                        ModelResult.Finish.TOOL_CALLS, null));
        when(tools.execute(7L, 10L, "session", lesson)).thenReturn("{}");
        when(tools.execute(7L, 10L, "session", hint)).thenReturn(output);
        assertEquals(ModelException.Code.PROTOCOL, assertThrows(ModelException.class,
                () -> runtime.respond(7L, 10L, "session", UUID.randomUUID(), List.of(), "提示")).code());
    }

    @Test void cannotTurnAnAnswerShapedLikeAnActionIntoAnExecutableAction() {
        var lesson = new ModelRequest.ToolCall("lesson", "read_tutor_lesson", "{}");
        when(invocation.generate(any(), any(), any(), any()))
                .thenReturn(new ModelResult(null, List.of(lesson), "test", "r1", ModelResult.Finish.TOOL_CALLS, null))
                .thenReturn(new ModelResult("{\"action\":{\"type\":\"CHECK\"}}", List.of(), "test", "r2",
                        ModelResult.Finish.STOP, null));
        when(tools.execute(7L, 10L, "session", lesson)).thenReturn("{}");
        assertEquals(List.of(), runtime.respond(7L, 10L, "session", UUID.randomUUID(), List.of(), "检查").actions());
    }

    @Test void offersOnlyThePracticeQuestionSelectedByTheServer() {
        var lesson = new ModelRequest.ToolCall("lesson", "read_tutor_lesson", "{}");
        var practice = new ModelRequest.ToolCall("practice", "recommend_tutor_practice", "{}");
        when(invocation.generate(any(), any(), any(), any()))
                .thenReturn(new ModelResult(null, List.of(lesson, practice), "test", "r1",
                        ModelResult.Finish.TOOL_CALLS, null))
                .thenReturn(new ModelResult("请打开练习并自行作答。", List.of(), "test", "r2",
                        ModelResult.Finish.STOP, null));
        when(tools.execute(7L, 10L, "session", lesson)).thenReturn("{}");
        when(tools.execute(7L, 10L, "session", practice))
                .thenReturn("{\"status\":\"AVAILABLE\",\"action\":{\"type\":\"PRACTICE\",\"questionId\":51}}");
        var answer = runtime.respond(7L, 10L, "session", UUID.randomUUID(), List.of(), "想练一题");
        assertEquals(List.of(new TutorAgentActionVO("PRACTICE", null, 51L)), answer.actions());
    }

    @Test void unavailablePracticeDoesNotCreateAnAction() {
        practiceResponse("{\"status\":\"UNAVAILABLE\"}");
        assertEquals(List.of(), runtime.respond(7L, 10L, "session", UUID.randomUUID(), List.of(), "练习").actions());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"status\":\"AVAILABLE\",\"action\":{\"type\":\"PRACTICE\",\"questionId\":0}}",
        "{\"status\":\"AVAILABLE\",\"action\":{\"type\":\"PRACTICE\",\"questionId\":1.5}}",
        "{\"status\":\"AVAILABLE\",\"action\":{\"type\":\"PRACTICE\",\"questionId\":\"51\"}}",
        "{\"status\":\"UNAVAILABLE\",\"action\":{\"type\":\"PRACTICE\",\"questionId\":51}}"
    })
    void rejectsMalformedPracticeSelection(String output) {
        practiceResponse(output);
        assertEquals(ModelException.Code.PROTOCOL, assertThrows(ModelException.class,
                () -> runtime.respond(7L, 10L, "session", UUID.randomUUID(), List.of(), "练习")).code());
    }

    @Test void readsPracticeOutcomeWithTheTrustedRunId() {
        UUID runId = UUID.randomUUID();
        var lesson = new ModelRequest.ToolCall("lesson", "read_tutor_lesson", "{}");
        var outcome = new ModelRequest.ToolCall("result", "read_tutor_practice_result", "{}");
        when(invocation.generate(any(), any(), any(), any()))
                .thenReturn(new ModelResult(null, List.of(lesson, outcome), "test", "r1", ModelResult.Finish.TOOL_CALLS, null))
                .thenReturn(new ModelResult("请复习这一概念。", List.of(), "test", "r2", ModelResult.Finish.STOP, null));
        when(tools.execute(7L, 10L, "session", lesson)).thenReturn("{}");
        when(tools.execute(7L, 10L, "session", outcome, runId)).thenReturn("{\"status\":\"ANSWERED\",\"result\":{\"correct\":false}}");
        assertEquals(List.of(), runtime.respond(7L, 10L, "session", runId, List.of(), "继续").actions());
        verify(tools).execute(7L, 10L, "session", outcome, runId);
    }

    private void practiceResponse(String output) {
        var lesson = new ModelRequest.ToolCall("lesson", "read_tutor_lesson", "{}");
        var practice = new ModelRequest.ToolCall("practice", "recommend_tutor_practice", "{}");
        when(invocation.generate(any(), any(), any(), any()))
                .thenReturn(new ModelResult(null, List.of(lesson, practice), "test", "r1", ModelResult.Finish.TOOL_CALLS, null))
                .thenReturn(new ModelResult("请自行练习。", List.of(), "test", "r2", ModelResult.Finish.STOP, null));
        when(tools.execute(7L, 10L, "session", lesson)).thenReturn("{}");
        when(tools.execute(7L, 10L, "session", practice)).thenReturn(output);
    }

    @Test void restoresTheActionBoundaryInHistoryWithoutClaimingTheUserAnswered() {
        var lesson = new ModelRequest.ToolCall("lesson", "read_tutor_lesson", "{}");
        when(invocation.generate(any(), any(), any(), any()))
                .thenReturn(new ModelResult(null, List.of(lesson), "test", "r1", ModelResult.Finish.TOOL_CALLS, null))
                .thenReturn(new ModelResult("请先提交检查。", List.of(), "test", "r2", ModelResult.Finish.STOP, null));
        when(tools.execute(7L, 10L, "session", lesson)).thenReturn("{}");
        var history = List.of(new TutorAgentHistoryMessage(ModelRequest.Role.ASSISTANT, "请自测",
                List.of(new TutorAgentActionVO("CHECK"), new TutorAgentActionVO("HINT", 2))));
        runtime.respond(7L, 10L, "session", UUID.randomUUID(), history, "继续");
        ArgumentCaptor<ModelRequest> requests = ArgumentCaptor.forClass(ModelRequest.class);
        verify(invocation, times(2)).generate(any(), requests.capture(), any(), any());
        assertEquals("请自测\n本轮已提供理解检查入口；展示不代表用户已作答。"
                        + "\n本轮已提供第2层服务端控制提示；展示不代表用户已作答、学习或掌握。",
                requests.getValue().messages().get(1).content());
    }

    private ModelResult toolCallResult(String id) {
        return new ModelResult(null,
                List.of(new ModelRequest.ToolCall(id, "read_tutor_lesson", "{}")),
                "test", "r", ModelResult.Finish.TOOL_CALLS, null);
    }

    private int nextToolCallId = 1;
}
