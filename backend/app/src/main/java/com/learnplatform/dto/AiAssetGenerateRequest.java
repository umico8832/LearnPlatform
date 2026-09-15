package com.learnplatform.dto;

import jakarta.validation.constraints.NotNull;

/** AI 题目学习资产生成请求。 */
public class AiAssetGenerateRequest {
    @NotNull(message = "题目ID不能为空")
    private Long questionId;
    @NotNull(message = "资产类型不能为空")
    private AiAssetType assetType;

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public AiAssetType getAssetType() { return assetType; }
    public void setAssetType(AiAssetType assetType) { this.assetType = assetType; }
}
