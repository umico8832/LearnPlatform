package com.learnplatform.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 复习计划 VO
 */
public class ReviewScheduleVO {

    private Long id;
    private Long questionId;
    private String questionContent;
    private String questionType;
    private Integer difficulty;
    private Long courseId;
    private String courseName;

    /** SM-2 简易因子 */
    private BigDecimal easeFactor;

    /** 当前间隔天数 */
    private Integer intervalDays;

    /** 连续正确次数 */
    private Integer repetitions;

    /** 下次复习日期 */
    private LocalDate nextReviewDate;

    /** 上次复习日期 */
    private LocalDate lastReviewDate;

    /** 上次答题质量(0-5) */
    private Integer lastQuality;

    /** 总复习次数 */
    private Integer totalReviews;

    /** 是否逾期（到期未复习） */
    private boolean overdue;

    /** 逾期天数（未逾期为 0） */
    private int overdueDays;

    /** 掌握等级文字（新卡片/学习中/已掌握/困难） */
    private String statusLabel;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public String getQuestionContent() { return questionContent; }
    public void setQuestionContent(String questionContent) { this.questionContent = questionContent; }
    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }
    public Integer getDifficulty() { return difficulty; }
    public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public BigDecimal getEaseFactor() { return easeFactor; }
    public void setEaseFactor(BigDecimal easeFactor) { this.easeFactor = easeFactor; }
    public Integer getIntervalDays() { return intervalDays; }
    public void setIntervalDays(Integer intervalDays) { this.intervalDays = intervalDays; }
    public Integer getRepetitions() { return repetitions; }
    public void setRepetitions(Integer repetitions) { this.repetitions = repetitions; }
    public LocalDate getNextReviewDate() { return nextReviewDate; }
    public void setNextReviewDate(LocalDate nextReviewDate) { this.nextReviewDate = nextReviewDate; }
    public LocalDate getLastReviewDate() { return lastReviewDate; }
    public void setLastReviewDate(LocalDate lastReviewDate) { this.lastReviewDate = lastReviewDate; }
    public Integer getLastQuality() { return lastQuality; }
    public void setLastQuality(Integer lastQuality) { this.lastQuality = lastQuality; }
    public Integer getTotalReviews() { return totalReviews; }
    public void setTotalReviews(Integer totalReviews) { this.totalReviews = totalReviews; }
    public boolean isOverdue() { return overdue; }
    public void setOverdue(boolean overdue) { this.overdue = overdue; }
    public int getOverdueDays() { return overdueDays; }
    public void setOverdueDays(int overdueDays) { this.overdueDays = overdueDays; }
    public String getStatusLabel() { return statusLabel; }
    public void setStatusLabel(String statusLabel) { this.statusLabel = statusLabel; }
}