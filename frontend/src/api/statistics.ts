import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export interface StatisticsOverview {
  totalPractice: number
  correctCount: number
  wrongCount: number
  correctRate: number
  todayPractice: number
  streakDays: number
  wrongQuestionCount: number
  masteredCount: number
}

export interface DailyTrendItem {
  date: string
  total: number
  correct: number
  wrong: number
}

export interface CourseStatItem {
  courseId: number
  courseName: string
  total: number
  correct: number
  correctRate: number
}

/** 获取学习统计概览 */
export function getStatisticsOverview() {
  return request.get<any, ApiResponse<StatisticsOverview>>('/api/statistics/overview')
}

/** 获取每日刷题趋势 */
export function getDailyTrend() {
  return request.get<any, ApiResponse<DailyTrendItem[]>>('/api/statistics/daily-trend')
}

/** 获取课程维度统计 */
export function getCourseStats() {
  return request.get<any, ApiResponse<CourseStatItem[]>>('/api/statistics/course-stats')
}