package com.learnplatform.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class PrivateExamDocxConfirmRequest extends PrivateExamDocxRequest {
    @NotBlank(message = "预览文件哈希不能为空")
    @Pattern(regexp = "[0-9a-fA-F]{64}", message = "预览文件哈希格式不正确")
    private String expectedContentHash;
    @AssertTrue(message = "必须确认解析结果后才能导入")
    private boolean confirmed;

    public String getExpectedContentHash() { return expectedContentHash; }
    public void setExpectedContentHash(String expectedContentHash) { this.expectedContentHash = expectedContentHash; }
    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }
}
