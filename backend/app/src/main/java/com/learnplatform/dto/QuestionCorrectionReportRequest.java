package com.learnplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 用户提交题目纠错反馈请求。 */
public class QuestionCorrectionReportRequest {

    @NotBlank(message = "纠错类型不能为空")
    @Size(max = 30, message = "纠错类型长度不能超过30个字符")
    private String reportType;

    @NotBlank(message = "问题描述不能为空")
    @Size(max = 1000, message = "问题描述不能超过1000个字符")
    private String description;

    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
