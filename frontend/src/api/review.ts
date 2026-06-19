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
  return request.get<ReviewStatsVO>('/api/review/stats')
}

/** 获取今日待复习题目 */
export function getDueReviewCards(courseId?: number, limit?: number) {
  return request.get<ReviewScheduleVO[]>('/api/review/due', {
    params: { courseId, limit }
  })
}

/** 获取所有复习计划卡片 */
export function getAllReviewCards(courseId?: number) {
  return request.get<ReviewScheduleVO[]>('/api/review/cards', {
    params: { courseId }
  })
}

/** 将题目加入复习计划 */
export function addToReviewPlan(questionId: number) {
  return request.post<void>(`/api/review/add/${questionId}`)
}

/** 提交复习答案 */
export function submitReview(data: ReviewSubmitRequest) {
  return request.post<ReviewScheduleVO>('/api/review/submit', data)
}

/** 移出复习计划 */
export function removeFromReviewPlan(questionId: number) {
  return request.delete<void>(`/api/review/remove/${questionId}`)
}

/** 重置复习进度 */
export function resetReviewProgress(questionId: number) {
  return request.post<void>(`/api/review/reset/${questionId}`)
}