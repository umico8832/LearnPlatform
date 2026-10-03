import request from '@/utils/request'
import type { RewardFeedback } from './gamification'

export interface ReviewRequestOptions {
  errorDisplay?: 'inline'
}

/** 复习计划卡片 */
export interface ReviewScheduleVO {
  availableForReview?: boolean
  correct?: boolean | null
  reward?: RewardFeedback | null
  id: number
  questionId: number
  questionContent: string
  questionType: string
  difficulty: number
  courseId: number
  courseName: string
  easeFactor: number
  intervalDays: number
  repetitions: number
  nextReviewDate: string
  lastReviewDate: string
  lastQuality: number
  totalReviews: number
  overdue: boolean
  overdueDays: number
  statusLabel: string
}

/** 复习统计概览 */
export interface ReviewStatsVO {
  totalCards: number
  dueToday: number
  overdue: number
  reviewedToday: number
  newCards: number
  learningCards: number
  masteredCards: number
  difficultCards: number
  streakDays: number
  avgEaseFactor: number
}

/** 复习答题提交请求 */
export interface ReviewSubmitRequest {
  questionId: number
  userAnswer: string
  answerTime?: number
  selfAssessedQuality?: number
}

/** 获取复习统计概览 */
export function getReviewStats(options?: ReviewRequestOptions) {
  return options ? request.get<ReviewStatsVO>('/review/stats', options) : request.get<ReviewStatsVO>('/review/stats')
}

/** 获取今日待复习题目 */
export function getDueReviewCards(
  courseId?: number,
  limit?: number,
  questionId?: number,
  knowledgePointId?: number,
  options?: ReviewRequestOptions,
) {
  return request.get<ReviewScheduleVO[]>('/review/due', {
    params: { courseId, questionId, knowledgePointId, limit },
    ...(options || {}),
  })
}

/** 获取所有复习计划卡片 */
export function getAllReviewCards(courseId?: number, options?: ReviewRequestOptions) {
  return request.get<ReviewScheduleVO[]>('/review/cards', {
    params: { courseId },
    ...(options || {}),
  })
}

/** 将题目加入复习计划 */
export function addToReviewPlan(questionId: number) {
  return request.post<void>(`/review/add/${questionId}`)
}

/** 提交复习答案 */
export function submitReview(data: ReviewSubmitRequest, options?: ReviewRequestOptions) {
  return options
    ? request.post<ReviewScheduleVO>('/review/submit', data, options)
    : request.post<ReviewScheduleVO>('/review/submit', data)
}

/** 移出复习计划 */
export function removeFromReviewPlan(questionId: number, options?: ReviewRequestOptions) {
  return options
    ? request.delete<void>(`/review/remove/${questionId}`, options)
    : request.delete<void>(`/review/remove/${questionId}`)
}

/** 重置复习进度 */
export function resetReviewProgress(questionId: number, options?: ReviewRequestOptions) {
  return options
    ? request.post<void>(`/review/reset/${questionId}`, undefined, options)
    : request.post<void>(`/review/reset/${questionId}`)
}

/** 同步错题本到复习计划（未掌握/部分掌握的错题自动加入） */
export function syncWrongQuestionsToReview(options?: ReviewRequestOptions) {
  return options
    ? request.post<{ syncedCount: number }>('/review/sync-wrong-questions', undefined, options)
    : request.post<{ syncedCount: number }>('/review/sync-wrong-questions')
}

/** AI 复习建议（同步） */
export function getAiReviewSuggestion() {
  return request.post<{ content: string; source: string }>('/review/ai-suggestion')
}

/** AI 复习建议（流式 SSE） — 返回 fetch Response，调用方自行读取 SSE 流 */
export async function getAiReviewSuggestionStream(token: string, signal?: AbortSignal): Promise<Response> {
  const base = import.meta.env.VITE_API_BASE_URL || '/api'
  return fetch(`${base}/review/ai-suggestion/stream`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'text/event-stream',
    },
    ...(signal ? { signal } : {}),
  })
}
