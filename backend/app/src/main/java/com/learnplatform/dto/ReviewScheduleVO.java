package com.learnplatform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 复习计划 VO
 */
public class ReviewScheduleVO {

    private Long id;
    private Long questionId;

    @Schema(description = "当前用户能否进入正式题目理解页", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean hasUnderstanding;
    private String questionContent;
    private String questionType;
    private Integer difficulty;
    private Long courseId;
    private String courseName;

    /** 存量 SM-2 简易因子，FSRS 卡片为空。 */
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

    /** FSRS 排期状态的学习者称谓。 */
    private String statusLabel;

    @Schema(allowableValues = {"UNRATED", "LEARNING", "REVIEW", "RELEARNING"})
    private String schedulingState;

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(nullable = true, allowableValues = {"AGAIN", "HARD", "GOOD", "EASY"})
    private String lastRating;

    /** 是否仍具备自动判分所需的题型和答案依据。 */
    private boolean availableForReview;

    private GamificationRewardFeedback reward;
    /** Present only on a submit response; schedule queries do not infer a grading result. */
    private Boolean correct;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public boolean getHasUnderstanding() { return hasUnderstanding; }
    public void setHasUnderstanding(boolean hasUnderstanding) { this.hasUnderstanding = hasUnderstanding; }
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
    public String getSchedulingState() { return schedulingState; }
    public void setSchedulingState(String value) { schedulingState = value; }
    public String getLastRating() { return lastRating; }
    public void setLastRating(String value) { lastRating = value; }
    public String getStatusLabel() { return statusLabel; }
    public void setStatusLabel(String statusLabel) { this.statusLabel = statusLabel; }
    public boolean isAvailableForReview() { return availableForReview; }
    public void setAvailableForReview(boolean availableForReview) {
        this.availableForReview = availableForReview;
    }
    public GamificationRewardFeedback getReward() { return reward; }
    public void setReward(GamificationRewardFeedback reward) { this.reward = reward; }
    public Boolean getCorrect() { return correct; }
    public void setCorrect(Boolean correct) { this.correct = correct; }
}
