package com.learnplatform.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class GamificationDailyGoalRequest {
  @Min(1)
  @Max(200)
  private int dailyGoal;

  public int getDailyGoal() {
    return dailyGoal;
  }

  public void setDailyGoal(int dailyGoal) {
    this.dailyGoal = dailyGoal;
  }
}
