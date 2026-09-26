package com.learnplatform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TutorAgentActionVO(String type, Integer level) {
    public TutorAgentActionVO(String type) {
        this(type, null);
    }

    public TutorAgentActionVO {
        if ("CHECK".equals(type)) {
            if (level != null) {
                throw new IllegalArgumentException("Invalid Tutor teaching action fields");
            }
        } else if ("HINT".equals(type)) {
            if (level == null || level < 1 || level > 3) {
                throw new IllegalArgumentException("Invalid Tutor teaching action fields");
            }
        } else {
            throw new IllegalArgumentException("Unsupported Tutor teaching action");
        }
    }
}
