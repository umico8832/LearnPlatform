package com.learnplatform.dto;

import java.util.List;

public class GamificationSummaryVO {
  private long totalXp;
  private int level;
  private long levelStartXp;
  private Long nextLevelXp;
  private long xpIntoLevel;
  private long xpToNextLevel;
  private String levelTitle;
  private int streakDays;
  private int currentCombo;
  private int maxCombo;
  private int todayAnsweredCount;
  private int dailyGoal;
  private boolean todayGoalReached;
  private String zoneId;
  private long version;
  private long lastEventId;
  private List<GamificationAchievementVO> recentAchievements;

  public long getTotalXp() {
    return totalXp;
  }

  public void setTotalXp(long totalXp) {
    this.totalXp = totalXp;
  }

  public int getLevel() {
    return level;
  }

  public void setLevel(int level) {
    this.level = level;
  }

  public long getLevelStartXp() {
    return levelStartXp;
  }

  public void setLevelStartXp(long levelStartXp) {
    this.levelStartXp = levelStartXp;
  }

  public Long getNextLevelXp() {
    return nextLevelXp;
  }

  public void setNextLevelXp(Long nextLevelXp) {
    this.nextLevelXp = nextLevelXp;
  }

  public long getXpIntoLevel() {
    return xpIntoLevel;
  }

  public void setXpIntoLevel(long xpIntoLevel) {
    this.xpIntoLevel = xpIntoLevel;
  }

  public long getXpToNextLevel() {
    return xpToNextLevel;
  }

  public void setXpToNextLevel(long xpToNextLevel) {
    this.xpToNextLevel = xpToNextLevel;
  }

  public String getLevelTitle() {
    return levelTitle;
  }

  public void setLevelTitle(String levelTitle) {
    this.levelTitle = levelTitle;
  }

  public int getStreakDays() {
    return streakDays;
  }

  public void setStreakDays(int streakDays) {
    this.streakDays = streakDays;
  }

  public int getCurrentCombo() {
    return currentCombo;
  }

  public void setCurrentCombo(int currentCombo) {
    this.currentCombo = currentCombo;
  }

  public int getMaxCombo() {
    return maxCombo;
  }

  public void setMaxCombo(int maxCombo) {
    this.maxCombo = maxCombo;
  }

  public int getTodayAnsweredCount() {
    return todayAnsweredCount;
  }

  public void setTodayAnsweredCount(int todayAnsweredCount) {
    this.todayAnsweredCount = todayAnsweredCount;
  }

  public int getDailyGoal() {
    return dailyGoal;
  }

  public void setDailyGoal(int dailyGoal) {
    this.dailyGoal = dailyGoal;
  }

  public boolean isTodayGoalReached() {
    return todayGoalReached;
  }

  public void setTodayGoalReached(boolean todayGoalReached) {
    this.todayGoalReached = todayGoalReached;
  }

  public String getZoneId() {
    return zoneId;
  }

  public void setZoneId(String zoneId) {
    this.zoneId = zoneId;
  }

  public long getVersion() {
    return version;
  }

  public void setVersion(long version) {
    this.version = version;
  }

  public long getLastEventId() {
    return lastEventId;
  }

  public void setLastEventId(long lastEventId) {
    this.lastEventId = lastEventId;
  }

  public List<GamificationAchievementVO> getRecentAchievements() {
    return recentAchievements;
  }

  public void setRecentAchievements(List<GamificationAchievementVO> recentAchievements) {
    this.recentAchievements = recentAchievements;
  }
}
