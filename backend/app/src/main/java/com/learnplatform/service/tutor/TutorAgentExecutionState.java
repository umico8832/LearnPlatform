package com.learnplatform.service.tutor;

import java.util.List;
import java.util.UUID;

public record TutorAgentExecutionState(Long id, UUID runId, List<TutorAgentHistoryMessage> history) {
    public TutorAgentExecutionState {
        history = List.copyOf(history);
    }
}
