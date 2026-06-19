package com.learnplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 题目复审请求 DTO
 */
public class QuestionReReviewRequest {

    /** 复审动作：APPROVE-通过 REVISE-修订 REJECT-标记废弃 */
    @NotBlank(message = "复审动作不能为空")
    private String action;

    /** 修订后的题干（action=REVISE 时必填） */
    private String newContent;

    /** 修订后的难度（action=REVISE 时可选） */
    private Integer newDifficulty;

    /** 复审意见 */
    @NotBlank(message = "复审意见不能为空")
    private String comment;

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getNewContent() { return newContent; }
    public void setNewContent(String newContent) { this.newContent = newContent; }
    public Integer getNewDifficulty() { return newDifficulty; }
    public void setNewDifficulty(Integer newDifficulty) { this.newDifficulty = newDifficulty; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}