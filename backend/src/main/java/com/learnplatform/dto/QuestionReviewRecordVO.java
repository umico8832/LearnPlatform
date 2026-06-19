package com.learnplatform.dto;

import java.time.LocalDateTime;

/**
 * 题目复审记录 VO
 */
public class QuestionReviewRecordVO {

    private Long id;
    private Long questionId;
    private Long reviewerId;
    private String reviewerName;
    private String reviewType;
    private String action;
    private String oldContent;
    private String newContent;
    private Integer oldDifficulty;
    private Integer newDifficulty;
    private String comment;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public String getReviewerName() { return reviewerName; }
    public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }
    public String getReviewType() { return reviewType; }
    public void setReviewType(String reviewType) { this.reviewType = reviewType; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getOldContent() { return oldContent; }
    public void setOldContent(String oldContent) { this.oldContent = oldContent; }
    public String getNewContent() { return newContent; }
    public void setNewContent(String newContent) { this.newContent = newContent; }
    public Integer getOldDifficulty() { return oldDifficulty; }
    public void setOldDifficulty(Integer oldDifficulty) { this.oldDifficulty = oldDifficulty; }
    public Integer getNewDifficulty() { return newDifficulty; }
    public void setNewDifficulty(Integer newDifficulty) { this.newDifficulty = newDifficulty; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}