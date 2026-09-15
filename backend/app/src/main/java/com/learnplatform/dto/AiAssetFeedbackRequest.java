package com.learnplatform.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** AI 题目学习资产反馈请求。 */
public class AiAssetFeedbackRequest {
    @NotNull(message = "题目ID不能为空")
    private Long questionId;
    @NotNull(message = "资产类型不能为空")
    private AiAssetType assetType;
    @NotNull(message = "是否有帮助不能为空")
    private Boolean helpful;
    @Size(max = 1000, message = "反馈内容不能超过1000个字符")
    private String comment;

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public AiAssetType getAssetType() { return assetType; }
    public void setAssetType(AiAssetType assetType) { this.assetType = assetType; }
    public Boolean getHelpful() { return helpful; }
    public void setHelpful(Boolean helpful) { this.helpful = helpful; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
