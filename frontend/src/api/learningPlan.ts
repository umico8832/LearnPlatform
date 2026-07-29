import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

/** 学习计划 VO */
export interface LearningPlanVO {
  dailyGoal: number
  todayCount: number
  progress: number
  streakDays: number
  lastPracticeDate: string | null
}

/** 获取学习计划（含今日进度和连续打卡） */
export function getLearningPlan() {
  return request.get<unknown, ApiResponse<LearningPlanVO>>('/learning-plan')
}

/** 更新每日刷题目标 */
export function updateDailyGoal(dailyGoal: number) {
  return request.put<unknown, ApiResponse<LearningPlanVO>>('/learning-plan', { dailyGoal })
}
