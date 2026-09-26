package com.learnplatform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("tutor_agent_plan_confirmation")
public class TutorAgentPlanConfirmation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long messageId;
    private LocalDateTime confirmedTime;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getMessageId() { return messageId; }
    public void setMessageId(Long value) { messageId = value; }
    public LocalDateTime getConfirmedTime() { return confirmedTime; }
    public void setConfirmedTime(LocalDateTime value) { confirmedTime = value; }
}
