package com.learnplatform.service.ai;

import java.util.UUID;

public record AiCallContext(Long userId, String function, UUID runId) {
    public AiCallContext {
        if (userId == null || userId <= 0 || function == null || !function.matches("[a-z][a-z0-9_]{0,49}")) {
            throw new IllegalArgumentException("Authenticated user and function are required");
        }
    }
}
