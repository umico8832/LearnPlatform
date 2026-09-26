package com.learnplatform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** A server-selected next learning target proposed by the Tutor Agent. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TutorAgentPlanStepVO(String type, String title, String reason,
                                   Long knowledgePointId, Long questionId) {
    public TutorAgentPlanStepVO {
        if (title == null || title.isBlank() || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Tutor plan step requires title and reason");
        }
        boolean knowledgePointTarget = "TUTOR".equals(type) || "COURSE_SEQUENCE".equals(type);
        boolean questionTarget = "DUE_REVIEW".equals(type) || "WRONG_QUESTION".equals(type);
        if (knowledgePointTarget && (knowledgePointId == null || knowledgePointId <= 0 || questionId != null)) {
            throw new IllegalArgumentException("Invalid Tutor plan knowledge point target");
        }
        if (questionTarget && (questionId == null || questionId <= 0 || knowledgePointId != null)) {
            throw new IllegalArgumentException("Invalid Tutor plan question target");
        }
        if (!knowledgePointTarget && !questionTarget) {
            throw new IllegalArgumentException("Unsupported Tutor plan step target");
        }
    }
}
