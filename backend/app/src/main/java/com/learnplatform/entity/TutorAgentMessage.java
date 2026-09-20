package com.learnplatform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("tutor_agent_message")
public class TutorAgentMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long runId;
    private Integer sequenceNo;
    private String role;
    private String content;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getRunId() { return runId; }
    public void setRunId(Long value) { runId = value; }
    public Integer getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(Integer value) { sequenceNo = value; }
    public String getRole() { return role; }
    public void setRole(String value) { role = value; }
    public String getContent() { return content; }
    public void setContent(String value) { content = value; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime value) { createTime = value; }
}
