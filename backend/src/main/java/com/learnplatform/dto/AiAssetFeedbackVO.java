package com.learnplatform.dto;

import com.learnplatform.entity.AiAssetFeedback;

public record AiAssetFeedbackVO(Boolean helpful, String comment) {

    public static AiAssetFeedbackVO fromEntity(AiAssetFeedback entity) {
        if (entity == null) {
            return null;
        }
        return new AiAssetFeedbackVO(entity.getHelpful(), entity.getComment() == null ? "" : entity.getComment());
    }

    public Boolean getHelpful() { return helpful; }
    public String getComment() { return comment; }
}
