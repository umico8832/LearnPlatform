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

    /** 用户自评难度：0-5（可选，默认由系统根据对错自动映射）
     * 5 - 完美记住
     * 4 - 稍有犹豫但答对
     * 3 - 困难但答对
     * 2 - 答错但看到答案后觉得简单
     * 1 - 答错但看到答案后还记得
     * 0 - 完全不记得
     */
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