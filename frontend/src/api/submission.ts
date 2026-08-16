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
  status: number // 0-待审核 1-已通过 2-已拒绝 3-已入库
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
  status: number // 1-通过 2-拒绝
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

/** AI 质检单项结果 */
export interface QualityCheckItem {
  status: 'PASS' | 'WARNING' | 'FAIL'
  detail: string
}

/** AI 质检结果 */
export interface SubmissionQualityCheck {
  qualityScore: number
  summary: string
  recommendation: 'APPROVE' | 'REVISE' | 'REJECT'
  formatCheck: QualityCheckItem
  completenessCheck: QualityCheckItem
  answerCheck: QualityCheckItem
  analysisCheck: QualityCheckItem
  knowledgePointCheck: QualityCheckItem
  riskPoints: string[]
  suggestions: string[]
}

/** AI 知识点标注推荐项 */
export interface TaggedKnowledgePoint {
  id: number
  name: string
  courseName: string
  confidence: 'HIGH' | 'MEDIUM' | 'LOW'
  reason: string
}

/** AI 知识点标注结果 */
export interface SubmissionKPTagging {
  recommendations: TaggedKnowledgePoint[]
  analysis: string
  suggestedIds: string
}

/** AI 难度评估影响因素 */
export interface DifficultyFactor {
  name: string
  description: string
  impact: 'INCREASE' | 'DECREASE' | 'NEUTRAL'
}

/** AI 难度评估结果 */
export interface SubmissionDifficultyAssessment {
  suggestedDifficulty: number
  originalDifficulty: number | null
  difficultyMatch: boolean
  confidence: 'HIGH' | 'MEDIUM' | 'LOW'
  reason: string
  cognitiveLevel: string
  factors: DifficultyFactor[]
  summary: string
}

// ========== 用户端 ==========

/** 提交题目投稿 */
export function submitQuestion(data: SubmissionForm) {
  return request.post<unknown, ApiResponse<QuestionSubmissionVO>>('/submission', data)
}

/** 我的投稿列表 */
export function getMySubmissions(params?: { pageNum?: number; pageSize?: number; status?: number }) {
  return request.get<unknown, ApiResponse<PageResult<QuestionSubmissionVO>>>('/submission/my', { params })
}

/** 投稿详情 */
export function getSubmissionDetail(id: number) {
  return request.get<unknown, ApiResponse<QuestionSubmissionVO>>(`/submission/${id}`)
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
  return request.get<unknown, ApiResponse<PageResult<QuestionSubmissionVO>>>('/admin/submission', { params })
}

/** 管理端投稿详情 */
export function getAdminSubmissionDetail(id: number) {
  return request.get<unknown, ApiResponse<QuestionSubmissionVO>>(`/admin/submission/${id}`)
}

/** 审核投稿 */
export function reviewSubmission(id: number, data: ReviewForm) {
  return request.post<unknown, ApiResponse<QuestionSubmissionVO>>(`/admin/submission/${id}/review`, data)
}

/** 投稿入库 */
export function importSubmission(id: number) {
  return request.post<unknown, ApiResponse<QuestionSubmissionVO>>(`/admin/submission/${id}/import`)
}

/** 投稿统计 */
export function getSubmissionStats() {
  return request.get<unknown, ApiResponse<SubmissionStats>>('/admin/submission/stats')
}

/** AI 质检 */
export function qualityCheckSubmission(id: number) {
  return request.post<unknown, ApiResponse<SubmissionQualityCheck>>(`/admin/submission/${id}/quality-check`)
}

/** AI 知识点标注 */
export function kpTaggingSubmission(id: number) {
  return request.post<unknown, ApiResponse<SubmissionKPTagging>>(`/admin/submission/${id}/kp-tagging`)
}

/** 应用知识点标注结果到投稿 */
export function applyKnowledgePoints(id: number, knowledgePointIds: string) {
  return request.post<unknown, ApiResponse<QuestionSubmissionVO>>(`/admin/submission/${id}/apply-kp`, null, {
    params: { knowledgePointIds },
  })
}

/** AI 难度评估 */
export function assessDifficulty(id: number) {
  return request.post<unknown, ApiResponse<SubmissionDifficultyAssessment>>(
    `/admin/submission/${id}/difficulty-assessment`,
  )
}

/** AI 生成审核意见（基于质检结果） */
export function generateReviewComment(id: number) {
  return request.post<unknown, ApiResponse<string>>(`/admin/submission/${id}/generate-review-comment`)
}
