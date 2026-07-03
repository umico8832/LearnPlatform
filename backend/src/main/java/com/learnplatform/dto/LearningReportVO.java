package com.learnplatform.dto;

import java.util.List;
import java.util.Map;

/**
 * 个人学习报告 VO
 */
public class LearningReportVO {

    /** 本月总刷题数 */
    private Integer monthTotalPractice;

    /** 本月答对数 */
    private Integer monthCorrectCount;

    /** 本月正确率 */
    private Double monthCorrectRate;

    /** 本月错题新增数 */
    private Integer monthNewWrongCount;

    /** 本月已掌握错题数 */
    private Integer monthMasteredCount;

    /** 本月考试参加次数 */
    private Integer monthExamCount;

    /** 本月考试平均分 */
    private Double monthExamAvgScore;

    /** 上月总刷题数 */
    private Integer lastMonthTotalPractice;

    /** 上月正确率 */
    private Double lastMonthCorrectRate;

    /** 刷题量环比增长率（百分比） */
    private Double practiceGrowthRate;

    /** 正确率较上月变化（百分点） */
    private Double correctRateChange;

    /** 学习效果综合分（0-100） */
    private Double learningEffectScore;

    /** 学习效果等级：EXCELLENT / IMPROVING / STABLE / AT_RISK */
    private String learningEffectLevel;

    /** 学习效果等级文案 */
    private String learningEffectLabel;

    /** 学习效果建议摘要 */
    private String learningEffectSummary;

    /** 错题转化率（已掌握错题 / 新增错题与已掌握错题合计） */
    private Double wrongQuestionConversionRate;

    /** 复习掌握率（已掌握复习卡片 / 复习卡片总数） */
    private Double reviewMasteryRate;

    /** 本月活跃学习天数 */
    private Integer activeStudyDays;

    /** 本月每日刷题趋势 */
    private List<Map<String, Object>> dailyTrend;

    /** 本月各课程正确率 */
    private List<Map<String, Object>> courseStats;

    /** 本月各题型刷题分布 */
    private Map<String, Integer> questionTypeDistribution;

    // ======================== 复习统计 ========================

    /** 复习计划总卡片数 */
    private Integer totalReviewCards;

    /** 本月完成复习次数 */
    private Integer monthlyReviewedCount;

    /** 连续复习天数 */
    private Integer reviewStreakDays;

    /** 已掌握卡片数（间隔 >= 21 天） */
    private Integer masteredReviewCards;

    /** 今日待复习数 */
    private Integer dueTodayCount;

    /** 本月每日复习趋势 */
    private List<Integer> monthlyReviewTrend;

    public Integer getMonthTotalPractice() {
        return monthTotalPractice;
    }

    public void setMonthTotalPractice(Integer monthTotalPractice) {
        this.monthTotalPractice = monthTotalPractice;
    }

    public Integer getMonthCorrectCount() {
        return monthCorrectCount;
    }

    public void setMonthCorrectCount(Integer monthCorrectCount) {
        this.monthCorrectCount = monthCorrectCount;
    }

    public Double getMonthCorrectRate() {
        return monthCorrectRate;
    }

    public void setMonthCorrectRate(Double monthCorrectRate) {
        this.monthCorrectRate = monthCorrectRate;
    }

    public Integer getMonthNewWrongCount() {
        return monthNewWrongCount;
    }

    public void setMonthNewWrongCount(Integer monthNewWrongCount) {
        this.monthNewWrongCount = monthNewWrongCount;
    }

    public Integer getMonthMasteredCount() {
        return monthMasteredCount;
    }

    public void setMonthMasteredCount(Integer monthMasteredCount) {
        this.monthMasteredCount = monthMasteredCount;
    }

    public Integer getMonthExamCount() {
        return monthExamCount;
    }

    public void setMonthExamCount(Integer monthExamCount) {
        this.monthExamCount = monthExamCount;
    }

    public Double getMonthExamAvgScore() {
        return monthExamAvgScore;
    }

    public void setMonthExamAvgScore(Double monthExamAvgScore) {
        this.monthExamAvgScore = monthExamAvgScore;
    }

    public Integer getLastMonthTotalPractice() {
        return lastMonthTotalPractice;
    }

    public void setLastMonthTotalPractice(Integer lastMonthTotalPractice) {
        this.lastMonthTotalPractice = lastMonthTotalPractice;
    }

    public Double getLastMonthCorrectRate() {
        return lastMonthCorrectRate;
    }

    public void setLastMonthCorrectRate(Double lastMonthCorrectRate) {
        this.lastMonthCorrectRate = lastMonthCorrectRate;
    }

    public Double getPracticeGrowthRate() {
        return practiceGrowthRate;
    }

    public void setPracticeGrowthRate(Double practiceGrowthRate) {
        this.practiceGrowthRate = practiceGrowthRate;
    }

    public Double getCorrectRateChange() {
        return correctRateChange;
    }

    public void setCorrectRateChange(Double correctRateChange) {
        this.correctRateChange = correctRateChange;
    }

    public Double getLearningEffectScore() {
        return learningEffectScore;
    }

    public void setLearningEffectScore(Double learningEffectScore) {
        this.learningEffectScore = learningEffectScore;
    }

    public String getLearningEffectLevel() {
        return learningEffectLevel;
    }

    public void setLearningEffectLevel(String learningEffectLevel) {
        this.learningEffectLevel = learningEffectLevel;
    }

    public String getLearningEffectLabel() {
        return learningEffectLabel;
    }

    public void setLearningEffectLabel(String learningEffectLabel) {
        this.learningEffectLabel = learningEffectLabel;
    }

    public String getLearningEffectSummary() {
        return learningEffectSummary;
    }

    public void setLearningEffectSummary(String learningEffectSummary) {
        this.learningEffectSummary = learningEffectSummary;
    }

    public Double getWrongQuestionConversionRate() {
        return wrongQuestionConversionRate;
    }

    public void setWrongQuestionConversionRate(Double wrongQuestionConversionRate) {
        this.wrongQuestionConversionRate = wrongQuestionConversionRate;
    }

    public Double getReviewMasteryRate() {
        return reviewMasteryRate;
    }

    public void setReviewMasteryRate(Double reviewMasteryRate) {
        this.reviewMasteryRate = reviewMasteryRate;
    }

    public Integer getActiveStudyDays() {
        return activeStudyDays;
    }

    public void setActiveStudyDays(Integer activeStudyDays) {
        this.activeStudyDays = activeStudyDays;
    }

    public List<Map<String, Object>> getDailyTrend() {
        return dailyTrend;
    }

    public void setDailyTrend(List<Map<String, Object>> dailyTrend) {
        this.dailyTrend = dailyTrend;
    }

    public List<Map<String, Object>> getCourseStats() {
        return courseStats;
    }

    public void setCourseStats(List<Map<String, Object>> courseStats) {
        this.courseStats = courseStats;
    }

    public Map<String, Integer> getQuestionTypeDistribution() {
        return questionTypeDistribution;
    }

    public void setQuestionTypeDistribution(Map<String, Integer> questionTypeDistribution) {
        this.questionTypeDistribution = questionTypeDistribution;
    }

    public Integer getTotalReviewCards() {
        return totalReviewCards;
    }

    public void setTotalReviewCards(Integer totalReviewCards) {
        this.totalReviewCards = totalReviewCards;
    }

    public Integer getMonthlyReviewedCount() {
        return monthlyReviewedCount;
    }

    public void setMonthlyReviewedCount(Integer monthlyReviewedCount) {
        this.monthlyReviewedCount = monthlyReviewedCount;
    }

    public Integer getReviewStreakDays() {
        return reviewStreakDays;
    }

    public void setReviewStreakDays(Integer reviewStreakDays) {
        this.reviewStreakDays = reviewStreakDays;
    }

    public Integer getMasteredReviewCards() {
        return masteredReviewCards;
    }

    public void setMasteredReviewCards(Integer masteredReviewCards) {
        this.masteredReviewCards = masteredReviewCards;
    }

    public Integer getDueTodayCount() {
        return dueTodayCount;
    }

    public void setDueTodayCount(Integer dueTodayCount) {
        this.dueTodayCount = dueTodayCount;
    }

    public List<Integer> getMonthlyReviewTrend() {
        return monthlyReviewTrend;
    }

    public void setMonthlyReviewTrend(List<Integer> monthlyReviewTrend) {
        this.monthlyReviewTrend = monthlyReviewTrend;
    }
}
