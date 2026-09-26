package com.learnplatform.dto;

import com.fasterxml.jackson.databind.JsonNode;

public class TutorSessionVO {
    private String sessionKey;
    private String title;
    private JsonNode lesson;
    private JsonNode check;
    private String checkAnswer;
    private TutorCheckResultVO checkResult;
    private TutorLearningContextVO learningContext;
    private boolean agentAvailable;

    public String getSessionKey() { return sessionKey; }
    public void setSessionKey(String value) { sessionKey = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public JsonNode getLesson() { return lesson; }
    public void setLesson(JsonNode value) { lesson = value; }
    public JsonNode getCheck() { return check; }
    public void setCheck(JsonNode value) { check = value; }
    public String getCheckAnswer() { return checkAnswer; }
    public void setCheckAnswer(String value) { checkAnswer = value; }
    public TutorCheckResultVO getCheckResult() { return checkResult; }
    public void setCheckResult(TutorCheckResultVO value) { checkResult = value; }
    public TutorLearningContextVO getLearningContext() { return learningContext; }
    public void setLearningContext(TutorLearningContextVO value) { learningContext = value; }
    public boolean isAgentAvailable() { return agentAvailable; }
    public void setAgentAvailable(boolean value) { agentAvailable = value; }
}
