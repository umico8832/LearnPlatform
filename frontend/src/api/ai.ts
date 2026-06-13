import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export interface AiResponse {
  content: string
  source: string
}

/** AI 生成题目解析 */
export function getExplanation(questionId: number) {
  return request.post<any, ApiResponse<AiResponse>>('/api/ai/explanation', { questionId })
}

/** AI 生成变式题 */
export function getVariant(questionId: number) {
  return request.post<any, ApiResponse<AiResponse>>('/api/ai/variant', { questionId })
}

/** AI 生成复习建议 */
export function getReviewSuggestion(courseId?: number) {
  return request.post<any, ApiResponse<AiResponse>>('/api/ai/review-suggestion', courseId ? { courseId } : {})
}

/** AI 生成知识点总结 */
export function getSummary(knowledgePointId: number) {
  return request.post<any, ApiResponse<AiResponse>>('/api/ai/summary', { knowledgePointId })
}