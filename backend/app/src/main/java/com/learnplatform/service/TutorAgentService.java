package com.learnplatform.service;

import com.learnplatform.ai.model.ModelException;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.TutorAgentMessageRequest;
import com.learnplatform.dto.TutorAgentRunVO;
import com.learnplatform.service.tutor.TutorAgentExecutionState;
import com.learnplatform.service.tutor.TutorAgentRuntime;
import org.springframework.stereotype.Service;

@Service
public class TutorAgentService {
    private final TutorAgentRunStateService states;
    private final TutorAgentRuntime runtime;

    public TutorAgentService(TutorAgentRunStateService states, TutorAgentRuntime runtime) {
        this.states = states;
        this.runtime = runtime;
    }

    public TutorAgentRunVO start(Long userId, Long courseId, String sessionKey,
                                 TutorAgentMessageRequest request) {
        String question = normalize(request);
        return execute(userId, courseId, sessionKey, states.begin(userId, courseId, sessionKey), question);
    }

    public TutorAgentRunVO resume(Long userId, Long courseId, String sessionKey, String runKey,
                                  TutorAgentMessageRequest request) {
        String question = normalize(request);
        return execute(userId, courseId, sessionKey,
                states.resume(userId, courseId, sessionKey, runKey), question);
    }

    public TutorAgentRunVO get(Long userId, Long courseId, String sessionKey, String runKey) {
        return states.get(userId, courseId, sessionKey, runKey);
    }

    private TutorAgentRunVO execute(Long userId, Long courseId, String sessionKey,
                                    TutorAgentExecutionState state, String question) {
        try {
            String answer = runtime.respond(userId, courseId, sessionKey, state.runId(),
                    state.history(), question);
            return states.complete(state, question, answer);
        } catch (RuntimeException exception) {
            states.fail(state.id());
            if (exception instanceof ModelException modelException) {
                throw modelFailure(modelException);
            }
            throw exception;
        }
    }

    private String normalize(TutorAgentMessageRequest request) {
        if (request == null || request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "问题不能为空");
        }
        return request.getMessage().trim();
    }

    private BusinessException modelFailure(ModelException exception) {
        String message = switch (exception.code()) {
            case CONFIGURATION -> "AI 服务尚未配置完成";
            case UNSUPPORTED -> "当前 AI 模型不支持 Tutor Agent 工具调用";
            case TIMEOUT -> "Tutor Agent 响应超时，请稍后重试";
            case CANCELLED -> "Tutor Agent 请求已取消";
            case REFUSAL -> "Tutor Agent 未能提供此内容，请调整问题后重试";
            case TRUNCATED -> "Tutor Agent 回答未完整生成，请重试";
            case PROTOCOL, SCHEMA -> "Tutor Agent 未能生成可验证的回答，请重试";
            default -> "Tutor Agent 暂时不可用，请稍后重试";
        };
        return new BusinessException(ResultCode.BUSINESS_ERROR, message);
    }
}
