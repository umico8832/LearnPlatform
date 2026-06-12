import request from '@/utils/request'
import type { ApiResponse, PageData } from '@/types/api'

// ======================== 类型定义 ========================

/** 错题 VO */
export interface WrongQuestionVO {
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
  createTime: string
  updateTime: string
}

/** 错题统计 */
export interface WrongQuestionStatsVO {
  total: number
  unmastered: number
  partial: number
  mastered: number
  courseWrongCount: Record<string, number>
}

// ======================== API 方法 ========================

/** 获取错题本列表（分页） */
export function getWrongQuestions(params: {
  pageNum?: number
  pageSize?: number
  courseId?: number
  masteryLevel?: number
}) {
  return request.get<any, ApiResponse<PageData<WrongQuestionVO>>>('/api/wrong-questions', { params })
}

/** 获取错题统计 */
export function getWrongQuestionStats() {
  return request.get<any, ApiResponse<WrongQuestionStatsVO>>('/api/wrong-questions/stats')
}

/** 更新掌握程度 */
export function updateMasteryLevel(id: number, masteryLevel: number) {
  return request.put<any, ApiResponse<null>>(`/api/wrong-questions/${id}/mastery`, null, {
    params: { masteryLevel }
  })
}

/** 移出错题本 */
export function removeWrongQuestion(id: number) {
  return request.delete<any, ApiResponse<null>>(`/api/wrong-questions/${id}`)
}