package com.learnplatform.dto;

import java.time.LocalDateTime;
import java.util.List;

public class PrivateExamDraftVO {
    private Long id;
    private String title;
    private Long courseId;
    private Integer duration;
    private String status;
    private Long confirmedPaperId;
    private String sourceName;
    private String sourceFormat;
    private Boolean originalFileAvailable;
    private Integer reviewedQuestionCount;
    private Integer questionCount;
    private LocalDateTime createTime;
    private List<DraftQuestion> questions;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getConfirmedPaperId() { return confirmedPaperId; }
    public void setConfirmedPaperId(Long confirmedPaperId) { this.confirmedPaperId = confirmedPaperId; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getSourceFormat() { return sourceFormat; }
    public void setSourceFormat(String sourceFormat) { this.sourceFormat = sourceFormat; }
    public Boolean getOriginalFileAvailable() { return originalFileAvailable; }
    public void setOriginalFileAvailable(Boolean originalFileAvailable) {
        this.originalFileAvailable = originalFileAvailable;
    }
    public Integer getReviewedQuestionCount() { return reviewedQuestionCount; }
    public void setReviewedQuestionCount(Integer reviewedQuestionCount) {
        this.reviewedQuestionCount = reviewedQuestionCount;
    }
    public Integer getQuestionCount() { return questionCount; }
    public void setQuestionCount(Integer questionCount) { this.questionCount = questionCount; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public List<DraftQuestion> getQuestions() { return questions; }
    public void setQuestions(List<DraftQuestion> questions) { this.questions = questions; }

    public static class DraftQuestion {
        private Long id;
        private Integer sortOrder;
        private String content;
        private String questionType;
        private Integer score;
        private List<OptionItem> options;
        private List<String> originalAnswerLabels;
        private String originalAnalysis;
        private List<String> aiAnswerLabels;
        private String aiAnalysis;
        private String generationStatus;
        private List<String> finalAnswerLabels;
        private String finalAnalysis;
        private String reviewStatus;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getQuestionType() { return questionType; }
        public void setQuestionType(String questionType) { this.questionType = questionType; }
        public Integer getScore() { return score; }
        public void setScore(Integer score) { this.score = score; }
        public List<OptionItem> getOptions() { return options; }
        public void setOptions(List<OptionItem> options) { this.options = options; }
        public List<String> getOriginalAnswerLabels() { return originalAnswerLabels; }
        public void setOriginalAnswerLabels(List<String> originalAnswerLabels) {
            this.originalAnswerLabels = originalAnswerLabels;
        }
        public String getOriginalAnalysis() { return originalAnalysis; }
        public void setOriginalAnalysis(String originalAnalysis) { this.originalAnalysis = originalAnalysis; }
        public List<String> getAiAnswerLabels() { return aiAnswerLabels; }
        public void setAiAnswerLabels(List<String> aiAnswerLabels) { this.aiAnswerLabels = aiAnswerLabels; }
        public String getAiAnalysis() { return aiAnalysis; }
        public void setAiAnalysis(String aiAnalysis) { this.aiAnalysis = aiAnalysis; }
        public String getGenerationStatus() { return generationStatus; }
        public void setGenerationStatus(String generationStatus) { this.generationStatus = generationStatus; }
        public List<String> getFinalAnswerLabels() { return finalAnswerLabels; }
        public void setFinalAnswerLabels(List<String> finalAnswerLabels) { this.finalAnswerLabels = finalAnswerLabels; }
        public String getFinalAnalysis() { return finalAnalysis; }
        public void setFinalAnalysis(String finalAnalysis) { this.finalAnalysis = finalAnalysis; }
        public String getReviewStatus() { return reviewStatus; }
        public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
    }

    public static class OptionItem {
        private String label;
        private String content;

        public OptionItem() { }
        public OptionItem(String label, String content) {
            this.label = label;
            this.content = content;
        }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}
