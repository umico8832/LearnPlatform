package com.learnplatform.dto;

/**
 * 练习答题结果 VO（提交答案后返回）
 */
public class PracticeResultVO {

    /** 记录ID */
    private Long recordId;

    /** 题目ID */
    private Long questionId;

    /** 用户答案 */
    private String userAnswer;

    /** 是否正确 */
    private Boolean correct;

    /** 正确答案 */
    private String correctAnswer;

    /** 题目解析 */
    private String analysis;

    /** 题目分值 */
    private Integer score;

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public String getUserAnswer() { return userAnswer; }
    public void setUserAnswer(String userAnswer) { this.userAnswer = userAnswer; }
    public Boolean getCorrect() { return correct; }
    public void setCorrect(Boolean correct) { this.correct = correct; }
    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }
    public String getAnalysis() { return analysis; }
    public void setAnalysis(String analysis) { this.analysis = analysis; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
}