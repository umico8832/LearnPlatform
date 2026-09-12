package com.learnplatform.dto;

import java.time.LocalDateTime;
import java.util.List;

public class AiVariantReviewVO {
    private Long id;
    private Long motherQuestionId;
    private String motherQuestionContent;
    private Long courseId;
    private String courseName;
    private String questionContent;
    private String questionType;
    private List<AiVariantQuestionVO.Option> options;
    private String correctAnswer;
    private String analysis;
    private Integer difficulty;
    private String reviewStatus;
    private String reviewNote;
    private Long reviewedBy;
    private LocalDateTime reviewedTime;
    private Long publishedQuestionId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMotherQuestionId() { return motherQuestionId; }
    public void setMotherQuestionId(Long value) { this.motherQuestionId = value; }
    public String getMotherQuestionContent() { return motherQuestionContent; }
    public void setMotherQuestionContent(String value) { this.motherQuestionContent = value; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public String getQuestionContent() { return questionContent; }
    public void setQuestionContent(String value) { this.questionContent = value; }
    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }
    public List<AiVariantQuestionVO.Option> getOptions() { return options; }
    public void setOptions(List<AiVariantQuestionVO.Option> options) { this.options = options; }
    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }
    public String getAnalysis() { return analysis; }
    public void setAnalysis(String analysis) { this.analysis = analysis; }
    public Integer getDifficulty() { return difficulty; }
    public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewedTime() { return reviewedTime; }
    public void setReviewedTime(LocalDateTime reviewedTime) { this.reviewedTime = reviewedTime; }
    public Long getPublishedQuestionId() { return publishedQuestionId; }
    public void setPublishedQuestionId(Long value) { this.publishedQuestionId = value; }
}
