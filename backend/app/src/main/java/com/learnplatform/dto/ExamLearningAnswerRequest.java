package com.learnplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public class ExamLearningAnswerRequest {
    @NotNull(message = "题目ID不能为空")
    private Long questionId;
    @NotBlank(message = "答案不能为空")
    private String userAnswer;
    @PositiveOrZero(message = "答题耗时不能为负数")
    private Integer answerTime;

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public String getUserAnswer() { return userAnswer; }
    public void setUserAnswer(String userAnswer) { this.userAnswer = userAnswer; }
    public Integer getAnswerTime() { return answerTime; }
    public void setAnswerTime(Integer answerTime) { this.answerTime = answerTime; }
}
