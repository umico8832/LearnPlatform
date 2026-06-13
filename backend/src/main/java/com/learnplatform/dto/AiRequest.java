package com.learnplatform.dto;

/**
 * AI 请求 DTO
 */
public class AiRequest {

    /** 题目ID（用于解析和变式题） */
    private Long questionId;

    /** 课程ID（用于复习建议） */
    private Long courseId;

    /** 知识点ID（用于知识点总结） */
    private Long knowledgePointId;

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }

    public Long getKnowledgePointId() { return knowledgePointId; }
    public void setKnowledgePointId(Long knowledgePointId) { this.knowledgePointId = knowledgePointId; }
}