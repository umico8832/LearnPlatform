package com.learnplatform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("exam_learning_session")
public class ExamLearningSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long examPaperId;
    private Integer status;
    private Long currentQuestionId;
    private String activeSessionKey;
    private LocalDateTime startTime;
    private LocalDateTime completeTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getExamPaperId() { return examPaperId; }
    public void setExamPaperId(Long examPaperId) { this.examPaperId = examPaperId; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Long getCurrentQuestionId() { return currentQuestionId; }
    public void setCurrentQuestionId(Long currentQuestionId) { this.currentQuestionId = currentQuestionId; }
    public String getActiveSessionKey() { return activeSessionKey; }
    public void setActiveSessionKey(String activeSessionKey) { this.activeSessionKey = activeSessionKey; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getCompleteTime() { return completeTime; }
    public void setCompleteTime(LocalDateTime completeTime) { this.completeTime = completeTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
