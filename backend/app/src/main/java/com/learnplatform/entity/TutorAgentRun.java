package com.learnplatform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("tutor_agent_run")
public class TutorAgentRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String runKey;
    private Long tutorSessionId;
    private Long userId;
    private String status;
    private Integer nextSequence;
    private String executionKey;
    private LocalDateTime leaseUntil;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public String getRunKey() { return runKey; }
    public void setRunKey(String value) { runKey = value; }
    public Long getTutorSessionId() { return tutorSessionId; }
    public void setTutorSessionId(Long value) { tutorSessionId = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { userId = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Integer getNextSequence() { return nextSequence; }
    public void setNextSequence(Integer value) { nextSequence = value; }
    public String getExecutionKey() { return executionKey; }
    public void setExecutionKey(String value) { executionKey = value; }
    public LocalDateTime getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(LocalDateTime value) { leaseUntil = value; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime value) { createTime = value; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime value) { updateTime = value; }
}
