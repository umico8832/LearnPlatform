package com.learnplatform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("course_stage_assessment_question")
public class CourseStageAssessmentQuestion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long assessmentId;
    private Long questionId;
    private Integer sortOrder;
    private String questionType;
    private String sourceTypeSnapshot;
    private String sourceCategorySnapshot;
    private Long originQuestionIdSnapshot;
    private String knowledgePointsJson;
    private String contentSnapshot;
    private String optionsSnapshot;
    private String correctAnswerSnapshot;
    private String analysisSnapshot;
    private Integer score;
    private String userAnswer;
    private Integer isCorrect;
    private LocalDateTime answeredTime;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAssessmentId() { return assessmentId; }
    public void setAssessmentId(Long assessmentId) { this.assessmentId = assessmentId; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }
    public String getSourceTypeSnapshot() { return sourceTypeSnapshot; }
    public void setSourceTypeSnapshot(String value) { this.sourceTypeSnapshot = value; }
    public String getSourceCategorySnapshot() { return sourceCategorySnapshot; }
    public void setSourceCategorySnapshot(String value) { this.sourceCategorySnapshot = value; }
    public Long getOriginQuestionIdSnapshot() { return originQuestionIdSnapshot; }
    public void setOriginQuestionIdSnapshot(Long value) { this.originQuestionIdSnapshot = value; }
    public String getKnowledgePointsJson() { return knowledgePointsJson; }
    public void setKnowledgePointsJson(String value) { this.knowledgePointsJson = value; }
    public String getContentSnapshot() { return contentSnapshot; }
    public void setContentSnapshot(String contentSnapshot) { this.contentSnapshot = contentSnapshot; }
    public String getOptionsSnapshot() { return optionsSnapshot; }
    public void setOptionsSnapshot(String optionsSnapshot) { this.optionsSnapshot = optionsSnapshot; }
    public String getCorrectAnswerSnapshot() { return correctAnswerSnapshot; }
    public void setCorrectAnswerSnapshot(String correctAnswerSnapshot) { this.correctAnswerSnapshot = correctAnswerSnapshot; }
    public String getAnalysisSnapshot() { return analysisSnapshot; }
    public void setAnalysisSnapshot(String analysisSnapshot) { this.analysisSnapshot = analysisSnapshot; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public String getUserAnswer() { return userAnswer; }
    public void setUserAnswer(String userAnswer) { this.userAnswer = userAnswer; }
    public Integer getIsCorrect() { return isCorrect; }
    public void setIsCorrect(Integer isCorrect) { this.isCorrect = isCorrect; }
    public LocalDateTime getAnsweredTime() { return answeredTime; }
    public void setAnsweredTime(LocalDateTime answeredTime) { this.answeredTime = answeredTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
