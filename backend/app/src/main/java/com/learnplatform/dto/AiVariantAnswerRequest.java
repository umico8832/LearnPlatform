package com.learnplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 结构化变式题首次作答请求。 */
public class AiVariantAnswerRequest {

    @NotBlank(message = "请选择答案")
    @Size(max = 500, message = "答案长度不能超过500个字符")
    private String userAnswer;

    public String getUserAnswer() { return userAnswer; }
    public void setUserAnswer(String userAnswer) { this.userAnswer = userAnswer; }
}
