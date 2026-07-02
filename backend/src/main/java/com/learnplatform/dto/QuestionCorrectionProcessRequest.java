package com.learnplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 管理员处理题目纠错反馈请求。 */
public class QuestionCorrectionProcessRequest {

    @NotBlank(message = "处理状态不能为空")
    @Size(max = 20, message = "处理状态长度不能超过20个字符")
    private String status;

    @NotBlank(message = "处理说明不能为空")
    @Size(max = 1000, message = "处理说明不能超过1000个字符")
    private String handlerComment;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getHandlerComment() { return handlerComment; }
    public void setHandlerComment(String handlerComment) { this.handlerComment = handlerComment; }
}
