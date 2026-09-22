package com.learnplatform.service.tutor;

import com.learnplatform.ai.model.ModelRequest;

import java.util.UUID;

public interface TutorAgentToolExecutor {
    default boolean supportsKnowledgeSearch() { return false; }

    default String execute(Long userId, Long courseId, String sessionKey, ModelRequest.ToolCall call, UUID runId) {
        return execute(userId, courseId, sessionKey, call);
    }

    String execute(Long userId, Long courseId, String sessionKey, ModelRequest.ToolCall call);
}
