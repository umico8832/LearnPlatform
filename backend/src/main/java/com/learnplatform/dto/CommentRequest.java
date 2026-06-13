package com.learnplatform.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 评论创建/回复请求
 */
public class CommentRequest {

    @NotNull(message = "题目ID不能为空")
    private Long questionId;

    @NotBlank(message = "评论内容不能为空")
    @Max(value = 2000, message = "评论内容不能超过2000字")
    private String content;

    /** 父评论ID，0表示顶级评论 */
    @Min(value = 0, message = "父评论ID不能为负数")
    private Long parentId = 0L;

    /** 回复目标用户ID */
    private Long replyToUserId;

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Long getReplyToUserId() { return replyToUserId; }
    public void setReplyToUserId(Long replyToUserId) { this.replyToUserId = replyToUserId; }
}