package com.learnplatform.service.tutor;

import com.learnplatform.ai.model.ModelRequest;
import com.learnplatform.dto.TutorAgentActionVO;

import java.util.List;

public record TutorAgentHistoryMessage(ModelRequest.Role role, String content, List<TutorAgentActionVO> actions) {
    public TutorAgentHistoryMessage(ModelRequest.Role role, String content) {
        this(role, content, List.of());
    }

    public TutorAgentHistoryMessage {
        if (role != ModelRequest.Role.USER && role != ModelRequest.Role.ASSISTANT) {
            throw new IllegalArgumentException("Tutor Agent history only stores visible messages");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Tutor Agent message content is required");
        }
        actions = List.copyOf(actions);
        if (role != ModelRequest.Role.ASSISTANT && !actions.isEmpty()) {
            throw new IllegalArgumentException("Only Tutor responses can offer actions");
        }
    }
}
