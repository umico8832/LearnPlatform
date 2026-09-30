package com.learnplatform.dto;

import java.time.LocalDateTime;

public class GamificationAchievementVO {
  private String code;
  private String name;
  private String description;
  private boolean unlocked;
  private LocalDateTime unlockedAt;
  private long progress;
  private long target;

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public boolean isUnlocked() {
    return unlocked;
  }

  public void setUnlocked(boolean unlocked) {
    this.unlocked = unlocked;
  }

  public LocalDateTime getUnlockedAt() {
    return unlockedAt;
  }

  public void setUnlockedAt(LocalDateTime unlockedAt) {
    this.unlockedAt = unlockedAt;
  }

  public long getProgress() {
    return progress;
  }

  public void setProgress(long progress) {
    this.progress = progress;
  }

  public long getTarget() {
    return target;
  }

  public void setTarget(long target) {
    this.target = target;
  }
}
