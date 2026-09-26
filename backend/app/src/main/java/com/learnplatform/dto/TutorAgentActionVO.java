package com.learnplatform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TutorAgentActionVO(String type, Integer level, Long questionId) {
    public TutorAgentActionVO(String type) {
        this(type, null, null);
    }

    public TutorAgentActionVO(String type, Integer level) {
        this(type, level, null);
    }

    public TutorAgentActionVO {
        if ("CHECK".equals(type)) {
            if (level != null || questionId != null) {
                throw new IllegalArgumentException("Invalid Tutor teaching action fields");
            }
        } else if ("HINT".equals(type)) {
            if (level == null || level < 1 || level > 3 || questionId != null) {
                throw new IllegalArgumentException("Invalid Tutor teaching action fields");
            }
        } else if ("PRACTICE".equals(type)) {
            if (level != null || questionId == null || questionId <= 0) {
                throw new IllegalArgumentException("Invalid Tutor teaching action fields");
            }
        } else {
            throw new IllegalArgumentException("Unsupported Tutor teaching action");
        }
    }
}
