import request from '@/utils/request'
import type { ApiResponse, PageData } from '@/types/api'
import type { Misconception } from './understanding'

export interface WrongQuestionRequestOptions {
  errorDisplay?: 'inline'
}

// ======================== 类型定义 ========================

/** 错题 VO */
export interface WrongQuestionVO {
  hasUnderstanding?: boolean
  id: number
  questionId: number
  questionContent: string
  questionType: string
  courseId: number
  courseName: string
  difficulty: number
  wrongCount: number
  masteryLevel: number
  lastWrongAnswer: string
  misconception: Misconception | null
  nextReviewDate: string | null
  createTime: string
  updateTime: string
}

/** 错题统计 */
export interface WrongQuestionStatsVO {
  total: number
  dueCount: number
  unmastered: number
  partial: number
  mastered: number
  courseWrongCount: Record<string, number>
}

export interface WrongQuestionScope {
  courseId?: number
  questionId?: number
  knowledgePointId?: number
}

// ======================== API 方法 ========================

/** 获取错题本列表（分页） */
export function getWrongQuestions(
  params: WrongQuestionScope & {
    pageNum?: number
    pageSize?: number
    masteryLevel?: number
    dueOnly?: boolean
    sortBy?: 'updateTime' | 'nextReviewDate'
  },
  options?: WrongQuestionRequestOptions,
) {
  return request.get<unknown, ApiResponse<PageData<WrongQuestionVO>>>('/wrong-questions', {
    params,
    ...(options || {}),
  })
}

/** 获取错题统计 */
export function getWrongQuestionStats(options?: WrongQuestionRequestOptions): Promise<ApiResponse<WrongQuestionStatsVO>>
export function getWrongQuestionStats(
  params: WrongQuestionScope,
  options?: WrongQuestionRequestOptions,
): Promise<ApiResponse<WrongQuestionStatsVO>>
export function getWrongQuestionStats(
  paramsOrOptions?: WrongQuestionScope | WrongQuestionRequestOptions,
  options?: WrongQuestionRequestOptions,
) {
  const config =
    paramsOrOptions && 'errorDisplay' in paramsOrOptions
      ? paramsOrOptions
      : paramsOrOptions
        ? { params: paramsOrOptions, ...(options || {}) }
        : options
  return config
    ? request.get<unknown, ApiResponse<WrongQuestionStatsVO>>('/wrong-questions/stats', config)
    : request.get<unknown, ApiResponse<WrongQuestionStatsVO>>('/wrong-questions/stats')
}

/** 更新掌握程度 */
export function updateMasteryLevel(id: number, masteryLevel: number, options?: WrongQuestionRequestOptions) {
  return request.put<unknown, ApiResponse<null>>(`/wrong-questions/${id}/mastery`, null, {
    params: { masteryLevel },
    ...(options || {}),
  })
}

/** 移出错题本 */
export function removeWrongQuestion(id: number, options?: WrongQuestionRequestOptions) {
  return options
    ? request.delete<unknown, ApiResponse<null>>(`/wrong-questions/${id}`, options)
    : request.delete<unknown, ApiResponse<null>>(`/wrong-questions/${id}`)
}
