package com.learnplatform.dto;

import java.time.LocalDateTime;
import java.util.List;

public class SubjectiveAnswerReviewVO {
    private Long answerId;
    private Long examRecordId;
    private Long userId;
    private String examTitle;
    private String displayNumber;
    private String content;
    private String userAnswer;
    private Integer fullScore;
    private String gradingStatus;
    private Integer score;
    private String reviewComment;
    private String reviewDetailJson;
    private LocalDateTime submittedAt;
    private List<GradingPointVO> gradingPoints;

    public Long getAnswerId() { return answerId; }
    public void setAnswerId(Long answerId) { this.answerId = answerId; }
    public Long getExamRecordId() { return examRecordId; }
    public void setExamRecordId(Long examRecordId) { this.examRecordId = examRecordId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getExamTitle() { return examTitle; }
    public void setExamTitle(String examTitle) { this.examTitle = examTitle; }
    public String getDisplayNumber() { return displayNumber; }
    public void setDisplayNumber(String displayNumber) { this.displayNumber = displayNumber; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getUserAnswer() { return userAnswer; }
    public void setUserAnswer(String userAnswer) { this.userAnswer = userAnswer; }
    public Integer getFullScore() { return fullScore; }
    public void setFullScore(Integer fullScore) { this.fullScore = fullScore; }
    public String getGradingStatus() { return gradingStatus; }
    public void setGradingStatus(String gradingStatus) { this.gradingStatus = gradingStatus; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public String getReviewDetailJson() { return reviewDetailJson; }
    public void setReviewDetailJson(String reviewDetailJson) { this.reviewDetailJson = reviewDetailJson; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public List<GradingPointVO> getGradingPoints() { return gradingPoints; }
    public void setGradingPoints(List<GradingPointVO> gradingPoints) { this.gradingPoints = gradingPoints; }

    public static class GradingPointVO {
        private String pointKey;
        private String title;
        private String description;
        private String referenceAnswer;
        private Integer maxScore;
        private Integer sortOrder;

        public String getPointKey() { return pointKey; }
        public void setPointKey(String pointKey) { this.pointKey = pointKey; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getReferenceAnswer() { return referenceAnswer; }
        public void setReferenceAnswer(String referenceAnswer) { this.referenceAnswer = referenceAnswer; }
        public Integer getMaxScore() { return maxScore; }
        public void setMaxScore(Integer maxScore) { this.maxScore = maxScore; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    }
}
