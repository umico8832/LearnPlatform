package com.learnplatform.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.TutorAgentRunVO;
import com.learnplatform.entity.TutorAgentMessage;
import com.learnplatform.entity.TutorAgentRun;
import com.learnplatform.entity.TutorSession;
import com.learnplatform.mapper.TutorAgentMessageMapper;
import com.learnplatform.mapper.TutorAgentRunMapper;
import com.learnplatform.mapper.TutorSessionMapper;
import com.learnplatform.service.tutor.TutorAgentExecutionState;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TutorAgentRunStateServiceTest {
    private final TutorSessionMapper sessions = mock(TutorSessionMapper.class);
    private final TutorAgentRunMapper runs = mock(TutorAgentRunMapper.class);
    private final TutorAgentMessageMapper messages = mock(TutorAgentMessageMapper.class);
    private TutorAgentRunStateService service;

    @BeforeEach void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), TutorAgentRun.class);
        service = new TutorAgentRunStateService(sessions, runs, messages);
    }

    @Test void createsARunningRunBoundToTheOwnedTutorSession() {
        when(sessions.selectOne(any())).thenReturn(session());
        doAnswer(call -> {
            ((TutorAgentRun) call.getArgument(0)).setId(5L);
            return 1;
        }).when(runs).insert(any());

        TutorAgentExecutionState state = service.begin(7L, 10L, "session");

        assertEquals(5L, state.id());
        ArgumentCaptor<TutorAgentRun> stored = ArgumentCaptor.forClass(TutorAgentRun.class);
        verify(runs).insert(stored.capture());
        assertEquals("RUNNING", stored.getValue().getStatus());
        assertEquals(30L, stored.getValue().getTutorSessionId());
        assertEquals(7L, stored.getValue().getUserId());
    }

    @Test void refusesToClaimARunThatIsAlreadyProcessing() {
        when(sessions.selectOne(any())).thenReturn(session());
        when(runs.selectOne(any())).thenReturn(run());
        when(runs.update(any(), any())).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.resume(7L, 10L, "session", "run"));
    }

    @Test void appendsAUserAssistantPairAndPausesAtTheUserBoundary() {
        TutorAgentRun run = run();
        when(runs.selectById(5L)).thenReturn(run);
        when(runs.update(any(), any())).thenReturn(1);
        TutorAgentMessage user = message(1, "USER", "问题");
        TutorAgentMessage assistant = message(2, "ASSISTANT", "回答");
        when(messages.selectList(any())).thenReturn(List.of(user, assistant));
        TutorAgentExecutionState state = new TutorAgentExecutionState(5L,
                java.util.UUID.fromString(run.getRunKey()), List.of());

        TutorAgentRunVO result = service.complete(state, "问题", "回答");

        assertEquals("WAITING_USER", result.getStatus());
        assertEquals(List.of("USER", "ASSISTANT"),
                result.getMessages().stream().map(item -> item.getRole()).toList());
        ArgumentCaptor<TutorAgentMessage> stored = ArgumentCaptor.forClass(TutorAgentMessage.class);
        verify(messages, times(2)).insert(stored.capture());
        assertEquals(List.of(1, 2), stored.getAllValues().stream().map(TutorAgentMessage::getSequenceNo).toList());
    }

    @Test void hidesRunsWhenTheTutorSessionDoesNotBelongToTheRequester() {
        when(sessions.selectOne(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.get(8L, 10L, "session", "run"));
    }

    private TutorSession session() {
        TutorSession session = new TutorSession();
        session.setId(30L);
        session.setUserId(7L);
        session.setCourseId(10L);
        return session;
    }

    private TutorAgentRun run() {
        TutorAgentRun run = new TutorAgentRun();
        run.setId(5L);
        run.setRunKey("69af726c-2a51-443f-a475-bab94530748e");
        run.setTutorSessionId(30L);
        run.setUserId(7L);
        run.setStatus("RUNNING");
        run.setNextSequence(1);
        return run;
    }

    private TutorAgentMessage message(int sequence, String role, String content) {
        TutorAgentMessage message = new TutorAgentMessage();
        message.setRunId(5L);
        message.setSequenceNo(sequence);
        message.setRole(role);
        message.setContent(content);
        return message;
    }
}
