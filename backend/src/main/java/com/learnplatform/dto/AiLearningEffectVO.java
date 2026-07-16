package com.learnplatform.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 学习资产使用与后续答题表现的观察性统计。
 */
public class AiLearningEffectVO {

    private Integer days;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Long assetViewCount;
    private Long engagedUserCount;
    private Long viewedQuestionCount;
    private Long feedbackCount;
    private Double helpfulRate;
    private Long variantTrainingStartedCount;
    private Long variantTrainingCompletedCount;
    private Double variantTrainingCompletionRate;
    private Long variantTrainingAnsweredCount;
    private Long variantTrainingCorrectCount;
    private Double variantTrainingCorrectRate;
    private Long afterViewPracticeCount;
    private Double afterViewCorrectRate;
    private Long baselinePracticeCount;
    private Double baselineCorrectRate;
    private Double correctRateLift;
    private String conclusionLevel;
    private String conclusion;
    private Integer crossQuestionWindowDays;
    private Long crossQuestionAfterViewPracticeCount;
    private Double crossQuestionAfterViewCorrectRate;
    private Long crossQuestionBaselinePracticeCount;
    private Double crossQuestionBaselineCorrectRate;
    private Double crossQuestionCorrectRateLift;
    private String crossQuestionConclusionLevel;
    private String crossQuestionConclusion;
    private List<AssetTypeEffect> assetTypeStats = new ArrayList<>();

    public Integer getDays() { return days; }
    public void setDays(Integer days) { this.days = days; }
    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }
    public Long getAssetViewCount() { return assetViewCount; }
    public void setAssetViewCount(Long assetViewCount) { this.assetViewCount = assetViewCount; }
    public Long getEngagedUserCount() { return engagedUserCount; }
    public void setEngagedUserCount(Long engagedUserCount) { this.engagedUserCount = engagedUserCount; }
    public Long getViewedQuestionCount() { return viewedQuestionCount; }
    public void setViewedQuestionCount(Long viewedQuestionCount) { this.viewedQuestionCount = viewedQuestionCount; }
    public Long getFeedbackCount() { return feedbackCount; }
    public void setFeedbackCount(Long feedbackCount) { this.feedbackCount = feedbackCount; }
    public Double getHelpfulRate() { return helpfulRate; }
    public void setHelpfulRate(Double helpfulRate) { this.helpfulRate = helpfulRate; }
    public Long getVariantTrainingStartedCount() { return variantTrainingStartedCount; }
    public void setVariantTrainingStartedCount(Long variantTrainingStartedCount) { this.variantTrainingStartedCount = variantTrainingStartedCount; }
    public Long getVariantTrainingCompletedCount() { return variantTrainingCompletedCount; }
    public void setVariantTrainingCompletedCount(Long variantTrainingCompletedCount) { this.variantTrainingCompletedCount = variantTrainingCompletedCount; }
    public Double getVariantTrainingCompletionRate() { return variantTrainingCompletionRate; }
    public void setVariantTrainingCompletionRate(Double variantTrainingCompletionRate) { this.variantTrainingCompletionRate = variantTrainingCompletionRate; }
    public Long getVariantTrainingAnsweredCount() { return variantTrainingAnsweredCount; }
    public void setVariantTrainingAnsweredCount(Long variantTrainingAnsweredCount) { this.variantTrainingAnsweredCount = variantTrainingAnsweredCount; }
    public Long getVariantTrainingCorrectCount() { return variantTrainingCorrectCount; }
    public void setVariantTrainingCorrectCount(Long variantTrainingCorrectCount) { this.variantTrainingCorrectCount = variantTrainingCorrectCount; }
    public Double getVariantTrainingCorrectRate() { return variantTrainingCorrectRate; }
    public void setVariantTrainingCorrectRate(Double variantTrainingCorrectRate) { this.variantTrainingCorrectRate = variantTrainingCorrectRate; }
    public Long getAfterViewPracticeCount() { return afterViewPracticeCount; }
    public void setAfterViewPracticeCount(Long afterViewPracticeCount) { this.afterViewPracticeCount = afterViewPracticeCount; }
    public Double getAfterViewCorrectRate() { return afterViewCorrectRate; }
    public void setAfterViewCorrectRate(Double afterViewCorrectRate) { this.afterViewCorrectRate = afterViewCorrectRate; }
    public Long getBaselinePracticeCount() { return baselinePracticeCount; }
    public void setBaselinePracticeCount(Long baselinePracticeCount) { this.baselinePracticeCount = baselinePracticeCount; }
    public Double getBaselineCorrectRate() { return baselineCorrectRate; }
    public void setBaselineCorrectRate(Double baselineCorrectRate) { this.baselineCorrectRate = baselineCorrectRate; }
    public Double getCorrectRateLift() { return correctRateLift; }
    public void setCorrectRateLift(Double correctRateLift) { this.correctRateLift = correctRateLift; }
    public String getConclusionLevel() { return conclusionLevel; }
    public void setConclusionLevel(String conclusionLevel) { this.conclusionLevel = conclusionLevel; }
    public String getConclusion() { return conclusion; }
    public void setConclusion(String conclusion) { this.conclusion = conclusion; }
    public Integer getCrossQuestionWindowDays() { return crossQuestionWindowDays; }
    public void setCrossQuestionWindowDays(Integer crossQuestionWindowDays) { this.crossQuestionWindowDays = crossQuestionWindowDays; }
    public Long getCrossQuestionAfterViewPracticeCount() { return crossQuestionAfterViewPracticeCount; }
    public void setCrossQuestionAfterViewPracticeCount(Long crossQuestionAfterViewPracticeCount) { this.crossQuestionAfterViewPracticeCount = crossQuestionAfterViewPracticeCount; }
    public Double getCrossQuestionAfterViewCorrectRate() { return crossQuestionAfterViewCorrectRate; }
    public void setCrossQuestionAfterViewCorrectRate(Double crossQuestionAfterViewCorrectRate) { this.crossQuestionAfterViewCorrectRate = crossQuestionAfterViewCorrectRate; }
    public Long getCrossQuestionBaselinePracticeCount() { return crossQuestionBaselinePracticeCount; }
    public void setCrossQuestionBaselinePracticeCount(Long crossQuestionBaselinePracticeCount) { this.crossQuestionBaselinePracticeCount = crossQuestionBaselinePracticeCount; }
    public Double getCrossQuestionBaselineCorrectRate() { return crossQuestionBaselineCorrectRate; }
    public void setCrossQuestionBaselineCorrectRate(Double crossQuestionBaselineCorrectRate) { this.crossQuestionBaselineCorrectRate = crossQuestionBaselineCorrectRate; }
    public Double getCrossQuestionCorrectRateLift() { return crossQuestionCorrectRateLift; }
    public void setCrossQuestionCorrectRateLift(Double crossQuestionCorrectRateLift) { this.crossQuestionCorrectRateLift = crossQuestionCorrectRateLift; }
    public String getCrossQuestionConclusionLevel() { return crossQuestionConclusionLevel; }
    public void setCrossQuestionConclusionLevel(String crossQuestionConclusionLevel) { this.crossQuestionConclusionLevel = crossQuestionConclusionLevel; }
    public String getCrossQuestionConclusion() { return crossQuestionConclusion; }
    public void setCrossQuestionConclusion(String crossQuestionConclusion) { this.crossQuestionConclusion = crossQuestionConclusion; }
    public List<AssetTypeEffect> getAssetTypeStats() { return assetTypeStats; }
    public void setAssetTypeStats(List<AssetTypeEffect> assetTypeStats) { this.assetTypeStats = assetTypeStats; }

    public static class AssetTypeEffect {
        private String assetType;
        private String assetTypeLabel;
        private Long viewCount;
        private Long userCount;
        private Long feedbackCount;
        private Double helpfulRate;

        public String getAssetType() { return assetType; }
        public void setAssetType(String assetType) { this.assetType = assetType; }
        public String getAssetTypeLabel() { return assetTypeLabel; }
        public void setAssetTypeLabel(String assetTypeLabel) { this.assetTypeLabel = assetTypeLabel; }
        public Long getViewCount() { return viewCount; }
        public void setViewCount(Long viewCount) { this.viewCount = viewCount; }
        public Long getUserCount() { return userCount; }
        public void setUserCount(Long userCount) { this.userCount = userCount; }
        public Long getFeedbackCount() { return feedbackCount; }
        public void setFeedbackCount(Long feedbackCount) { this.feedbackCount = feedbackCount; }
        public Double getHelpfulRate() { return helpfulRate; }
        public void setHelpfulRate(Double helpfulRate) { this.helpfulRate = helpfulRate; }
    }
}
