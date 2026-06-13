package com.learnplatform.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 学习计划设置请求
 */
public class LearningPlanRequest {

    /** 每日刷题目标数 */
    @NotNull(message = "每日目标不能为空")
    @Min(value = 1, message = "每日目标最少 1 题")
    @Max(value = 200, message = "每日目标最多 200 题")
    private Integer dailyGoal;

    public Integer getDailyGoal() { return dailyGoal; }
    public void setDailyGoal(Integer dailyGoal) { this.dailyGoal = dailyGoal; }
}