package com.learnplatform.dto;

/**
 * 复习答题提交请求
 */
public class ReviewSubmitRequest {

    /** 题目ID */
    private Long questionId;

    /** 用户答案 */
    private String userAnswer;

    /** 答题用时（秒） */
    private Integer answerTime;

    /** 可选的 0–5 自评；答错时不能覆盖服务端 AGAIN 评分。 */
    private Integer selfAssessedQuality;

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public String getUserAnswer() { return userAnswer; }
    public void setUserAnswer(String userAnswer) { this.userAnswer = userAnswer; }
    public Integer getAnswerTime() { return answerTime; }
    public void setAnswerTime(Integer answerTime) { this.answerTime = answerTime; }
    public Integer getSelfAssessedQuality() { return selfAssessedQuality; }
    public void setSelfAssessedQuality(Integer selfAssessedQuality) { this.selfAssessedQuality = selfAssessedQuality; }
}
