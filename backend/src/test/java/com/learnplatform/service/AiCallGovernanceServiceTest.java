package com.learnplatform.service;

import com.learnplatform.config.AiConfig;
import com.learnplatform.entity.AiCallLog;
import com.learnplatform.entity.User;
import com.learnplatform.mapper.AiCallLogMapper;
import com.learnplatform.mapper.UserMapper;
import com.learnplatform.service.ai.AiProvider;
import com.learnplatform.service.ai.AiCostCalculator;
import com.learnplatform.service.ai.AiTokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiCallGovernanceServiceTest {

    @Mock private AiProvider aiProvider;
    @Mock private AiCostCalculator aiCostCalculator;
    @Mock private AiConfig aiConfig;
    @Mock private AiCallLogMapper aiCallLogMapper;
    @Mock private UserMapper userMapper;
    @InjectMocks private AiCallGovernanceService callGovernanceService;

    @Test
    void recordsExactUpstreamTotalTokensForSuccessfulCall() {
        when(aiConfig.getModel()).thenReturn("gpt-4o-mini");
        when(aiProvider.getLastTokenUsage()).thenReturn(new AiTokenUsage(12, 8, 20));
        when(aiCostCalculator.calculate(eq("gpt-4o-mini"), any())).thenReturn(new java.math.BigDecimal("0.00000660"));

        callGovernanceService.logCall(7L, "explanation", true, null, 123);

        ArgumentCaptor<AiCallLog> captor = ArgumentCaptor.forClass(AiCallLog.class);
        verify(aiCallLogMapper).insert(captor.capture());
        assertEquals(20, captor.getValue().getTokensUsed());
        assertEquals(12, captor.getValue().getPromptTokens());
        assertEquals(8, captor.getValue().getCompletionTokens());
        assertEquals(new java.math.BigDecimal("0.00000660"), captor.getValue().getCostUsd());
        assertEquals("gpt-4o-mini", captor.getValue().getModel());
    }

    @Test
    void recordsRequestTraceIdWhenAvailable() {
        when(aiConfig.getModel()).thenReturn("gpt-4o-mini");
        MDC.put("traceId", "a1b2c3d4");
        try {
            callGovernanceService.logCall(7L, "explanation", true, null, 123);
        } finally {
            MDC.clear();
        }

        ArgumentCaptor<AiCallLog> captor = ArgumentCaptor.forClass(AiCallLog.class);
        verify(aiCallLogMapper).insert(captor.capture());
        assertEquals("a1b2c3d4", captor.getValue().getTraceId());
    }

    @Test
    void recordsPromptAndModelTraceMetadataWithoutRawPromptContent() {
        when(aiConfig.getModel()).thenReturn("gpt-4o-mini");
        when(aiConfig.getMaxTokens()).thenReturn(2000);
        when(aiConfig.isStreamIncludeUsage()).thenReturn(true);
        when(aiConfig.getModelPrices()).thenReturn(Collections.emptyMap());
        callGovernanceService.logCallWithPrompt(7L, "summary", true, null, 123,
                "system", "user");

        ArgumentCaptor<AiCallLog> captor = ArgumentCaptor.forClass(AiCallLog.class);
        verify(aiCallLogMapper).insert(captor.capture());
        AiCallLog log = captor.getValue();
        assertEquals("summary", log.getPromptTemplate());
        assertEquals("7b5a886fdc40f9bfe8fae3663a19b14dd5c6f90c004b929fe75e428038106da0",
                log.getPromptHash());
        assertEquals("84aac6ab73e7f1455476a1d77eb1c7a4775b6ce130cbc69017916b8a822f20a9",
                log.getModelConfigVersion());
        assertFalse(log.getPromptHash().contains("system"));
    }

    @Test
    void hashesPartiallyMissingPromptLikePreviousAiPromptBoundary() {
        when(aiConfig.getModel()).thenReturn("gpt-4o-mini");

        callGovernanceService.logCallWithPrompt(7L, "summary", true, null, 123, null, "user");

        ArgumentCaptor<AiCallLog> captor = ArgumentCaptor.forClass(AiCallLog.class);
        verify(aiCallLogMapper).insert(captor.capture());
        assertEquals("ab0684db6102a3c5fe10451340e13d63cf7256fd308ff9d00c46f24c2dbfa72c",
                captor.getValue().getPromptHash());
    }

    @Test
    void doesNotAssignTokensToFailedCall() {
        callGovernanceService.logCall(7L, "explanation", false, "timeout", 123);

        ArgumentCaptor<AiCallLog> captor = ArgumentCaptor.forClass(AiCallLog.class);
        verify(aiCallLogMapper).insert(captor.capture());
        assertNull(captor.getValue().getTokensUsed());
        verify(aiProvider, never()).getLastTokenUsage();
    }

    @Test
    void auditWriteFailureDoesNotOverrideBusinessResult() {
        when(aiConfig.getModel()).thenReturn("gpt-4o-mini");
        doThrow(new IllegalStateException("database unavailable")).when(aiCallLogMapper).insert(any());

        assertDoesNotThrow(() -> callGovernanceService.logCall(7L, "explanation", true, null, 123));
    }

    @Test
    void userQuotaOverridesGlobalQuota() {
        User user = new User();
        user.setAiDailyQuota(2);
        when(userMapper.selectById(7L)).thenReturn(user);
        when(aiCallLogMapper.selectCount(any())).thenReturn(2L);

        com.learnplatform.common.exception.BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
                com.learnplatform.common.exception.BusinessException.class,
                () -> callGovernanceService.checkDailyQuota(7L));

        assertEquals("今日 AI 调用次数已达上限（2 次），请明天再试", exception.getMessage());
        verify(aiConfig, never()).getDailyQuota();
    }

    @Test
    void nullUserQuotaInheritsGlobalQuotaForUsage() {
        User user = new User();
        user.setAiDailyQuota(null);
        when(userMapper.selectById(7L)).thenReturn(user);
        when(aiCallLogMapper.selectCount(any())).thenReturn(3L);
        when(aiConfig.getDailyQuota()).thenReturn(50);

        assertEquals(3, callGovernanceService.getDailyUsage(7L)[0]);
        assertEquals(50, callGovernanceService.getDailyUsage(7L)[1]);
    }
}
