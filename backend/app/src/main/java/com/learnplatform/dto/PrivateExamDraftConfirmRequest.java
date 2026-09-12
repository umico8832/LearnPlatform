package com.learnplatform.dto;

import jakarta.validation.constraints.AssertTrue;

public class PrivateExamDraftConfirmRequest {
    @AssertTrue(message = "必须显式确认启用试卷")
    private Boolean confirmed;

    public Boolean getConfirmed() { return confirmed; }
    public void setConfirmed(Boolean confirmed) { this.confirmed = confirmed; }
}
