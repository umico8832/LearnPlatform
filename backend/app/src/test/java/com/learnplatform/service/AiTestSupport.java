package com.learnplatform.service;

import com.learnplatform.ai.model.ModelEvent;
import com.learnplatform.ai.model.ModelRequest;
import com.learnplatform.ai.model.ModelResult;
import com.learnplatform.entity.AiCallLog;
import com.learnplatform.service.ai.AiCallContext;
import com.learnplatform.service.ai.AiCallTicket;
import com.learnplatform.service.ai.AiProvider;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

final class AiTestSupport {
    private AiTestSupport() { }

    static void wire(AiProvider provider, AiCallGovernanceService governance) {
        lenient().when(provider.defaultOptions()).thenReturn(new ModelRequest.Options("test-model", 2000, 0.7));
        lenient().when(provider.complete(any(), any())).thenAnswer(call -> {
            ModelRequest request = call.getArgument(0);
            return result(provider.chat(request.messages().get(0).content(), request.messages().get(1).content()));
        });
        lenient().when(provider.stream(any(), any(), any())).thenAnswer(call -> {
            ModelRequest request = call.getArgument(0);
            Consumer<ModelEvent> events = call.getArgument(1);
            var content = new StringBuilder();
            provider.chatStream(request.messages().get(0).content(), request.messages().get(1).content(), chunk -> {
                content.append(chunk);
                events.accept(new ModelEvent.TextDelta(chunk));
            });
            return result(content.toString());
        });
        lenient().doAnswer(call -> {
            AiCallContext context = call.getArgument(0);
            AiCallLog log = new AiCallLog();
            log.setUserId(context.userId());
            log.setFunctionType(context.function());
            return new AiCallTicket(log, Map.of());
        }).when(governance).begin(any(), any());
    }

    static void assertCall(AiCallGovernanceService governance, Long userId, String function, String outcome) {
        verify(governance).finish(argThat(ticket -> ticket.entry().getUserId().equals(userId)
                && ticket.entry().getFunctionType().equals(function)), any(), eq(outcome), anyLong());
    }

    private static ModelResult result(String text) {
        return new ModelResult(text, List.of(), "test-model", null, ModelResult.Finish.STOP, null);
    }
}
