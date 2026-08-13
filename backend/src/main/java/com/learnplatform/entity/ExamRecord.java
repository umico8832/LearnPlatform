package com.learnplatform.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

/**
 * 考试记录实体
 */
@TableName("exam_record")
public class ExamRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long examPaperId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer score;

    private Integer totalScore;

    /** 状态：0-进行中 1-已完成 2-已超时 */
    private Integer status;

    /** 进行中考试幂等键，完成或超时后释放 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String activeExamKey;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getExamPaperId() { return examPaperId; }
    public void setExamPaperId(Long examPaperId) { this.examPaperId = examPaperId; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public Integer getTotalScore() { return totalScore; }
    public void setTotalScore(Integer totalScore) { this.totalScore = totalScore; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getActiveExamKey() { return activeExamKey; }
    public void setActiveExamKey(String activeExamKey) { this.activeExamKey = activeExamKey; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
