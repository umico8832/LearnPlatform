package com.learnplatform.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 题目审核请求
 */
public class QuestionReviewRequest {

    @NotNull(message = "审核结果不能为空")
    /** 1-通过 2-拒绝 */
    private Integer status;

    @Size(max = 1000, message = "审核意见不能超过1000字")
    private String reviewComment;

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
}