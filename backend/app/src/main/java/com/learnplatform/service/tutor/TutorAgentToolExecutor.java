package com.learnplatform.service.tutor;

import com.learnplatform.ai.model.ModelRequest;

public interface TutorAgentToolExecutor {
    String execute(Long userId, Long courseId, String sessionKey, ModelRequest.ToolCall call);
}
