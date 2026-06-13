import { aiService } from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export interface AiResponse {
  content: string
  source: string
}

/** AI 生成题目解析 */
export function getExplanation(questionId: number) {
  return aiService.post<any, ApiResponse<AiResponse>>('/ai/explanation', { questionId })
}

/** AI 生成变式题 */
export function getVariant(questionId: number) {
  return aiService.post<any, ApiResponse<AiResponse>>('/ai/variant', { questionId })
}

/** AI 生成复习建议 */
export function getReviewSuggestion(courseId?: number) {
  return aiService.post<any, ApiResponse<AiResponse>>('/ai/review-suggestion', courseId ? { courseId } : {})
}

/** AI 生成知识点总结 */
export function getSummary(knowledgePointId: number) {
  return aiService.post<any, ApiResponse<AiResponse>>('/ai/summary', { knowledgePointId })
}
