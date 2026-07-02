import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

/** 题目选项 VO */
export interface QuestionOptionVO {
  id: number
  content: string
  optionLabel: string
  isCorrect: number
  sortOrder: number
}

/** 题目 VO */
export interface QuestionVO {
  id: number
  content: string
  questionType: string
  courseId: number
  courseName: string
  difficulty: number
  analysis: string | null
  tags: string | null
  score: number
  status: number
  sourceType?: string
  sourceReference?: string
  lastReviewTime?: string
  nextReviewTime?: string
  reviewRounds?: number
  createTime: string
  updateTime: string
  options: QuestionOptionVO[]
  knowledgePointIds: number[]
  knowledgePointNames: string[]
}

/** 创建/更新题目请求 */
export interface QuestionForm {
  content: string
  questionType: string
  courseId: number
  difficulty?: number
  analysis?: string
  tags?: string
  score?: number
  options?: OptionItem[]
  knowledgePointIds?: number[]
}

/** 选项项 */
export interface OptionItem {
  content: string
  optionLabel: string
  isCorrect?: number
  sortOrder?: number
}

/** 分页结果 */
interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

/** 获取题目分页（用户端） */
export function getQuestionPage(params: {
  pageNum?: number
  pageSize?: number
  questionType?: string
  courseId?: number
  difficulty?: number
}) {
  return request.get<any, ApiResponse<PageResult<QuestionVO>>>('/questions', { params })
}

/** 获取题目详情（用户端） */
export function getQuestionById(id: number) {
  return request.get<any, ApiResponse<QuestionVO>>(`/questions/${id}`)
}

/** 获取题目分页（管理端） */
/** 题目来源统计 */
export interface QuestionSourceStatsVO {
  sourceType: string
  count: number
}

/** 复审记录 VO */
export interface QuestionReviewRecordVO {
  id: number
  questionId: number
  reviewerId: number
  reviewerName: string
  reviewType: string
  action: string
  oldContent: string
  newContent: string
  oldDifficulty: number
  newDifficulty: number
  comment: string
  createTime: string
}

/** AI 复审建议 VO */
export interface QuestionReviewSuggestionVO {
  recommendation: 'APPROVE' | 'REVISE' | 'REJECT'
  confidenceScore: number
  summary: string
  suggestedContent?: string
  suggestedDifficulty?: number
  riskPoints: string[]
  suggestions: string[]
  answerAnalysis?: string
  knowledgeAnalysis?: string
}

/** 疑似重复题目分组 */
export interface QuestionDuplicateGroupVO {
  matchType: 'EXACT' | 'SIMILAR'
  similarityScore: number
  representativeContent: string
  questions: QuestionVO[]
}

/** 题目纠错反馈 */
export interface QuestionCorrectionReportVO {
  id: number
  questionId: number
  questionContent?: string
  reporterId: number
  reporterName?: string
  reportType: 'CONTENT' | 'ANSWER' | 'ANALYSIS' | 'KNOWLEDGE_POINT' | 'OTHER'
  description: string
  status: 'OPEN' | 'RESOLVED' | 'REJECTED'
  handlerId?: number
  handlerName?: string
  handlerComment?: string
  handledTime?: string
  createTime: string
  updateTime: string
}

export function getAdminQuestionPage(params: {
  pageNum?: number
  pageSize?: number
  keyword?: string
  questionType?: string
  courseId?: number
  difficulty?: number
  status?: number
  sourceType?: string
}) {
  return request.get<any, ApiResponse<PageResult<QuestionVO>>>('/admin/questions', { params })
}

/** 获取题目详情（管理端） */
export function getAdminQuestionById(id: number) {
  return request.get<any, ApiResponse<QuestionVO>>(`/admin/questions/${id}`)
}

/** 创建题目（管理端） */
export function createQuestion(data: QuestionForm) {
  return request.post<any, ApiResponse<QuestionVO>>('/admin/questions', data)
}

/** 更新题目（管理端） */
export function updateQuestion(id: number, data: QuestionForm) {
  return request.put<any, ApiResponse<QuestionVO>>(`/admin/questions/${id}`, data)
}

/** 删除题目（管理端） */
export function deleteQuestion(id: number) {
  return request.delete<any, ApiResponse<void>>(`/admin/questions/${id}`)
}

/** 检测疑似重复题目 */
export function detectDuplicateQuestions(params?: {
  courseId?: number
  questionType?: string
  minSimilarity?: number
  limit?: number
}) {
  return request.get<any, ApiResponse<QuestionDuplicateGroupVO[]>>('/admin/questions/duplicates', { params })
}

/** 提交题目纠错反馈 */
export function submitQuestionCorrectionReport(questionId: number, data: {
  reportType: string
  description: string
}) {
  return request.post<any, ApiResponse<QuestionCorrectionReportVO>>(`/questions/${questionId}/correction-reports`, data)
}

/** 我的题目纠错反馈 */
export function getMyQuestionCorrectionReports(params?: {
  pageNum?: number
  pageSize?: number
  status?: string
}) {
  return request.get<any, ApiResponse<PageResult<QuestionCorrectionReportVO>>>('/questions/correction-reports/my', { params })
}

/** 管理端题目纠错反馈列表 */
export function getAdminQuestionCorrectionReports(params?: {
  pageNum?: number
  pageSize?: number
  status?: string
  questionId?: number
}) {
  return request.get<any, ApiResponse<PageResult<QuestionCorrectionReportVO>>>('/admin/questions/correction-reports', { params })
}

/** 管理端处理题目纠错反馈 */
export function processQuestionCorrectionReport(reportId: number, data: {
  status: string
  handlerComment: string
}) {
  return request.post<any, ApiResponse<QuestionCorrectionReportVO>>(`/admin/questions/correction-reports/${reportId}/process`, data)
}

/** 题目导入结果 */
export interface QuestionImportResult {
  totalRows: number
  successCount: number
  failCount: number
  errors: string[]
}

/** 导出题目（管理端）- 返回 Blob 下载 */
export function exportQuestions(params?: {
  questionType?: string
  courseId?: number
  difficulty?: number
}) {
  return request.get('/admin/questions/export', {
    params,
    responseType: 'blob'
  })
}

/** 下载导入模板 */
export function downloadTemplate() {
  return request.get('/admin/questions/template', {
    responseType: 'blob'
  })
}

/** 导入题目（管理端 - Excel） */
export function importQuestions(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<any, ApiResponse<QuestionImportResult>>('/admin/questions/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 导入题目（管理端 - Markdown） */
export function importQuestionsMarkdown(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<any, ApiResponse<QuestionImportResult>>('/admin/questions/import-markdown', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 下载 Markdown 导入模板 */
export function downloadMarkdownTemplate() {
  return request.get('/admin/questions/template-markdown', {
    responseType: 'blob'
  })
}

/** 获取题目来源统计 */
export function getSourceStats() {
  return request.get<any, ApiResponse<QuestionSourceStatsVO[]>>('/admin/questions/source-stats')
}

/** 获取待复审题目列表 */
export function getReviewOverdue(params?: { pageNum?: number; pageSize?: number }) {
  return request.get<any, ApiResponse<PageResult<QuestionVO>>>('/admin/questions/review-overdue', { params })
}

/** 获取题目的复审记录 */
export function getReviewRecords(questionId: number) {
  return request.get<any, ApiResponse<QuestionReviewRecordVO[]>>(`/admin/questions/${questionId}/review-records`)
}

/** 获取 AI 复审建议 */
export function getReviewSuggestion(questionId: number) {
  return request.get<any, ApiResponse<QuestionReviewSuggestionVO>>(`/admin/questions/${questionId}/review-suggestion`)
}

/** 执行复审 */
export function performReReview(questionId: number, data: {
  action: string
  newContent?: string
  newDifficulty?: number
  comment: string
}) {
  return request.post<any, ApiResponse<QuestionReviewRecordVO>>(`/admin/questions/${questionId}/re-review`, data)
}
