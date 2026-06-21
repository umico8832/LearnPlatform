package com.learnplatform.service;

import com.learnplatform.config.AiConfig;
import com.learnplatform.entity.AiCallLog;
import com.learnplatform.mapper.*;
import com.learnplatform.entity.User;
import com.learnplatform.service.ai.AiProvider;
import com.learnplatform.service.ai.AiCostCalculator;
import com.learnplatform.service.ai.AiTokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiServiceLoggingTest {

    @Mock private AiProvider aiProvider;
    @Mock private AiCostCalculator aiCostCalculator;
    @Mock private AiConfig aiConfig;
    @Mock private AiCallLogMapper aiCallLogMapper;
    @Mock private UserMapper userMapper;
    @Mock private QuestionMapper questionMapper;
    @Mock private QuestionOptionMapper questionOptionMapper;
    @Mock private QuestionKnowledgePointMapper questionKnowledgePointMapper;
    @Mock private KnowledgePointMapper knowledgePointMapper;
    @Mock private CourseMapper courseMapper;
    @Mock private WrongQuestionMapper wrongQuestionMapper;
    @InjectMocks private AiService aiService;

    @Test
    void recordsExactUpstreamTotalTokensForSuccessfulCall() {
        when(aiConfig.getModel()).thenReturn("gpt-4o-mini");
        when(aiProvider.getLastTokenUsage()).thenReturn(new AiTokenUsage(12, 8, 20));
        when(aiCostCalculator.calculate(eq("gpt-4o-mini"), any())).thenReturn(new java.math.BigDecimal("0.00000660"));

        aiService.logCall(7L, "explanation", true, null, 123);

        ArgumentCaptor<AiCallLog> captor = ArgumentCaptor.forClass(AiCallLog.class);
        verify(aiCallLogMapper).insert(captor.capture());
        assertEquals(20, captor.getValue().getTokensUsed());
        assertEquals(12, captor.getValue().getPromptTokens());
        assertEquals(8, captor.getValue().getCompletionTokens());
        assertEquals(new java.math.BigDecimal("0.00000660"), captor.getValue().getCostUsd());
        assertEquals("gpt-4o-mini", captor.getValue().getModel());
    }

    @Test
    void doesNotAssignTokensToFailedCall() {
        aiService.logCall(7L, "explanation", false, "timeout", 123);

        ArgumentCaptor<AiCallLog> captor = ArgumentCaptor.forClass(AiCallLog.class);
        verify(aiCallLogMapper).insert(captor.capture());
        assertNull(captor.getValue().getTokensUsed());
        verify(aiProvider, never()).getLastTokenUsage();
    }

    @Test
    void userQuotaOverridesGlobalQuota() {
        User user = new User();
        user.setAiDailyQuota(2);
        when(userMapper.selectById(7L)).thenReturn(user);
        when(aiCallLogMapper.selectCount(any())).thenReturn(2L);

        com.learnplatform.common.exception.BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
                com.learnplatform.common.exception.BusinessException.class,
                () -> aiService.checkDailyQuota(7L));

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

        assertEquals(3, aiService.getDailyUsage(7L)[0]);
        assertEquals(50, aiService.getDailyUsage(7L)[1]);
    }
}
