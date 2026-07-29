import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

/** 评论 VO */
export interface CommentVO {
  id: number
  questionId: number
  userId: number
  nickname: string
  avatar: string | null
  content: string
  parentId: number
  replyToUserId: number | null
  replyToNickname: string | null
  likeCount: number
  likedByMe: boolean
  createTime: string
  replies?: CommentVO[]
}

/** 发表评论请求 */
export interface CommentRequest {
  questionId: number
  content: string
  parentId?: number
  replyToUserId?: number
}

/** 获取题目评论列表 */
export function getComments(questionId: number) {
  return request.get<unknown, ApiResponse<CommentVO[]>>(`/comments/question/${questionId}`)
}

/** 发表评论 */
export function addComment(data: CommentRequest) {
  return request.post<unknown, ApiResponse<CommentVO>>('/comments', data)
}

/** 删除评论 */
export function deleteComment(commentId: number) {
  return request.delete<unknown, ApiResponse<void>>(`/comments/${commentId}`)
}

/** 点赞/取消点赞 */
export function toggleLike(commentId: number) {
  return request.post<unknown, ApiResponse<boolean>>(`/comments/${commentId}/like`)
}

/** 获取题目评论数 */
export function getCommentCount(questionId: number) {
  return request.get<unknown, ApiResponse<number>>(`/comments/count/${questionId}`)
}
