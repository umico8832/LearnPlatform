package com.learnplatform.dto;

/**
 * 复习统计概览 VO
 */
public class ReviewStatsVO {

    /** 总卡片数（已加入复习计划的题目数） */
    private int totalCards;

    /** 今日待复习数（今天及逾期未复习） */
    private int dueToday;

    /** 逾期未复习数 */
    private int overdue;

    /** 今日已完成复习数 */
    private int reviewedToday;

    /** 新卡片数（从未复习过） */
    private int newCards;

    /** 学习中卡片数（复习过但间隔 < 21 天） */
    private int learningCards;

    /** 已掌握卡片数（间隔 >= 21 天） */
    private int masteredCards;

    /** 困难卡片数（简易因子 < 2.0） */
    private int difficultCards;

    /** 连续复习天数 */
    private int streakDays;

    /** 平均简易因子 */
    private double avgEaseFactor;

    public int getTotalCards() { return totalCards; }
    public void setTotalCards(int totalCards) { this.totalCards = totalCards; }
    public int getDueToday() { return dueToday; }
    public void setDueToday(int dueToday) { this.dueToday = dueToday; }
    public int getOverdue() { return overdue; }
    public void setOverdue(int overdue) { this.overdue = overdue; }
    public int getReviewedToday() { return reviewedToday; }
    public void setReviewedToday(int reviewedToday) { this.reviewedToday = reviewedToday; }
    public int getNewCards() { return newCards; }
    public void setNewCards(int newCards) { this.newCards = newCards; }
    public int getLearningCards() { return learningCards; }
    public void setLearningCards(int learningCards) { this.learningCards = learningCards; }
    public int getMasteredCards() { return masteredCards; }
    public void setMasteredCards(int masteredCards) { this.masteredCards = masteredCards; }
    public int getDifficultCards() { return difficultCards; }
    public void setDifficultCards(int difficultCards) { this.difficultCards = difficultCards; }
    public int getStreakDays() { return streakDays; }
    public void setStreakDays(int streakDays) { this.streakDays = streakDays; }
    public double getAvgEaseFactor() { return avgEaseFactor; }
    public void setAvgEaseFactor(double avgEaseFactor) { this.avgEaseFactor = avgEaseFactor; }
}