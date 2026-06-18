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
export function getAdminQuestionPage(params: {
  pageNum?: number
  pageSize?: number
  keyword?: string
  questionType?: string
  courseId?: number
  difficulty?: number
  status?: number
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

/** 导入题目（管理端） */
export function importQuestions(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<any, ApiResponse<QuestionImportResult>>('/admin/questions/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
