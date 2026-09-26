package com.learnplatform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TutorAgentActionVO(String type, Integer level, Long questionId, List<TutorAgentPlanStepVO> steps) {
    public TutorAgentActionVO(String type) {
        this(type, null, null, null);
    }

    public TutorAgentActionVO(String type, Integer level) {
        this(type, level, null, null);
    }

    public TutorAgentActionVO(String type, Integer level, Long questionId) {
        this(type, level, questionId, null);
    }

    public TutorAgentActionVO {
        if ("CHECK".equals(type)) {
            if (level != null || questionId != null || steps != null) {
                throw new IllegalArgumentException("Invalid Tutor teaching action fields");
            }
        } else if ("HINT".equals(type)) {
            if (level == null || level < 1 || level > 3 || questionId != null || steps != null) {
                throw new IllegalArgumentException("Invalid Tutor teaching action fields");
            }
        } else if ("PRACTICE".equals(type)) {
            if (level != null || questionId == null || questionId <= 0 || steps != null) {
                throw new IllegalArgumentException("Invalid Tutor teaching action fields");
            }
        } else if ("PLAN".equals(type)) {
            if (level != null || questionId != null || steps == null || steps.isEmpty() || steps.size() > 3) {
                throw new IllegalArgumentException("Invalid Tutor plan action fields");
            }
            steps = List.copyOf(steps);
            if (steps.stream().map(step -> step.type() + ":" + step.knowledgePointId() + ":" + step.questionId())
                    .distinct().count() != steps.size()) {
                throw new IllegalArgumentException("Duplicate Tutor plan targets");
            }
        } else {
            throw new IllegalArgumentException("Unsupported Tutor teaching action");
        }
    }
}
