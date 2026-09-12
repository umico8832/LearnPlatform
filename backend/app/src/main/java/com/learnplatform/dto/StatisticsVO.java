package com.learnplatform.dto;

/**
 * 用户学习统计 VO
 */
public class StatisticsVO {

    /** 总刷题数 */
    private long totalPractice;

    /** 答对数 */
    private long correctCount;

    /** 答错数 */
    private long wrongCount;

    /** 正确率 */
    private double correctRate;

    /** 今日刷题数 */
    private long todayPractice;

    /** 连续刷题天数 */
    private int streakDays;

    /** 错题本数量 */
    private long wrongQuestionCount;

    /** 已掌握错题数 */
    private long masteredCount;

    public long getTotalPractice() { return totalPractice; }
    public void setTotalPractice(long totalPractice) { this.totalPractice = totalPractice; }

    public long getCorrectCount() { return correctCount; }
    public void setCorrectCount(long correctCount) { this.correctCount = correctCount; }

    public long getWrongCount() { return wrongCount; }
    public void setWrongCount(long wrongCount) { this.wrongCount = wrongCount; }

    public double getCorrectRate() { return correctRate; }
    public void setCorrectRate(double correctRate) { this.correctRate = correctRate; }

    public long getTodayPractice() { return todayPractice; }
    public void setTodayPractice(long todayPractice) { this.todayPractice = todayPractice; }

    public int getStreakDays() { return streakDays; }
    public void setStreakDays(int streakDays) { this.streakDays = streakDays; }

    public long getWrongQuestionCount() { return wrongQuestionCount; }
    public void setWrongQuestionCount(long wrongQuestionCount) { this.wrongQuestionCount = wrongQuestionCount; }

    public long getMasteredCount() { return masteredCount; }
    public void setMasteredCount(long masteredCount) { this.masteredCount = masteredCount; }
}