package com.learnplatform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/** Stored Tutor plan preview and its explicit user confirmation state. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record TutorAgentPlanVO(List<TutorAgentPlanStepVO> steps, boolean confirmed,
                               LocalDateTime confirmedAt, boolean available) {
    public TutorAgentPlanVO {
        steps = List.copyOf(steps);
    }
}
