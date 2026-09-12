package com.learnplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AiVariantReviewRequest {
    @NotBlank(message = "审查结论不能为空")
    @Pattern(regexp = "APPROVE|REJECT", message = "审查结论不合法")
    private String decision;

    @Size(max = 500, message = "审查说明不能超过500个字符")
    private String reviewNote;

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
}
