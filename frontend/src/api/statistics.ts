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
  return request.get<any, ApiResponse<StatisticsOverview>>('/statistics/overview')
}

/** 获取每日刷题趋势 */
export function getDailyTrend() {
  return request.get<any, ApiResponse<DailyTrendItem[]>>('/statistics/daily-trend')
}

/** 获取课程维度统计 */
export function getCourseStats() {
  return request.get<any, ApiResponse<CourseStatItem[]>>('/statistics/course-stats')
}

/** 获取管理端平台统计概览 */
export function getAdminStatisticsOverview() {
  return request.get<any, ApiResponse<AdminStatisticsOverview>>('/admin/statistics/overview')
}

export interface LearningReport {
  monthTotalPractice: number
  monthCorrectCount: number
  monthCorrectRate: number
  monthNewWrongCount: number
  monthMasteredCount: number
  monthExamCount: number
  monthExamAvgScore: number
  lastMonthTotalPractice: number
  lastMonthCorrectRate: number
  practiceGrowthRate: number
  dailyTrend: DailyTrendItem[]
  courseStats: CourseStatItem[]
  questionTypeDistribution: Record<string, number>
}

/** 获取个人学习报告 */
export function getLearningReport() {
  return request.get<any, ApiResponse<LearningReport>>('/statistics/learning-report')
}

// ======================== 学习路径推荐 ========================

export interface LearningPathStep {
  order: number
  knowledgePointId: number
  knowledgePointName: string
  courseId: number
  courseName: string
  parentId: number | null
  correctRate: number
  totalAttempts: number
  wrongCount: number
  masteryStatus: 'MASTERED' | 'NEEDS_REVIEW' | 'WEAK' | 'NOT_STARTED'
  priorityScore: number
  recommendation: string
}

export interface LearningPathCourseOverview {
  courseId: number
  courseName: string
  correctRate: number
  totalAttempts: number
  knowledgePointCount: number
  masteredPointCount: number
}

export interface LearningPath {
  courseName: string
  overallMastery: number
  totalKnowledgePoints: number
  masteredCount: number
  weakCount: number
  steps: LearningPathStep[]
  courseOverviews: LearningPathCourseOverview[]
}

/** 获取学习路径推荐 */
export function getLearningPath(courseId?: number) {
  const params = courseId ? { courseId } : {}
  return request.get<any, ApiResponse<LearningPath>>('/statistics/learning-path', { params })
}
