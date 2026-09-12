package com.learnplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class PrivateExamDocxDraftCreateRequest extends PrivateExamDocxRequest {
    @NotBlank(message = "预览文件哈希不能为空")
    @Pattern(regexp = "[0-9a-fA-F]{64}", message = "预览文件哈希格式不正确")
    private String expectedContentHash;

    public String getExpectedContentHash() { return expectedContentHash; }
    public void setExpectedContentHash(String expectedContentHash) { this.expectedContentHash = expectedContentHash; }
}
