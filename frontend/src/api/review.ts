import request from '@/utils/request'

/** 复习计划卡片 */
export interface ReviewScheduleVO {
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
export function getReviewStats() {
  return request.get<ReviewStatsVO>('/review/stats')
}

/** 获取今日待复习题目 */
export function getDueReviewCards(courseId?: number, limit?: number) {
  return request.get<ReviewScheduleVO[]>('/review/due', {
    params: { courseId, limit }
  })
}

/** 获取所有复习计划卡片 */
export function getAllReviewCards(courseId?: number) {
  return request.get<ReviewScheduleVO[]>('/review/cards', {
    params: { courseId }
  })
}

/** 将题目加入复习计划 */
export function addToReviewPlan(questionId: number) {
  return request.post<void>(`/review/add/${questionId}`)
}

/** 提交复习答案 */
export function submitReview(data: ReviewSubmitRequest) {
  return request.post<ReviewScheduleVO>('/review/submit', data)
}

/** 移出复习计划 */
export function removeFromReviewPlan(questionId: number) {
  return request.delete<void>(`/review/remove/${questionId}`)
}

/** 重置复习进度 */
export function resetReviewProgress(questionId: number) {
  return request.post<void>(`/review/reset/${questionId}`)
}

/** 同步错题本到复习计划（未掌握/部分掌握的错题自动加入） */
export function syncWrongQuestionsToReview() {
  return request.post<{ syncedCount: number }>('/review/sync-wrong-questions')
}

/** AI 复习建议（同步） */
export function getAiReviewSuggestion() {
  return request.post<{ content: string; source: string }>('/review/ai-suggestion')
}

/** AI 复习建议（流式 SSE） — 返回 fetch Response，调用方自行读取 SSE 流 */
export async function getAiReviewSuggestionStream(token: string): Promise<Response> {
  const base = import.meta.env.VITE_API_BASE_URL || '/api'
  return fetch(`${base}/review/ai-suggestion/stream`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Accept': 'text/event-stream',
    },
  })
}
