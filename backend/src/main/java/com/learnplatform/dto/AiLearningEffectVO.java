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
    private Long minimumComparisonSample;
    private Long minimumDistinctUsers;
    private Long variantTrainingStartedCount;
    private Long variantTrainingCompletedCount;
    private Double variantTrainingCompletionRate;
    private Long variantTrainingAnsweredCount;
    private Long variantTrainingCorrectCount;
    private Double variantTrainingCorrectRate;
    private Long variantDifficultyMinimumSample;
    private Long variantDifficultyCoveredCount;
    private Long variantDifficultySufficientCount;
    private String variantDifficultyReadiness;
    private String variantDifficultyConclusion;
    private List<VariantDifficultyEffect> variantDifficultyStats = new ArrayList<>();
    private Long afterViewPracticeCount;
    private Long afterViewUserCount;
    private Double afterViewCorrectRate;
    private Long baselinePracticeCount;
    private Long baselineUserCount;
    private Double baselineCorrectRate;
    private Double correctRateLift;
    private String conclusionLevel;
    private String conclusion;
    private Integer crossQuestionWindowDays;
    private Long crossQuestionAfterViewPracticeCount;
    private Long crossQuestionAfterViewUserCount;
    private Double crossQuestionAfterViewCorrectRate;
    private Long crossQuestionBaselinePracticeCount;
    private Long crossQuestionBaselineUserCount;
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
    public Long getMinimumComparisonSample() { return minimumComparisonSample; }
    public void setMinimumComparisonSample(Long minimumComparisonSample) { this.minimumComparisonSample = minimumComparisonSample; }
    public Long getMinimumDistinctUsers() { return minimumDistinctUsers; }
    public void setMinimumDistinctUsers(Long minimumDistinctUsers) { this.minimumDistinctUsers = minimumDistinctUsers; }
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
    public Long getVariantDifficultyMinimumSample() { return variantDifficultyMinimumSample; }
    public void setVariantDifficultyMinimumSample(Long variantDifficultyMinimumSample) { this.variantDifficultyMinimumSample = variantDifficultyMinimumSample; }
    public Long getVariantDifficultyCoveredCount() { return variantDifficultyCoveredCount; }
    public void setVariantDifficultyCoveredCount(Long variantDifficultyCoveredCount) { this.variantDifficultyCoveredCount = variantDifficultyCoveredCount; }
    public Long getVariantDifficultySufficientCount() { return variantDifficultySufficientCount; }
    public void setVariantDifficultySufficientCount(Long variantDifficultySufficientCount) { this.variantDifficultySufficientCount = variantDifficultySufficientCount; }
    public String getVariantDifficultyReadiness() { return variantDifficultyReadiness; }
    public void setVariantDifficultyReadiness(String variantDifficultyReadiness) { this.variantDifficultyReadiness = variantDifficultyReadiness; }
    public String getVariantDifficultyConclusion() { return variantDifficultyConclusion; }
    public void setVariantDifficultyConclusion(String variantDifficultyConclusion) { this.variantDifficultyConclusion = variantDifficultyConclusion; }
    public List<VariantDifficultyEffect> getVariantDifficultyStats() { return variantDifficultyStats; }
    public void setVariantDifficultyStats(List<VariantDifficultyEffect> variantDifficultyStats) { this.variantDifficultyStats = variantDifficultyStats; }
    public Long getAfterViewPracticeCount() { return afterViewPracticeCount; }
    public void setAfterViewPracticeCount(Long afterViewPracticeCount) { this.afterViewPracticeCount = afterViewPracticeCount; }
    public Long getAfterViewUserCount() { return afterViewUserCount; }
    public void setAfterViewUserCount(Long afterViewUserCount) { this.afterViewUserCount = afterViewUserCount; }
    public Double getAfterViewCorrectRate() { return afterViewCorrectRate; }
    public void setAfterViewCorrectRate(Double afterViewCorrectRate) { this.afterViewCorrectRate = afterViewCorrectRate; }
    public Long getBaselinePracticeCount() { return baselinePracticeCount; }
    public void setBaselinePracticeCount(Long baselinePracticeCount) { this.baselinePracticeCount = baselinePracticeCount; }
    public Long getBaselineUserCount() { return baselineUserCount; }
    public void setBaselineUserCount(Long baselineUserCount) { this.baselineUserCount = baselineUserCount; }
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
    public Long getCrossQuestionAfterViewUserCount() { return crossQuestionAfterViewUserCount; }
    public void setCrossQuestionAfterViewUserCount(Long crossQuestionAfterViewUserCount) { this.crossQuestionAfterViewUserCount = crossQuestionAfterViewUserCount; }
    public Double getCrossQuestionAfterViewCorrectRate() { return crossQuestionAfterViewCorrectRate; }
    public void setCrossQuestionAfterViewCorrectRate(Double crossQuestionAfterViewCorrectRate) { this.crossQuestionAfterViewCorrectRate = crossQuestionAfterViewCorrectRate; }
    public Long getCrossQuestionBaselinePracticeCount() { return crossQuestionBaselinePracticeCount; }
    public void setCrossQuestionBaselinePracticeCount(Long crossQuestionBaselinePracticeCount) { this.crossQuestionBaselinePracticeCount = crossQuestionBaselinePracticeCount; }
    public Long getCrossQuestionBaselineUserCount() { return crossQuestionBaselineUserCount; }
    public void setCrossQuestionBaselineUserCount(Long crossQuestionBaselineUserCount) { this.crossQuestionBaselineUserCount = crossQuestionBaselineUserCount; }
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
        private Long afterViewPracticeCount;
        private Long afterViewUserCount;
        private Double afterViewCorrectRate;
        private Long baselinePracticeCount;
        private Long baselineUserCount;
        private Double baselineCorrectRate;
        private Double correctRateLift;
        private Boolean sampleSufficient;
        private String conclusionLevel;
        private String conclusion;

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
        public Long getAfterViewPracticeCount() { return afterViewPracticeCount; }
        public void setAfterViewPracticeCount(Long afterViewPracticeCount) { this.afterViewPracticeCount = afterViewPracticeCount; }
        public Long getAfterViewUserCount() { return afterViewUserCount; }
        public void setAfterViewUserCount(Long afterViewUserCount) { this.afterViewUserCount = afterViewUserCount; }
        public Double getAfterViewCorrectRate() { return afterViewCorrectRate; }
        public void setAfterViewCorrectRate(Double afterViewCorrectRate) { this.afterViewCorrectRate = afterViewCorrectRate; }
        public Long getBaselinePracticeCount() { return baselinePracticeCount; }
        public void setBaselinePracticeCount(Long baselinePracticeCount) { this.baselinePracticeCount = baselinePracticeCount; }
        public Long getBaselineUserCount() { return baselineUserCount; }
        public void setBaselineUserCount(Long baselineUserCount) { this.baselineUserCount = baselineUserCount; }
        public Double getBaselineCorrectRate() { return baselineCorrectRate; }
        public void setBaselineCorrectRate(Double baselineCorrectRate) { this.baselineCorrectRate = baselineCorrectRate; }
        public Double getCorrectRateLift() { return correctRateLift; }
        public void setCorrectRateLift(Double correctRateLift) { this.correctRateLift = correctRateLift; }
        public Boolean getSampleSufficient() { return sampleSufficient; }
        public void setSampleSufficient(Boolean sampleSufficient) { this.sampleSufficient = sampleSufficient; }
        public String getConclusionLevel() { return conclusionLevel; }
        public void setConclusionLevel(String conclusionLevel) { this.conclusionLevel = conclusionLevel; }
        public String getConclusion() { return conclusion; }
        public void setConclusion(String conclusion) { this.conclusion = conclusion; }
    }

    public static class VariantDifficultyEffect {
        private Integer difficulty;
        private String difficultyLabel;
        private Long answeredCount;
        private Long answeredUserCount;
        private Long correctCount;
        private Double correctRate;
        private Boolean sampleSufficient;

        public Integer getDifficulty() { return difficulty; }
        public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }
        public String getDifficultyLabel() { return difficultyLabel; }
        public void setDifficultyLabel(String difficultyLabel) { this.difficultyLabel = difficultyLabel; }
        public Long getAnsweredCount() { return answeredCount; }
        public void setAnsweredCount(Long answeredCount) { this.answeredCount = answeredCount; }
        public Long getAnsweredUserCount() { return answeredUserCount; }
        public void setAnsweredUserCount(Long answeredUserCount) { this.answeredUserCount = answeredUserCount; }
        public Long getCorrectCount() { return correctCount; }
        public void setCorrectCount(Long correctCount) { this.correctCount = correctCount; }
        public Double getCorrectRate() { return correctRate; }
        public void setCorrectRate(Double correctRate) { this.correctRate = correctRate; }
        public Boolean getSampleSufficient() { return sampleSufficient; }
        public void setSampleSufficient(Boolean sampleSufficient) { this.sampleSufficient = sampleSufficient; }
    }
}
