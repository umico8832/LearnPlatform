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

export interface AdminDailyActivity {
  date: string
  practiceCount: number
  activeUsers: number
}

export interface AdminStatisticsOverview {
  totalUsers: number
  enabledUsers: number
  totalQuestions: number
  weeklyNewQuestions: number
  totalExamPapers: number
  publishedExamPapers: number
  draftExamPapers: number
  todayActiveUsers: number
  totalPracticeRecords: number
  questionTypeDistribution: Record<string, number>
  dailyActivity: AdminDailyActivity[]
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

/** 获取管理端平台统计概览 */
export function getAdminStatisticsOverview() {
  return request.get<any, ApiResponse<AdminStatisticsOverview>>('/admin/statistics/overview')
}
