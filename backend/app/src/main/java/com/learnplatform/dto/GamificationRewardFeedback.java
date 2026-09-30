package com.learnplatform.dto;

import java.util.List;

public class GamificationRewardFeedback {
  private Long eventId;
  private int awardedXp;
  private String reason;
  private boolean eligible;
  private int levelBefore;
  private int levelAfter;
  private boolean leveledUp;
  private int streakDays;
  private int currentCombo;
  private int maxCombo;
  private List<GamificationAchievementVO> newAchievements;
  private GamificationSummaryVO summary;

  public Long getEventId() {
    return eventId;
  }

  public void setEventId(Long eventId) {
    this.eventId = eventId;
  }

  public int getAwardedXp() {
    return awardedXp;
  }

  public void setAwardedXp(int awardedXp) {
    this.awardedXp = awardedXp;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public boolean isEligible() {
    return eligible;
  }

  public void setEligible(boolean eligible) {
    this.eligible = eligible;
  }

  public int getLevelBefore() {
    return levelBefore;
  }

  public void setLevelBefore(int levelBefore) {
    this.levelBefore = levelBefore;
  }

  public int getLevelAfter() {
    return levelAfter;
  }

  public void setLevelAfter(int levelAfter) {
    this.levelAfter = levelAfter;
  }

  public boolean isLeveledUp() {
    return leveledUp;
  }

  public void setLeveledUp(boolean leveledUp) {
    this.leveledUp = leveledUp;
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

  public List<GamificationAchievementVO> getNewAchievements() {
    return newAchievements;
  }

  public void setNewAchievements(List<GamificationAchievementVO> newAchievements) {
    this.newAchievements = newAchievements;
  }

  public GamificationSummaryVO getSummary() {
    return summary;
  }

  public void setSummary(GamificationSummaryVO summary) {
    this.summary = summary;
  }
}
