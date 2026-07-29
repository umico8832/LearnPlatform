import request from '@/utils/request'
import type { ApiResponse, PageData } from '@/types/api'

// ======================== 类型定义 ========================

/** 收藏题目 VO */
export interface FavoriteQuestionVO {
  id: number
  questionId: number
  questionContent: string
  questionType: string
  courseId: number
  courseName: string
  difficulty: number
  score: number
  createTime: string
}

// ======================== API 方法 ========================

/** 收藏题目 */
export function addFavorite(questionId: number) {
  return request.post<unknown, ApiResponse<null>>(`/favorites/${questionId}`)
}

/** 取消收藏 */
export function removeFavorite(questionId: number) {
  return request.delete<unknown, ApiResponse<null>>(`/favorites/${questionId}`)
}

/** 检查是否已收藏 */
export function checkFavorite(questionId: number) {
  return request.get<unknown, ApiResponse<{ isFavorite: boolean }>>(`/favorites/${questionId}/status`)
}

/** 获取收藏列表（分页） */
export function getFavorites(params?: { pageNum?: number; pageSize?: number }) {
  return request.get<unknown, ApiResponse<PageData<FavoriteQuestionVO>>>('/favorites', { params })
}

/** 获取收藏题目 ID 列表 */
export function getFavoriteIds() {
  return request.get<unknown, ApiResponse<number[]>>('/favorites/ids')
}
