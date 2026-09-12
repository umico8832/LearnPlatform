package com.learnplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 提交练习答案请求
 */
public class PracticeSubmitRequest {

    /** 题目ID */
    @NotNull(message = "题目ID不能为空")
    private Long questionId;

    /** 用户答案（单选/多选用选项标签如 "A" 或 "A,B,C"，判断用 "TRUE"/"FALSE"，填空/简答直接写文本） */
    @NotBlank(message = "答案不能为空")
    private String userAnswer;

    /** 答题耗时（秒） */
    private Integer answerTime;

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public String getUserAnswer() { return userAnswer; }
    public void setUserAnswer(String userAnswer) { this.userAnswer = userAnswer; }
    public Integer getAnswerTime() { return answerTime; }
    public void setAnswerTime(Integer answerTime) { this.answerTime = answerTime; }
}