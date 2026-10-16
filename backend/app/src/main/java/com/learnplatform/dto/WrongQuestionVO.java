package com.learnplatform.dto;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.Map;

/**
 * 错题本 VO
 */
public class WrongQuestionVO {

    private Long id;
    private boolean hasUnderstanding;
    private Long questionId;
    private String questionContent;
    private String questionType;
    private Long courseId;
    private String courseName;
    private Integer difficulty;
    private Integer wrongCount;
    private Integer masteryLevel;
    private String lastWrongAnswer;
    private Map<String, Object> misconception;
    private LocalDate nextReviewDate;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public boolean getHasUnderstanding() { return hasUnderstanding; }
    public void setHasUnderstanding(boolean hasUnderstanding) { this.hasUnderstanding = hasUnderstanding; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public String getQuestionContent() { return questionContent; }
    public void setQuestionContent(String questionContent) { this.questionContent = questionContent; }
    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public Integer getDifficulty() { return difficulty; }
    public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }
    public Integer getWrongCount() { return wrongCount; }
    public void setWrongCount(Integer wrongCount) { this.wrongCount = wrongCount; }
    public Integer getMasteryLevel() { return masteryLevel; }
    public void setMasteryLevel(Integer masteryLevel) { this.masteryLevel = masteryLevel; }
    public String getLastWrongAnswer() { return lastWrongAnswer; }
    public void setLastWrongAnswer(String lastWrongAnswer) { this.lastWrongAnswer = lastWrongAnswer; }
    public Map<String, Object> getMisconception() { return misconception; }
    public void setMisconception(Map<String, Object> misconception) { this.misconception = misconception; }
    public LocalDate getNextReviewDate() { return nextReviewDate; }
    public void setNextReviewDate(LocalDate nextReviewDate) { this.nextReviewDate = nextReviewDate; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
