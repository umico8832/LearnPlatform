import request from '@/utils/request'
import type { ApiResponse, PageData } from '@/types/api'
import type { RewardFeedback } from './gamification'

// ======================== 类型定义 ========================

/** 练习题目 VO */
export interface PracticeQuestionVO {
  id: number
  content: string
  questionType: string
  courseId: number
  courseName: string
  difficulty: number
  score: number
  tags: string | null
  options: PracticeOptionVO[]
  knowledgePointIds: number[]
  knowledgePointNames: string[]
}

/** 练习选项 VO（不暴露正确答案） */
export interface PracticeOptionVO {
  id: number
  content: string
  optionLabel: string
  sortOrder: number
}

/** 提交答案请求 */
export interface PracticeSubmitRequest {
  questionId: number
  userAnswer: string
  answerTime?: number
}

/** 答题结果 */
export interface PracticeResultVO {
  reward?: RewardFeedback | null
  recordId: number
  questionId: number
  userAnswer: string
  correct: boolean | null
  correctAnswer: string
  analysis: string
  score: number
}

/** 练习记录 */
export interface PracticeRecordVO {
  id: number
  questionId: number
  questionContent: string
  questionType: string
  courseName: string
  difficulty: number
  userAnswer: string
  isCorrect: number | null
  answerTime: number
  createTime: string
}

/** 练习统计 */
export interface PracticeStatsVO {
  totalAnswered: number
  correctCount: number
  wrongCount: number
  correctRate: number
}

// ======================== API 方法 ========================

export interface PracticeRequestOptions {
  errorDisplay?: 'inline'
}

function requestConfig<T>(params: T, options?: PracticeRequestOptions) {
  return options ? { params, ...options } : { params }
}

/** 获取练习题目（随机抽取） */
export function getPracticeQuestions(
  params?: {
    courseId?: number
    knowledgePointId?: number
    questionType?: string
    difficulty?: number
    count?: number
  },
  options?: PracticeRequestOptions,
) {
  return request.get<unknown, ApiResponse<PracticeQuestionVO[]>>('/practice/questions', requestConfig(params, options))
}

/** 提交答案 */
export function submitAnswer(data: PracticeSubmitRequest, options?: PracticeRequestOptions) {
  return options
    ? request.post<unknown, ApiResponse<PracticeResultVO>>('/practice/submit', data, options)
    : request.post<unknown, ApiResponse<PracticeResultVO>>('/practice/submit', data)
}

/** 获取练习记录（分页） */
export function getPracticeRecords(
  params: {
    pageNum?: number
    pageSize?: number
    questionType?: string
    courseId?: number
    isCorrect?: number
  },
  options?: PracticeRequestOptions,
) {
  return request.get<unknown, ApiResponse<PageData<PracticeRecordVO>>>(
    '/practice/records',
    requestConfig(params, options),
  )
}

/** 获取练习统计 */
export function getPracticeStats(options?: PracticeRequestOptions) {
  return options
    ? request.get<unknown, ApiResponse<PracticeStatsVO>>('/practice/stats', options)
    : request.get<unknown, ApiResponse<PracticeStatsVO>>('/practice/stats')
}

/** 获取错题重练题目 */
export function getWrongQuestionPractice(
  params?: {
    masteryLevel?: number
    count?: number
    courseId?: number
    knowledgePointId?: number
    questionId?: number
  },
  options?: PracticeRequestOptions,
) {
  return request.get<unknown, ApiResponse<PracticeQuestionVO[]>>(
    '/practice/wrong-questions',
    requestConfig(params, options),
  )
}

/** 获取收藏题练习题目 */
export function getFavoritePractice(
  params?: { count?: number; questionId?: number },
  options?: PracticeRequestOptions,
) {
  return request.get<unknown, ApiResponse<PracticeQuestionVO[]>>('/practice/favorites', requestConfig(params, options))
}

/** 自适应智能推荐题目 */
export function getAdaptiveQuestions(
  params?: {
    courseId?: number
    knowledgePointId?: number
    questionType?: string
    count?: number
  },
  options?: PracticeRequestOptions,
) {
  return request.get<unknown, ApiResponse<PracticeQuestionVO[]>>('/practice/adaptive', requestConfig(params, options))
}

/** 获取自适应推荐摘要（各难度答题表现和推荐权重） */
export function getAdaptiveSummary(options?: PracticeRequestOptions) {
  return options
    ? request.get<unknown, ApiResponse<AdaptiveSummaryVO>>('/practice/adaptive/summary', options)
    : request.get<unknown, ApiResponse<AdaptiveSummaryVO>>('/practice/adaptive/summary')
}

/** 自适应推荐摘要 VO */
export interface AdaptiveSummaryVO {
  totalAnswered: number
  overallCorrectRate: number
  recommendedDifficulty: number
  difficultyDetails: AdaptiveDifficultyDetail[]
}

/** 自适应难度详情 */
export interface AdaptiveDifficultyDetail {
  difficulty: number
  label: string
  total: number
  correct: number
  correctRate: number
  weight: number
}
