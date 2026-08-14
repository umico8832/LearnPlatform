package com.learnplatform.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public class PrivateExamImportConfirmRequest extends PrivateExamImportRequest {
    @NotBlank(message = "预览内容哈希不能为空")
    private String expectedContentHash;
    @AssertTrue(message = "必须确认解析结果后才能导入")
    private boolean confirmed;

    public String getExpectedContentHash() { return expectedContentHash; }
    public void setExpectedContentHash(String expectedContentHash) { this.expectedContentHash = expectedContentHash; }
    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }
}
