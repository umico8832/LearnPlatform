package com.learnplatform.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateAiDailyQuotaRequest {

    @Min(value = 0, message = "AI 日配额不能小于 0")
    @Max(value = 10000, message = "AI 日配额不能超过 10000")
    private Integer dailyQuota;

    @NotBlank(message = "调整原因不能为空")
    @Size(max = 500, message = "调整原因不能超过 500 个字符")
    private String reason;

    public Integer getDailyQuota() { return dailyQuota; }
    public void setDailyQuota(Integer dailyQuota) { this.dailyQuota = dailyQuota; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
