package com.learnplatform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/** 同一作答可归属多个知识点，各行不能相加作为课程总数。 */
public class CourseKnowledgePointFactVO {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(description = "知识点 ID；null 表示未关联知识点")
    private Long knowledgePointId;
    private String knowledgePointName;
    @Schema(description = "知识点是否仍在当前课程目录中")
    private boolean available;
    private boolean tutorAvailable;
    private int answeredCount;
    private int correctCount;
    private int unresolvedWrongCount;
    private int dueReviewCount;

    public Long getKnowledgePointId() { return knowledgePointId; }
    public void setKnowledgePointId(Long value) { knowledgePointId = value; }
    public String getKnowledgePointName() { return knowledgePointName; }
    public void setKnowledgePointName(String value) { knowledgePointName = value; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean value) { available = value; }
    public boolean isTutorAvailable() { return tutorAvailable; }
    public void setTutorAvailable(boolean value) { tutorAvailable = value; }
    public int getAnsweredCount() { return answeredCount; }
    public void setAnsweredCount(int value) { answeredCount = value; }
    public int getCorrectCount() { return correctCount; }
    public void setCorrectCount(int value) { correctCount = value; }
    public int getUnresolvedWrongCount() { return unresolvedWrongCount; }
    public void setUnresolvedWrongCount(int value) { unresolvedWrongCount = value; }
    public int getDueReviewCount() { return dueReviewCount; }
    public void setDueReviewCount(int value) { dueReviewCount = value; }
}
