import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

/** 题目投稿 VO */
export interface QuestionSubmissionVO {
  id: number
  userId: number
  username: string
  nickname: string
  content: string
  questionType: string
  courseId: number
  courseName: string
  difficulty: number
  analysis: string | null
  optionsJson: string | null
  correctAnswer: string | null
  knowledgePointIds: string | null
  tags: string | null
  source: string | null
  status: number  // 0-待审核 1-已通过 2-已拒绝 3-已入库
  reviewComment: string | null
  reviewedBy: number | null
  reviewedByName: string | null
  reviewedTime: string | null
  importedQuestionId: number | null
  createTime: string
  updateTime: string
}

/** 投稿表单 */
export interface SubmissionForm {
  content: string
  questionType: string
  courseId: number
  difficulty: number
  analysis?: string
  optionsJson?: string
  correctAnswer?: string
  knowledgePointIds?: string
  tags?: string
  source?: string
}

/** 审核请求 */
export interface ReviewForm {
  status: number  // 1-通过 2-拒绝
  reviewComment?: string
}

/** 分页结果 */
interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

/** 投稿统计 */
export interface SubmissionStats {
  pending: number
  approved: number
  rejected: number
  imported: number
}

// ========== 用户端 ==========

/** 提交题目投稿 */
export function submitQuestion(data: SubmissionForm) {
  return request.post<ApiResponse<QuestionSubmissionVO>>('/submission', data)
}

/** 我的投稿列表 */
export function getMySubmissions(params?: {
  pageNum?: number
  pageSize?: number
  status?: number
}) {
  return request.get<ApiResponse<PageResult<QuestionSubmissionVO>>>('/submission/my', { params })
}

/** 投稿详情 */
export function getSubmissionDetail(id: number) {
  return request.get<ApiResponse<QuestionSubmissionVO>>(`/submission/${id}`)
}

// ========== 管理端 ==========

/** 管理端投稿列表 */
export function getAdminSubmissions(params?: {
  pageNum?: number
  pageSize?: number
  status?: number
  courseId?: number
  keyword?: string
}) {
  return request.get<ApiResponse<PageResult<QuestionSubmissionVO>>>('/admin/submission', { params })
}

/** 管理端投稿详情 */
export function getAdminSubmissionDetail(id: number) {
  return request.get<ApiResponse<QuestionSubmissionVO>>(`/admin/submission/${id}`)
}

/** 审核投稿 */
export function reviewSubmission(id: number, data: ReviewForm) {
  return request.post<ApiResponse<QuestionSubmissionVO>>(`/admin/submission/${id}/review`, data)
}

/** 投稿入库 */
export function importSubmission(id: number) {
  return request.post<ApiResponse<QuestionSubmissionVO>>(`/admin/submission/${id}/import`)
}

/** 投稿统计 */
export function getSubmissionStats() {
  return request.get<ApiResponse<SubmissionStats>>('/admin/submission/stats')
}