package com.learnplatform.dto;

import java.time.LocalDate;

public class GamificationHeatmapDayVO {
  private LocalDate date;
  private int answeredCount;
  private int earnedXp;

  public LocalDate getDate() {
    return date;
  }

  public void setDate(LocalDate date) {
    this.date = date;
  }

  public int getAnsweredCount() {
    return answeredCount;
  }

  public void setAnsweredCount(int answeredCount) {
    this.answeredCount = answeredCount;
  }

  public int getEarnedXp() {
    return earnedXp;
  }

  public void setEarnedXp(int earnedXp) {
    this.earnedXp = earnedXp;
  }
}
