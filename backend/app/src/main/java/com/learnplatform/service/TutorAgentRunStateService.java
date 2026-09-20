package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.learnplatform.ai.model.ModelRequest;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.TutorAgentMessageVO;
import com.learnplatform.dto.TutorAgentRunVO;
import com.learnplatform.entity.TutorAgentMessage;
import com.learnplatform.entity.TutorAgentRun;
import com.learnplatform.entity.TutorSession;
import com.learnplatform.mapper.TutorAgentMessageMapper;
import com.learnplatform.mapper.TutorAgentRunMapper;
import com.learnplatform.mapper.TutorSessionMapper;
import com.learnplatform.service.tutor.TutorAgentExecutionState;
import com.learnplatform.service.tutor.TutorAgentHistoryMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TutorAgentRunStateService {
    private static final String RUNNING = "RUNNING";
    private static final String WAITING_USER = "WAITING_USER";
    private static final String FAILED = "FAILED";

    private final TutorSessionMapper sessions;
    private final TutorAgentRunMapper runs;
    private final TutorAgentMessageMapper messages;

    public TutorAgentRunStateService(TutorSessionMapper sessions, TutorAgentRunMapper runs,
                                     TutorAgentMessageMapper messages) {
        this.sessions = sessions;
        this.runs = runs;
        this.messages = messages;
    }

    @Transactional
    public TutorAgentExecutionState begin(Long userId, Long courseId, String sessionKey) {
        TutorSession session = requireSession(userId, courseId, sessionKey);
        TutorAgentRun run = new TutorAgentRun();
        run.setRunKey(UUID.randomUUID().toString());
        run.setTutorSessionId(session.getId());
        run.setUserId(userId);
        run.setStatus(RUNNING);
        run.setNextSequence(1);
        runs.insert(run);
        return state(run, List.of());
    }

    @Transactional
    public TutorAgentExecutionState resume(Long userId, Long courseId, String sessionKey, String runKey) {
        TutorSession session = requireSession(userId, courseId, sessionKey);
        TutorAgentRun run = requireRun(userId, session.getId(), runKey);
        int updated = runs.update(null, new LambdaUpdateWrapper<TutorAgentRun>()
                .eq(TutorAgentRun::getId, run.getId())
                .in(TutorAgentRun::getStatus, List.of(WAITING_USER, FAILED))
                .set(TutorAgentRun::getStatus, RUNNING));
        if (updated == 0) {
            throw new BusinessException(ResultCode.RATE_LIMITED, "Tutor Agent 正在处理上一条消息");
        }
        run.setStatus(RUNNING);
        return state(run, listMessages(run.getId()));
    }

    @Transactional
    public TutorAgentRunVO complete(TutorAgentExecutionState state, String question, String answer) {
        TutorAgentRun run = runs.selectById(state.id());
        if (run == null || !RUNNING.equals(run.getStatus())) {
            throw new BusinessException(ResultCode.RATE_LIMITED, "Tutor Agent 运行状态已变化");
        }
        int sequence = run.getNextSequence();
        insertMessage(run.getId(), sequence, "USER", question);
        insertMessage(run.getId(), sequence + 1, "ASSISTANT", answer);
        int updated = runs.update(null, new LambdaUpdateWrapper<TutorAgentRun>()
                .eq(TutorAgentRun::getId, run.getId())
                .eq(TutorAgentRun::getStatus, RUNNING)
                .eq(TutorAgentRun::getNextSequence, sequence)
                .set(TutorAgentRun::getNextSequence, sequence + 2)
                .set(TutorAgentRun::getStatus, WAITING_USER));
        if (updated == 0) {
            throw new BusinessException(ResultCode.RATE_LIMITED, "Tutor Agent 运行状态已变化");
        }
        run.setStatus(WAITING_USER);
        run.setNextSequence(sequence + 2);
        return view(run, listMessages(run.getId()));
    }

    public void fail(Long runId) {
        runs.update(null, new LambdaUpdateWrapper<TutorAgentRun>()
                .eq(TutorAgentRun::getId, runId).eq(TutorAgentRun::getStatus, RUNNING)
                .set(TutorAgentRun::getStatus, FAILED));
    }

    public TutorAgentRunVO get(Long userId, Long courseId, String sessionKey, String runKey) {
        TutorSession session = requireSession(userId, courseId, sessionKey);
        TutorAgentRun run = requireRun(userId, session.getId(), runKey);
        return view(run, listMessages(run.getId()));
    }

    private TutorSession requireSession(Long userId, Long courseId, String sessionKey) {
        TutorSession session = sessions.selectOne(new QueryWrapper<TutorSession>()
                .eq("session_key", sessionKey).eq("user_id", userId).eq("course_id", courseId));
        if (session == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Tutor 会话不存在");
        }
        return session;
    }

    private TutorAgentRun requireRun(Long userId, Long tutorSessionId, String runKey) {
        TutorAgentRun run = runs.selectOne(new QueryWrapper<TutorAgentRun>()
                .eq("run_key", runKey).eq("user_id", userId).eq("tutor_session_id", tutorSessionId));
        if (run == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Tutor Agent 运行不存在");
        }
        return run;
    }

    private List<TutorAgentMessage> listMessages(Long runId) {
        return messages.selectList(new QueryWrapper<TutorAgentMessage>()
                .eq("run_id", runId).orderByAsc("sequence_no"));
    }

    private TutorAgentExecutionState state(TutorAgentRun run, List<TutorAgentMessage> stored) {
        List<TutorAgentHistoryMessage> history = stored.stream().map(message -> new TutorAgentHistoryMessage(
                ModelRequest.Role.valueOf(message.getRole()), message.getContent())).toList();
        return new TutorAgentExecutionState(run.getId(), UUID.fromString(run.getRunKey()), history);
    }

    private void insertMessage(Long runId, int sequence, String role, String content) {
        TutorAgentMessage message = new TutorAgentMessage();
        message.setRunId(runId);
        message.setSequenceNo(sequence);
        message.setRole(role);
        message.setContent(content);
        messages.insert(message);
    }

    private TutorAgentRunVO view(TutorAgentRun run, List<TutorAgentMessage> stored) {
        TutorAgentRunVO result = new TutorAgentRunVO();
        result.setRunKey(run.getRunKey());
        result.setStatus(run.getStatus());
        result.setMessages(stored.stream().map(message -> {
            TutorAgentMessageVO item = new TutorAgentMessageVO();
            item.setSequence(message.getSequenceNo());
            item.setRole(message.getRole());
            item.setContent(message.getContent());
            item.setCreateTime(message.getCreateTime());
            return item;
        }).toList());
        return result;
    }
}
