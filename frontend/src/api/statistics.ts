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

// ======================== 知识图谱 ========================

export interface KnowledgeGraphNode {
  id: number
  name: string
  courseId: number
  courseName: string
  parentId: number | null
  nodeType: 'root' | 'parent' | 'leaf'
  masteryLevel: number
  accuracy: number
  practiceCount: number
  wrongCount: number
  category: string
}

export interface KnowledgeGraphEdge {
  source: number
  target: number
  relationType: string
}

export interface KnowledgeGraphCourse {
  id: number
  name: string
}

export interface KnowledgeGraph {
  nodes: KnowledgeGraphNode[]
  edges: KnowledgeGraphEdge[]
  courses: KnowledgeGraphCourse[]
}

/** 获取知识图谱数据 */
export function getKnowledgeGraph(courseId?: number) {
  const params = courseId ? { courseId } : {}
  return request.get<any, ApiResponse<KnowledgeGraph>>('/statistics/knowledge-graph', { params })
}

// ======================== 学习诊断 ========================

export interface WeakPoint {
  knowledgePointId: number
  knowledgePointName: string
  courseId: number
  courseName: string
  correctRate: number
  totalAttempts: number
  wrongCount: number
  masteryStatus: 'WEAK' | 'NEEDS_REVIEW' | 'NOT_STARTED'
  priorityScore: number
  diagnosis: string
}

export interface CourseMastery {
  courseId: number
  courseName: string
  correctRate: number
  totalAttempts: number
  wrongCount: number
  knowledgePointCount: number
  weakPointCount: number
}

export interface CourseErrorCount {
  courseId: number
  courseName: string
  wrongCount: number
}

export interface KnowledgePointErrorRank {
  knowledgePointId: number
  knowledgePointName: string
  courseId: number
  courseName: string
  wrongCount: number
  totalAttempts: number
  correctRate: number
}

export interface RepeatedErrorItem {
  questionId: number
  questionContent: string
  questionType: string
  difficulty: number | null
  wrongCount: number
  masteryLevel: number | null
  lastWrongAnswer: string | null
  knowledgePointName: string | null
  courseName: string | null
}

export interface WeeklyErrorTrendItem {
  weekStart: string
  weekEnd: string
  label: string
  count: number
}

export interface ErrorPatternSummary {
  topErrorCourses: CourseErrorCount[]
  masteryDistribution: Record<string, number>
  repeatedErrorCount: number
  recentNewWrongCount: number
  questionTypeDistribution: Record<string, number>
  difficultyDistribution: Record<number, number>
  knowledgePointErrors: KnowledgePointErrorRank[]
  repeatedErrors: RepeatedErrorItem[]
  weeklyErrorTrend: WeeklyErrorTrendItem[]
}

export interface LearningHabit {
  avgDailyPractice: number
  preferredQuestionType: string
  preferredCourse: string
  weeklyTrend: DailyTrendItem[]
  frequencyLevel: 'ACTIVE' | 'MODERATE' | 'INACTIVE'
  frequencyDescription: string
}

export interface RecommendedQuestion {
  questionId: number
  reason: 'SPACED_REVIEW' | 'WEAK_POINT_REINFORCE' | 'ERROR_PRONE'
  reasonDescription: string
  questionContent: string
  questionType: string
  courseName: string | null
  difficulty: number | null
  knowledgePointName: string | null
  lastWrongAnswer: string | null
}

export interface LearningDiagnosis {
  totalPractice: number
  overallCorrectRate: number
  activeDaysLast30: number
  streakDays: number
  weakPoints: WeakPoint[]
  courseMasteries: CourseMastery[]
  errorPatterns: ErrorPatternSummary
  learningHabit: LearningHabit
  dailyRecommendations: RecommendedQuestion[]
  dailyAdvice: string
}

/** 获取学习诊断数据 */
export function getLearningDiagnosis() {
  return request.get<any, ApiResponse<LearningDiagnosis>>('/statistics/learning-diagnosis')
}

// ======================== AI 个性化学习建议 ========================

/** 获取 AI 个性化学习建议（同步） */
export function getAiAdvice() {
  return request.post<any, ApiResponse<{ content: string; source: string }>>('/statistics/ai-advice')
}

/** 获取 AI 个性化学习建议（流式 SSE） */
export function getAiAdviceStream(): Promise<Response> {
  const token = localStorage.getItem('token') || ''
  return fetch('/api/statistics/ai-advice/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    }
  })
}

// ======================== 相似题推荐 ========================

export interface SimilarQuestionItem {
  questionId: number
  questionContent: string
  questionType: string
  difficulty: number | null
  courseName: string | null
  knowledgePointName: string | null
  similarityScore: number
  reason: string
  alreadyAttempted: boolean
}

export interface SimilarQuestions {
  sourceQuestionId: number
  sourceQuestionContent: string
  similarQuestions: SimilarQuestionItem[]
}

/** 获取相似题推荐 */
export function getSimilarQuestions(questionId: number, limit = 5) {
  return request.get<any, ApiResponse<SimilarQuestions>>('/statistics/similar-questions', {
    params: { questionId, limit }
  })
}
