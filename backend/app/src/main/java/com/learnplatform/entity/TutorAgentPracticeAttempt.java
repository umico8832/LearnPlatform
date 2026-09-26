package com.learnplatform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("tutor_agent_practice_attempt")
public class TutorAgentPracticeAttempt {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long messageId;
    private Long questionId;
    private Long practiceRecordId;
    private String questionJson;
    private String resultJson;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getMessageId() { return messageId; }
    public void setMessageId(Long value) { messageId = value; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long value) { questionId = value; }
    public Long getPracticeRecordId() { return practiceRecordId; }
    public void setPracticeRecordId(Long value) { practiceRecordId = value; }
    public String getQuestionJson() { return questionJson; }
    public void setQuestionJson(String value) { questionJson = value; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String value) { resultJson = value; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime value) { createTime = value; }
}
