package com.learnplatform.service.understanding;

import com.learnplatform.ai.model.Cancellation;
import com.learnplatform.ai.model.ModelException;
import com.learnplatform.ai.model.ModelRequest;
import com.learnplatform.ai.model.ModelResult;
import com.learnplatform.service.AiCallGovernanceService;
import com.learnplatform.service.AiInvocationService;
import com.learnplatform.service.ai.AiCallContext;
import com.learnplatform.service.ai.AiProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuestionAskAnswerServiceTest {
    AiProvider provider;
    QuestionAskAnswerService answers;
    final AiCallContext context = new AiCallContext(7L, "question_ask", UUID.randomUUID());
    final QuestionAskMaterials.Snapshot locked = new QuestionAskMaterials.Snapshot("visible", "private", false, "A", "后进先出");

    @BeforeEach void setup() {
        provider = mock(AiProvider.class);
        when(provider.defaultOptions()).thenReturn(new ModelRequest.Options("deepseek-flash", 1000, 0.7));
        answers = new QuestionAskAnswerService(new AiInvocationService(provider, mock(AiCallGovernanceService.class)));
    }

    ModelResult text(String value) { return new ModelResult(value, List.of(), "deepseek-flash", "r", ModelResult.Finish.STOP, null); }
    String answer(QuestionAskMaterials.Snapshot materials) {
        return answers.answer(context, materials, "我提到 A，请只解释概念", new Cancellation(), () -> { }, () -> { });
    }

    @Test void semanticRejectionAndInvalidJsonRetryThenFallback() {
        for (String rejected : List.of("{\"safe\":false}", "invalid json")) {
            when(provider.stream(any(), any(), any())).thenReturn(text("B、C、D 都不成立"), text(rejected),
                    text("先解释关键定义"), text(rejected));
            assertEquals(QuestionAskPolicy.FALLBACK, answer(locked));
        }
        verify(provider, times(8)).stream(any(), any(), any());
    }

    @Test void acceptsSafeFreeTextAfterRetryWithoutDiscardedTextInPrompt() {
        when(provider.stream(any(), any(), any())).thenReturn(text("自己推出第一项成立"), text("{\"safe\":false}"),
                text("先解释关键定义"), text("{\"safe\":true}"));
        assertEquals("先解释关键定义", answer(locked));
        var requests = org.mockito.ArgumentCaptor.forClass(ModelRequest.class);
        verify(provider, times(4)).stream(requests.capture(), any(), any());
        String retryPrompt = requests.getAllValues().get(2).messages().toString();
        assertTrue(retryPrompt.contains("更保守"));
        assertFalse(retryPrompt.contains("自己推出第一项成立"));
    }

    @Test void directRuleSkipsJudgeAndUnopenedCorrectContentNeverLeavesAsAnswer() {
        when(provider.stream(any(), any(), any())).thenReturn(text("答案是 A"), text("后进先出"));
        assertEquals(QuestionAskPolicy.FALLBACK, answer(locked));
        verify(provider, times(2)).stream(any(), any(), any());
    }

    @Test void unlockedFreeExplanationSkipsLeakageChecksButStillChecksMaterialAlignment() {
        when(provider.stream(any(), any(), any())).thenReturn(text("应选 A，我们可以重新解释后进先出的含义。"),
                text("{\"safe\":true}"));
        assertTrue(answer(new QuestionAskMaterials.Snapshot("full", "private", true, "A", "后进先出")).startsWith("应选 A"));
        var requests = org.mockito.ArgumentCaptor.forClass(ModelRequest.class);
        verify(provider, times(2)).stream(requests.capture(), any(), any());
        assertTrue(requests.getAllValues().get(1).messages().getFirst().content().contains("不检查答案泄露"));
    }

    @Test void truncatedOrEmptyJudgmentIsRejectedAndRetried() {
        for (var invalid : List.of(new ModelResult("{\"safe\":", List.of(), "deepseek-flash", "r",
                ModelResult.Finish.LENGTH, null), text(""))) {
            when(provider.stream(any(), any(), any())).thenReturn(text("先解释关键定义"), invalid,
                    text("先检查条件"), text("{\"safe\":true}"));
            assertEquals("先检查条件", answer(locked));
        }
        verify(provider, times(8)).stream(any(), any(), any());
    }

    @Test void judgeTimeoutPropagatesWithoutRegeneratingOrFallingBack() {
        when(provider.stream(any(), any(), any())).thenReturn(text("先解释关键定义"))
                .thenThrow(new ModelException(ModelException.Code.TIMEOUT));
        assertEquals(ModelException.Code.TIMEOUT, assertThrows(ModelException.class, () -> answer(locked)).code());
        verify(provider, times(2)).stream(any(), any(), any());
    }

    @Test void cancellationBetweenGenerationAndJudgePreventsNextCall() {
        var cancellation = new Cancellation();
        when(provider.stream(any(), any(), any())).thenAnswer(call -> { cancellation.cancel(); return text("先检查条件"); });
        assertEquals(ModelException.Code.CANCELLED, assertThrows(ModelException.class, () -> answers.answer(
                context, locked, "疑问", cancellation, cancellation::check, cancellation::check)).code());
        verify(provider, times(1)).stream(any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = ModelException.Code.class, names = {"SCHEMA", "REFUSAL", "TRUNCATED"})
    void rejectedJudgmentRegeneratesInsteadOfPublishingUncheckedText(ModelException.Code code) {
        when(provider.stream(any(), any(), any())).thenReturn(text("先解释定义"))
                .thenThrow(new ModelException(code)).thenReturn(text("再检查条件"), text("{\"safe\":true}"));
        assertEquals("再检查条件", answer(locked));
        verify(provider, times(4)).stream(any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = ModelException.Code.class, names = {"PROTOCOL", "UPSTREAM", "CANCELLED", "CONFIGURATION"})
    void operationalJudgmentFailurePropagatesWithoutPublishingOrRetry(ModelException.Code code) {
        when(provider.stream(any(), any(), any())).thenReturn(text("先解释定义"))
                .thenThrow(new ModelException(code));
        assertEquals(code, assertThrows(ModelException.class, () -> answer(locked)).code());
        verify(provider, times(2)).stream(any(), any(), any());
    }

    @Test void protocolFailureWithNonemptyStoppedPartialIsNotTreatedAsEmptyJudgment() {
        when(provider.stream(any(), any(), any())).thenReturn(text("先解释定义"))
                .thenThrow(new ModelException(ModelException.Code.PROTOCOL, text("{\"safe\":true}")));
        assertEquals(ModelException.Code.PROTOCOL,
                assertThrows(ModelException.class, () -> answer(locked)).code());
        verify(provider, times(2)).stream(any(), any(), any());
    }

    @Test void cancellationAfterSuccessfulJudgeStillPreventsPublishing() {
        var cancellation = new Cancellation();
        when(provider.stream(any(), any(), any())).thenReturn(text("先解释定义")).thenAnswer(call -> {
            cancellation.cancel();
            return text("{\"safe\":true}");
        });
        assertEquals(ModelException.Code.CANCELLED, assertThrows(ModelException.class, () -> answers.answer(
                context, locked, "疑问", cancellation, cancellation::check, () -> { })).code());
        verify(provider, times(2)).stream(any(), any(), any());
    }
}
