package com.learnplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class TutorAgentMessageRequest {
    @NotBlank(message = "问题不能为空")
    @Size(max = 2000, message = "问题不能超过2000个字符")
    private String message;

    public String getMessage() { return message; }
    public void setMessage(String value) { message = value; }
}
