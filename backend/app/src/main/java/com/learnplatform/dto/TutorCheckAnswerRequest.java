package com.learnplatform.dto;

import jakarta.validation.constraints.NotBlank;

public class TutorCheckAnswerRequest {
    @NotBlank private String optionId;

    public String getOptionId() { return optionId; }
    public void setOptionId(String value) { optionId = value; }
}
