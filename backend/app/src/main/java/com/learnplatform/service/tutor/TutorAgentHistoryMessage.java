package com.learnplatform.service.tutor;

import com.learnplatform.ai.model.ModelRequest;

public record TutorAgentHistoryMessage(ModelRequest.Role role, String content) {
    public TutorAgentHistoryMessage {
        if (role != ModelRequest.Role.USER && role != ModelRequest.Role.ASSISTANT) {
            throw new IllegalArgumentException("Tutor Agent history only stores visible messages");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Tutor Agent message content is required");
        }
    }
}
