package com.learnplatform.service;

import com.learnplatform.ai.model.Cancellation;
import com.learnplatform.ai.model.ModelEvent;
import com.learnplatform.ai.model.ModelException;
import com.learnplatform.ai.model.ModelRequest;
import com.learnplatform.ai.model.ModelResult;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.entity.AiCallLog;
import com.learnplatform.service.ai.AiCallContext;
import com.learnplatform.service.ai.AiCallTicket;
import com.learnplatform.service.ai.AiProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiInvocationServiceTest {
    @Mock private AiProvider provider;
    @Mock private AiCallGovernanceService governance;
    private AiInvocationService invocation;
    private final ModelRequest.Options options = new ModelRequest.Options("test", 200, 0.7);
    private final ModelRequest request = ModelRequest.text("system", "user", options);
    private final AiCallContext context = new AiCallContext(7L, "test", null);
    private final AiCallTicket ticket = new AiCallTicket(new AiCallLog(), Map.of());

    @BeforeEach void setUp() {
        invocation = new AiInvocationService(provider, governance);
        lenient().when(provider.defaultOptions()).thenReturn(options);
        lenient().when(governance.begin(any(), any())).thenReturn(ticket);
    }

    @Test void quotaAdmissionHappensBeforeCloudAndFailurePreventsAnyCall() {
        when(governance.begin(any(), any())).thenThrow(new BusinessException(ResultCode.QUOTA_EXCEEDED));
        assertThrows(BusinessException.class, () -> invocation.generate(context, request, new Cancellation()));
        verify(provider, never()).complete(any(), any());
        verify(governance, never()).finish(any(), any(), any(), anyLong());
    }

    @Test void recordsEachAttemptWithoutSharingUsage() {
        var success = new ModelResult("lesson", List.of(), "actual", "r1", ModelResult.Finish.STOP,
                new ModelResult.Usage(3, 2, 5));
        when(provider.complete(any(), any())).thenReturn(success).thenThrow(new ModelException(ModelException.Code.TIMEOUT));
        assertSame(success, invocation.generate(context, request, new Cancellation()));
        assertEquals(ModelException.Code.TIMEOUT, assertThrows(ModelException.class,
                () -> invocation.generate(context, request, new Cancellation())).code());
        verify(governance).finish(eq(ticket), same(success), eq("SUCCEEDED"), anyLong());
        verify(governance).finish(eq(ticket), isNull(), eq("TIMEOUT"), anyLong());
        verify(provider, times(2)).complete(any(), any());
    }

    @Test void businessValidationFailureStillRecordsKnownUsage() {
        var result = new ModelResult("invalid", List.of(), "actual", null, ModelResult.Finish.STOP,
                new ModelResult.Usage(3, 2, 5));
        when(provider.complete(any(), any())).thenReturn(result);
        assertThrows(BusinessException.class, () -> invocation.text("test", 7L,
                new AiService.AiPrompt("system", "user"), text -> {
                    throw new BusinessException(ResultCode.VALIDATION_ERROR);
                }));
        verify(governance).finish(eq(ticket), same(result), eq("FAILED"), anyLong());
    }

    @Test void legacyStreamNeverReportsDoneOnTruncation() {
        var partial = new ModelResult("partial", List.of(), "actual", null, ModelResult.Finish.LENGTH, null);
        when(provider.stream(any(), any(), any())).thenAnswer(call -> {
            Consumer<ModelEvent> consumer = call.getArgument(1);
            consumer.accept(new ModelEvent.TextDelta("partial"));
            consumer.accept(new ModelEvent.Completed(partial));
            return partial;
        });
        var output = new StringBuilder();
        assertThrows(BusinessException.class, () -> invocation.stream("test", 7L,
                new AiService.AiPrompt("system", "user"), output::append));
        assertEquals("partial", output.toString());
        verify(governance).finish(eq(ticket), same(partial), eq("TRUNCATED"), anyLong());
    }

    @Test void structuredCallerGetsOneTypedTerminalEvent() {
        var result = new ModelResult("lesson", List.of(), "actual", null, ModelResult.Finish.STOP, null);
        when(provider.stream(any(), any(), any())).thenAnswer(call -> {
            call.getArgument(1, Consumer.class).accept(new ModelEvent.Completed(result));
            return result;
        });
        var events = new ArrayList<ModelEvent>();
        invocation.generateStream(context, request, events::add, new Cancellation());
        assertEquals(List.of(new ModelEvent.Completed(result)), events);
    }

    @Test void missingIdentityAndUnsupportedRequestsCannotBypassGovernance() {
        assertThrows(BusinessException.class, () -> invocation.callUnlogged(new AiService.AiPrompt("system", "user")));
        doThrow(new ModelException(ModelException.Code.UNSUPPORTED)).when(provider).validate(any());
        assertThrows(ModelException.class, () -> invocation.generate(context, request, new Cancellation()));
        verify(governance, never()).begin(any(), any());
        verify(provider, never()).complete(any(), any());
    }

    @Test void structuredBusinessValidationFailureIsAuditedWithItsKnownUsage() {
        var result = new ModelResult("answer", List.of(), "actual", "r1", ModelResult.Finish.STOP,
                new ModelResult.Usage(3, 2, 5));
        when(provider.complete(any(), any())).thenReturn(result);

        ModelException exception = assertThrows(ModelException.class, () -> invocation.generate(
                context, request, new Cancellation(), candidate -> {
                    throw new ModelException(ModelException.Code.PROTOCOL, candidate);
                }));

        assertEquals(ModelException.Code.PROTOCOL, exception.code());
        verify(governance).finish(eq(ticket), same(result), eq("PROTOCOL"), anyLong());
    }
}
