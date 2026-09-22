package com.learnplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.ai.model.EmbeddingRequest;
import com.learnplatform.ai.model.ModelRequest;
import com.learnplatform.ai.model.ModelResult;
import com.learnplatform.config.AiConfig;
import com.learnplatform.entity.AiCallLog;
import com.learnplatform.entity.User;
import com.learnplatform.mapper.AiCallLogMapper;
import com.learnplatform.mapper.UserMapper;
import com.learnplatform.service.ai.AiCallContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiCallGovernanceServiceTest {
    @Mock private AiCallReservationService reservations;
    @Mock private AiCallLogMapper logs;
    @Mock private UserMapper users;
    private final AiConfig config = new AiConfig();
    private AiCallGovernanceService governance;
    private final ModelRequest request = ModelRequest.text("private system", "private user",
            new ModelRequest.Options("requested-model", 200, 0.7));
    private final AiCallContext context = new AiCallContext(7L, "explanation", UUID.randomUUID());

    @BeforeEach void setUp() {
        governance = new AiCallGovernanceService(config, logs, users, reservations, new ObjectMapper());
    }

    @Test void reservesTraceRunAndFingerprintsWithoutRawContent() {
        MDC.put("traceId", "a1b2c3d4");
        try {
            var ticket = governance.begin(context, request);
            AiCallLog entry = ticket.entry();
            verify(reservations).reserve(entry);
            assertEquals("a1b2c3d4", entry.getTraceId());
            assertEquals(context.runId().toString(), entry.getRunId());
            assertEquals("RUNNING", entry.getOutcome());
            assertEquals(64, entry.getPromptHash().length());
            assertFalse(entry.getPromptHash().contains("private"));
            assertNotNull(UUID.fromString(entry.getCallId()));
            var changed = new ModelRequest(request.messages(), new ModelRequest.Options("requested-model", 200, 0.2),
                    List.of(), null);
            assertNotEquals(entry.getModelConfigVersion(), governance.begin(context, changed).entry().getModelConfigVersion());
        } finally { MDC.clear(); }
    }

    @Test void recordsBilledUsageEvenIfResultFailsValidationAndUsesPriceSnapshot() {
        var price = new AiConfig.ModelPrice();
        price.setInputPerMillion(BigDecimal.ONE);
        price.setOutputPerMillion(BigDecimal.valueOf(2));
        config.getModelPrices().put("actual-model", price);
        var ticket = governance.begin(context, request);
        price.setInputPerMillion(BigDecimal.valueOf(100));
        var result = new ModelResult("invalid json", List.of(), "actual-model", "r1", ModelResult.Finish.STOP,
                new ModelResult.Usage(12, 8, 20));
        governance.finish(ticket, result, "SCHEMA", 100);
        verify(reservations).finish(ticket.entry());
        assertEquals(20, ticket.entry().getTokensUsed());
        assertEquals(new BigDecimal("0.00002800"), ticket.entry().getCostUsd());
        assertEquals("actual-model", ticket.entry().getModel());
        assertEquals("requested-model", ticket.entry().getRequestedModel());
        assertEquals("AI_SCHEMA", ticket.entry().getErrorMessage());
        assertEquals(0, ticket.entry().getStatus());
    }

    @Test void unknownUsageRemainsNullAndAuditFailureDoesNotReplacePrimaryResult() {
        var ticket = governance.begin(context, request);
        doThrow(new IllegalStateException("database unavailable")).when(reservations).finish(any());
        assertDoesNotThrow(() -> governance.finish(ticket, null, "TIMEOUT", 200));
        assertNull(ticket.entry().getTokensUsed());
        assertNull(ticket.entry().getCostUsd());
    }

    @Test void userQuotaOverridesGlobalQuota() {
        User user = new User();
        user.setAiDailyQuota(2);
        when(users.selectById(7L)).thenReturn(user);
        when(logs.selectCount(any())).thenReturn(2L);
        var error = assertThrows(com.learnplatform.common.exception.BusinessException.class,
                () -> governance.checkDailyQuota(7L));
        assertEquals(1006, error.getCode());
    }

    @Test void nullUserQuotaInheritsGlobalQuotaForUsage() {
        when(users.selectById(7L)).thenReturn(new User());
        when(logs.selectCount(any())).thenReturn(3L);
        assertArrayEquals(new int[]{3, 50}, governance.getDailyUsage(7L));
    }

    @Test void embeddingAuditHashesInputsAndOnlyBillsInputTokens() {
        var price = new AiConfig.ModelPrice();
        price.setInputPerMillion(BigDecimal.ONE);
        config.getModelPrices().put("embedding-model", price);
        var request = new EmbeddingRequest("embedding-model", List.of("private chunk"), 3);

        var ticket = governance.beginEmbedding(context, request);
        var entry = ticket.entry();
        governance.finish(ticket, new ModelResult(null, List.of(), "embedding-model", "e1",
                ModelResult.Finish.STOP, new ModelResult.Usage(12, null, 12)), "SUCCEEDED", 100);

        assertEquals("EMBEDDING", entry.getCallKind());
        assertEquals("embedding-model", entry.getRequestedModel());
        assertEquals(64, entry.getPromptHash().length());
        assertFalse(entry.getPromptHash().contains("private"));
        assertEquals(new BigDecimal("0.00001200"), entry.getCostUsd());
        verify(reservations).reserve(entry);
        verify(reservations).finish(entry);
    }

    @Test void embeddingConfigFingerprintDoesNotChangeWithInputText() {
        var first = governance.beginEmbedding(context,
                new EmbeddingRequest("embedding-model", List.of("first text"), 3)).entry();
        var second = governance.beginEmbedding(context,
                new EmbeddingRequest("embedding-model", List.of("second text"), 3)).entry();

        assertEquals(first.getModelConfigVersion(), second.getModelConfigVersion());
        assertNotEquals(first.getPromptHash(), second.getPromptHash());
    }
}
