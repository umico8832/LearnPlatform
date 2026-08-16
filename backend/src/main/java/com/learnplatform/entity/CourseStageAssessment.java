package com.learnplatform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("course_stage_assessment")
public class CourseStageAssessment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long courseId;
    private String status;
    private String selectionStrategy;
    private Long targetKnowledgePointId;
    private String targetKnowledgePointNameSnapshot;
    private Integer questionCount;
    private Integer correctCount;
    private String activeSessionKey;
    private LocalDateTime startTime;
    private LocalDateTime completeTime;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSelectionStrategy() { return selectionStrategy; }
    public void setSelectionStrategy(String selectionStrategy) { this.selectionStrategy = selectionStrategy; }
    public Long getTargetKnowledgePointId() { return targetKnowledgePointId; }
    public void setTargetKnowledgePointId(Long targetKnowledgePointId) {
        this.targetKnowledgePointId = targetKnowledgePointId;
    }
    public String getTargetKnowledgePointNameSnapshot() { return targetKnowledgePointNameSnapshot; }
    public void setTargetKnowledgePointNameSnapshot(String value) { this.targetKnowledgePointNameSnapshot = value; }
    public Integer getQuestionCount() { return questionCount; }
    public void setQuestionCount(Integer questionCount) { this.questionCount = questionCount; }
    public Integer getCorrectCount() { return correctCount; }
    public void setCorrectCount(Integer correctCount) { this.correctCount = correctCount; }
    public String getActiveSessionKey() { return activeSessionKey; }
    public void setActiveSessionKey(String activeSessionKey) { this.activeSessionKey = activeSessionKey; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getCompleteTime() { return completeTime; }
    public void setCompleteTime(LocalDateTime completeTime) { this.completeTime = completeTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
