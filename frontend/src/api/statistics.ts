import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export interface DailyTrendItem {
  date: string
  total: number
  correct: number
  wrong: number
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

/** 获取管理端平台统计概览 */
export function getAdminStatisticsOverview() {
  return request.get<unknown, ApiResponse<AdminStatisticsOverview>>('/admin/statistics/overview')
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
  return request.get<unknown, ApiResponse<LearningDiagnosis>>('/statistics/learning-diagnosis')
}

// ======================== AI 个性化学习建议 ========================

/** 获取 AI 个性化学习建议（同步） */
export function getAiAdvice() {
  return request.post<unknown, ApiResponse<{ content: string; source: string }>>('/statistics/ai-advice')
}

/** 获取 AI 个性化学习建议（流式 SSE） */
export function getAiAdviceStream(): Promise<Response> {
  const token = localStorage.getItem('token') || ''
  const base = import.meta.env.VITE_API_BASE_URL || '/api'
  return fetch(`${base}/statistics/ai-advice/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
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
  return request.get<unknown, ApiResponse<SimilarQuestions>>('/statistics/similar-questions', {
    params: { questionId, limit },
  })
}

// ======================== 单题错因分析 ========================

export interface AttemptHistory {
  recordId: number
  userAnswer: string | null
  isCorrect: number | null
  answerTime: number | null
  createTime: string | null
}

export interface QuestionErrorAnalysis {
  questionId: number
  questionContent: string
  questionType: string
  difficulty: number | null
  courseName: string | null
  knowledgePointName: string | null
  totalAttempts: number
  correctCount: number
  wrongCount: number
  correctRate: number
  currentMasteryLevel: number | null
  masteryTrend: 'IMPROVING' | 'STAGNANT' | 'DECLINING'
  trendDescription: string
  attempts: AttemptHistory[]
  errorPattern: string
}

/** 获取单题错因分析 */
export function getQuestionErrorAnalysis(questionId: number) {
  return request.get<unknown, ApiResponse<QuestionErrorAnalysis>>('/statistics/question-error-analysis', {
    params: { questionId },
  })
}
