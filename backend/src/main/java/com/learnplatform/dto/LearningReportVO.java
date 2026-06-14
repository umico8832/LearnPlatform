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

    /** 本月每日刷题趋势 */
    private List<Map<String, Object>> dailyTrend;

    /** 本月各课程正确率 */
    private List<Map<String, Object>> courseStats;

    /** 本月各题型刷题分布 */
    private Map<String, Integer> questionTypeDistribution;

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
}