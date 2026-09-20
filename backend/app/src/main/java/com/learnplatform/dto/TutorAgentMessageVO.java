package com.learnplatform.dto;

import java.time.LocalDateTime;

public class TutorAgentMessageVO {
    private Integer sequence;
    private String role;
    private String content;
    private LocalDateTime createTime;

    public Integer getSequence() { return sequence; }
    public void setSequence(Integer value) { sequence = value; }
    public String getRole() { return role; }
    public void setRole(String value) { role = value; }
    public String getContent() { return content; }
    public void setContent(String value) { content = value; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime value) { createTime = value; }
}
