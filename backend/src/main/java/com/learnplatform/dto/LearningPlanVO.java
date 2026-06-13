package com.learnplatform.dto;

/**
 * 学习计划视图对象
 */
public class LearningPlanVO {

    /** 每日刷题目标数 */
    private Integer dailyGoal;

    /** 今日已刷题数 */
    private Integer todayCount;

    /** 完成百分比（0-100） */
    private Integer progress;

    /** 连续打卡天数 */
    private Integer streakDays;

    /** 最近一次打卡日期（yyyy-MM-dd） */
    private String lastPracticeDate;

    public Integer getDailyGoal() { return dailyGoal; }
    public void setDailyGoal(Integer dailyGoal) { this.dailyGoal = dailyGoal; }

    public Integer getTodayCount() { return todayCount; }
    public void setTodayCount(Integer todayCount) { this.todayCount = todayCount; }

    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }

    public Integer getStreakDays() { return streakDays; }
    public void setStreakDays(Integer streakDays) { this.streakDays = streakDays; }

    public String getLastPracticeDate() { return lastPracticeDate; }
    public void setLastPracticeDate(String lastPracticeDate) { this.lastPracticeDate = lastPracticeDate; }
}