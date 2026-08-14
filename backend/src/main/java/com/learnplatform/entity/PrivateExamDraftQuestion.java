package com.learnplatform.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("private_exam_draft_question")
public class PrivateExamDraftQuestion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long draftId;
    private Integer sortOrder;
    private String content;
    private String questionType;
    private Integer score;
    private String optionsJson;
    private String originalAnswerJson;
    private String originalAnalysis;
    private String aiAnswerJson;
    private String aiAnalysis;
    private String generationStatus;
    private String finalAnswerJson;
    private String finalAnalysis;
    private String reviewStatus;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDraftId() { return draftId; }
    public void setDraftId(Long draftId) { this.draftId = draftId; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public String getOptionsJson() { return optionsJson; }
    public void setOptionsJson(String optionsJson) { this.optionsJson = optionsJson; }
    public String getOriginalAnswerJson() { return originalAnswerJson; }
    public void setOriginalAnswerJson(String originalAnswerJson) { this.originalAnswerJson = originalAnswerJson; }
    public String getOriginalAnalysis() { return originalAnalysis; }
    public void setOriginalAnalysis(String originalAnalysis) { this.originalAnalysis = originalAnalysis; }
    public String getAiAnswerJson() { return aiAnswerJson; }
    public void setAiAnswerJson(String aiAnswerJson) { this.aiAnswerJson = aiAnswerJson; }
    public String getAiAnalysis() { return aiAnalysis; }
    public void setAiAnalysis(String aiAnalysis) { this.aiAnalysis = aiAnalysis; }
    public String getGenerationStatus() { return generationStatus; }
    public void setGenerationStatus(String generationStatus) { this.generationStatus = generationStatus; }
    public String getFinalAnswerJson() { return finalAnswerJson; }
    public void setFinalAnswerJson(String finalAnswerJson) { this.finalAnswerJson = finalAnswerJson; }
    public String getFinalAnalysis() { return finalAnalysis; }
    public void setFinalAnalysis(String finalAnalysis) { this.finalAnalysis = finalAnalysis; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
