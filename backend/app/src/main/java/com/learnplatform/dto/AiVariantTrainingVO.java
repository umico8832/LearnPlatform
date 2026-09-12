package com.learnplatform.dto;

import java.time.LocalDateTime;

/** 用户当前缓存变式题的训练状态，以及首次判分后才可见的答案结果。 */
public class AiVariantTrainingVO {

    private Long questionId;
    private Long assetId;
    private String status;
    private Boolean completed;
    private Boolean answered;
    private Boolean correct;
    private String userAnswer;
    private String correctAnswer;
    private String analysis;
    private LocalDateTime startedTime;
    private LocalDateTime answeredTime;
    private LocalDateTime completedTime;

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getCompleted() { return completed; }
    public void setCompleted(Boolean completed) { this.completed = completed; }
    public Boolean getAnswered() { return answered; }
    public void setAnswered(Boolean answered) { this.answered = answered; }
    public Boolean getCorrect() { return correct; }
    public void setCorrect(Boolean correct) { this.correct = correct; }
    public String getUserAnswer() { return userAnswer; }
    public void setUserAnswer(String userAnswer) { this.userAnswer = userAnswer; }
    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }
    public String getAnalysis() { return analysis; }
    public void setAnalysis(String analysis) { this.analysis = analysis; }
    public LocalDateTime getStartedTime() { return startedTime; }
    public void setStartedTime(LocalDateTime startedTime) { this.startedTime = startedTime; }
    public LocalDateTime getAnsweredTime() { return answeredTime; }
    public void setAnsweredTime(LocalDateTime answeredTime) { this.answeredTime = answeredTime; }
    public LocalDateTime getCompletedTime() { return completedTime; }
    public void setCompletedTime(LocalDateTime completedTime) { this.completedTime = completedTime; }
}
