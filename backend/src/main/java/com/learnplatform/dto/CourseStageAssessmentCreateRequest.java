package com.learnplatform.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class CourseStageAssessmentCreateRequest {
    @Min(value = 1, message = "测评题数不能少于1")
    @Max(value = 20, message = "测评题数不能超过20")
    private Integer questionCount = 5;
    private Long knowledgePointId;

    public Integer getQuestionCount() { return questionCount; }
    public void setQuestionCount(Integer questionCount) { this.questionCount = questionCount; }
    public Long getKnowledgePointId() { return knowledgePointId; }
    public void setKnowledgePointId(Long knowledgePointId) { this.knowledgePointId = knowledgePointId; }
}
