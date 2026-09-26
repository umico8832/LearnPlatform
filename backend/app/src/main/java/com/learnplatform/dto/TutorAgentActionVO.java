package com.learnplatform.dto;

public record TutorAgentActionVO(String type) {
    public TutorAgentActionVO {
        if (!"CHECK".equals(type)) {
            throw new IllegalArgumentException("Unsupported Tutor teaching action");
        }
    }
}
