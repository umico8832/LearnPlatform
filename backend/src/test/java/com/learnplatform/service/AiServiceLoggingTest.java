package com.learnplatform.service;

import com.learnplatform.config.AiConfig;
import com.learnplatform.entity.AiCallLog;
import com.learnplatform.mapper.*;
import com.learnplatform.service.ai.AiProvider;
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
    @Mock private AiConfig aiConfig;
    @Mock private AiCallLogMapper aiCallLogMapper;
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

        aiService.logCall(7L, "explanation", true, null, 123);

        ArgumentCaptor<AiCallLog> captor = ArgumentCaptor.forClass(AiCallLog.class);
        verify(aiCallLogMapper).insert(captor.capture());
        assertEquals(20, captor.getValue().getTokensUsed());
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
}
