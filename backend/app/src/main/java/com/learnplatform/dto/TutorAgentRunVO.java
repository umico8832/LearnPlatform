package com.learnplatform.dto;

import java.util.ArrayList;
import java.util.List;

public class TutorAgentRunVO {
    private String runKey;
    private String status;
    private List<TutorAgentMessageVO> messages = new ArrayList<>();

    public String getRunKey() { return runKey; }
    public void setRunKey(String value) { runKey = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public List<TutorAgentMessageVO> getMessages() { return messages; }
    public void setMessages(List<TutorAgentMessageVO> value) { messages = value; }
}
