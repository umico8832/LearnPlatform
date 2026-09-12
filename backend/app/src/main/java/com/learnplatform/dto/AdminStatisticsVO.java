package com.learnplatform.dto;

import java.util.List;
import java.util.Map;

/**
 * 管理端平台统计 VO
 */
public class AdminStatisticsVO {

    private long totalUsers;
    private long enabledUsers;
    private long totalQuestions;
    private long weeklyNewQuestions;
    private long totalExamPapers;
    private long publishedExamPapers;
    private long draftExamPapers;
    private long todayActiveUsers;
    private long totalPracticeRecords;
    private Map<String, Long> questionTypeDistribution;
    private List<DailyActivity> dailyActivity;

    public long getTotalUsers() { return totalUsers; }
    public void setTotalUsers(long totalUsers) { this.totalUsers = totalUsers; }
    public long getEnabledUsers() { return enabledUsers; }
    public void setEnabledUsers(long enabledUsers) { this.enabledUsers = enabledUsers; }
    public long getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(long totalQuestions) { this.totalQuestions = totalQuestions; }
    public long getWeeklyNewQuestions() { return weeklyNewQuestions; }
    public void setWeeklyNewQuestions(long weeklyNewQuestions) { this.weeklyNewQuestions = weeklyNewQuestions; }
    public long getTotalExamPapers() { return totalExamPapers; }
    public void setTotalExamPapers(long totalExamPapers) { this.totalExamPapers = totalExamPapers; }
    public long getPublishedExamPapers() { return publishedExamPapers; }
    public void setPublishedExamPapers(long publishedExamPapers) { this.publishedExamPapers = publishedExamPapers; }
    public long getDraftExamPapers() { return draftExamPapers; }
    public void setDraftExamPapers(long draftExamPapers) { this.draftExamPapers = draftExamPapers; }
    public long getTodayActiveUsers() { return todayActiveUsers; }
    public void setTodayActiveUsers(long todayActiveUsers) { this.todayActiveUsers = todayActiveUsers; }
    public long getTotalPracticeRecords() { return totalPracticeRecords; }
    public void setTotalPracticeRecords(long totalPracticeRecords) { this.totalPracticeRecords = totalPracticeRecords; }
    public Map<String, Long> getQuestionTypeDistribution() { return questionTypeDistribution; }
    public void setQuestionTypeDistribution(Map<String, Long> questionTypeDistribution) {
        this.questionTypeDistribution = questionTypeDistribution;
    }
    public List<DailyActivity> getDailyActivity() { return dailyActivity; }
    public void setDailyActivity(List<DailyActivity> dailyActivity) { this.dailyActivity = dailyActivity; }

    public static class DailyActivity {
        private String date;
        private long practiceCount;
        private long activeUsers;

        public DailyActivity() {}

        public DailyActivity(String date, long practiceCount, long activeUsers) {
            this.date = date;
            this.practiceCount = practiceCount;
            this.activeUsers = activeUsers;
        }

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public long getPracticeCount() { return practiceCount; }
        public void setPracticeCount(long practiceCount) { this.practiceCount = practiceCount; }
        public long getActiveUsers() { return activeUsers; }
        public void setActiveUsers(long activeUsers) { this.activeUsers = activeUsers; }
    }
}
