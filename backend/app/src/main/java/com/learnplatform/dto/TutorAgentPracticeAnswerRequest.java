package com.learnplatform.dto;

import jakarta.validation.constraints.NotBlank;

public class TutorAgentPracticeAnswerRequest {
    @NotBlank
    private String userAnswer;
    private Integer answerTime;

    public String getUserAnswer() {
        return userAnswer;
    }

    public void setUserAnswer(String value) {
        userAnswer = value;
    }

    public Integer getAnswerTime() {
        return answerTime;
    }

    public void setAnswerTime(Integer value) {
        answerTime = value;
    }
}
