package com.learnplatform.dto;

import java.util.List;

/** Aggregated reward feedback for one completed exam submission. */
public class GamificationSubmissionRewardVO {
    private int awardedXp;
    private int eventCount;
    private int levelBefore;
    private int levelAfter;
    private boolean leveledUp;
    private List<GamificationAchievementVO> newAchievements;
    private GamificationSummaryVO summary;

    public int getAwardedXp() { return awardedXp; }
    public void setAwardedXp(int awardedXp) { this.awardedXp = awardedXp; }
    public int getEventCount() { return eventCount; }
    public void setEventCount(int eventCount) { this.eventCount = eventCount; }
    public int getLevelBefore() { return levelBefore; }
    public void setLevelBefore(int levelBefore) { this.levelBefore = levelBefore; }
    public int getLevelAfter() { return levelAfter; }
    public void setLevelAfter(int levelAfter) { this.levelAfter = levelAfter; }
    public boolean isLeveledUp() { return leveledUp; }
    public void setLeveledUp(boolean leveledUp) { this.leveledUp = leveledUp; }
    public List<GamificationAchievementVO> getNewAchievements() { return newAchievements; }
    public void setNewAchievements(List<GamificationAchievementVO> newAchievements) {
        this.newAchievements = newAchievements;
    }
    public GamificationSummaryVO getSummary() { return summary; }
    public void setSummary(GamificationSummaryVO summary) { this.summary = summary; }
}
