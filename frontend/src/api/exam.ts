import request from '@/utils/request'
import type { ApiResponse, PageData } from '@/types/api'

// ======================== 类型定义 ========================

export interface ExamPaperVO {
  id: number
  title: string
  description: string
  courseId: number
  courseName: string
  totalScore: number
  duration: number
  questionCount: number
  status: number
  createBy: number
  createTime: string
  questions: ExamQuestionItem[]
}

export interface ExamQuestionItem {
  questionId: number
  sortOrder: number
  score: number
  content: string
  questionType: string
  options: { id: number; content: string; optionLabel: string; sortOrder: number }[]
}

export interface ExamPaperCreateRequest {
  title: string
  description?: string
  courseId?: number
  duration?: number
  status?: number
  questions?: { questionId: number; sortOrder?: number; score?: number }[]
}

export interface ExamRecordVO {
  id: number
  examPaperId: number
  examTitle: string
  startTime: string
  endTime: string
  score: number
  totalScore: number
  status: number
  duration: number
  answers: ExamAnswerVO[]
}

export interface ExamAnswerVO {
  questionId: number
  content: string
  questionType: string
  sortOrder: number
  fullScore: number
  userAnswer: string
  isCorrect: number
  score: number
  correctAnswer: string
  analysis: string
}

export interface ExamSubmitRequest {
  examRecordId: number
  answers: { questionId: number; userAnswer: string }[]
}

export interface SmartExamRequest {
  courseId?: number
  questionCount?: number
  difficultyMode?: 'EASY' | 'BALANCED' | 'HARD' | 'ADAPTIVE'
  includeWrongQuestions?: boolean
  title?: string
  duration?: number
}

export interface SmartExamPreview {
  title: string
  description: string
  courseId: number
  courseName: string
  questionCount: number
  totalScore: number
  duration: number
  knowledgePointDistribution: Record<string, number>
  difficultyDistribution: Record<string, number>
  questionIds: number[]
  recommendation: string
}

// ======================== 管理端 API ========================

export function getExamPaperList(params?: { pageNum?: number; pageSize?: number; courseId?: number; status?: number }) {
  return request.get<unknown, ApiResponse<PageData<ExamPaperVO>>>('/admin/exam-papers', { params })
}

export function getExamPaperDetail(id: number) {
  return request.get<unknown, ApiResponse<ExamPaperVO>>(`/admin/exam-papers/${id}`)
}

export function createExamPaper(data: ExamPaperCreateRequest) {
  return request.post<unknown, ApiResponse<ExamPaperVO>>('/admin/exam-papers', data)
}

export function updateExamPaper(id: number, data: ExamPaperCreateRequest) {
  return request.put<unknown, ApiResponse<ExamPaperVO>>(`/admin/exam-papers/${id}`, data)
}

export function deleteExamPaper(id: number) {
  return request.delete<unknown, ApiResponse<null>>(`/admin/exam-papers/${id}`)
}

export function publishExamPaper(id: number) {
  return request.post<unknown, ApiResponse<null>>(`/admin/exam-papers/${id}/publish`)
}

export function smartExamPreview(data: SmartExamRequest) {
  return request.post<unknown, ApiResponse<SmartExamPreview>>('/admin/exam-papers/smart-preview', data)
}

export function smartExamCreate(data: SmartExamPreview) {
  return request.post<unknown, ApiResponse<ExamPaperVO>>('/admin/exam-papers/smart-create', data)
}

// ======================== 用户端 API ========================

export function getPublishedPapers(params?: { pageNum?: number; pageSize?: number; courseId?: number }) {
  return request.get<unknown, ApiResponse<PageData<ExamPaperVO>>>('/exam/papers', { params })
}

export function getPaperDetail(id: number) {
  return request.get<unknown, ApiResponse<ExamPaperVO>>(`/exam/papers/${id}`)
}

export function startExam(paperId: number) {
  return request.post<unknown, ApiResponse<ExamRecordVO>>(`/exam/start/${paperId}`)
}

export function submitExam(data: ExamSubmitRequest) {
  return request.post<unknown, ApiResponse<ExamRecordVO>>('/exam/submit', data)
}

export function getExamResult(recordId: number) {
  return request.get<unknown, ApiResponse<ExamRecordVO>>(`/exam/result/${recordId}`)
}

export function getMyExamRecords(params?: { pageNum?: number; pageSize?: number }) {
  return request.get<unknown, ApiResponse<PageData<ExamRecordVO>>>('/exam/records', { params })
}
