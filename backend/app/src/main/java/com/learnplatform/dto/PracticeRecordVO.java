package com.learnplatform.dto;

import java.time.LocalDateTime;

/**
 * 练习记录 VO（列表展示用）
 */
public class PracticeRecordVO {

    /** 记录ID */
    private Long id;

    /** 题目ID */
    private Long questionId;

    /** 题干内容 */
    private String questionContent;

    /** 题型 */
    private String questionType;

    /** 课程名称 */
    private String courseName;

    /** 难度 */
    private Integer difficulty;

    /** 用户答案 */
    private String userAnswer;

    /** 是否正确 */
    private Integer isCorrect;

    /** 答题耗时（秒） */
    private Integer answerTime;

    /** 答题时间 */
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public String getQuestionContent() { return questionContent; }
    public void setQuestionContent(String questionContent) { this.questionContent = questionContent; }
    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public Integer getDifficulty() { return difficulty; }
    public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }
    public String getUserAnswer() { return userAnswer; }
    public void setUserAnswer(String userAnswer) { this.userAnswer = userAnswer; }
    public Integer getIsCorrect() { return isCorrect; }
    public void setIsCorrect(Integer isCorrect) { this.isCorrect = isCorrect; }
    public Integer getAnswerTime() { return answerTime; }
    public void setAnswerTime(Integer answerTime) { this.answerTime = answerTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}