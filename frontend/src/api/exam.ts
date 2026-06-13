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

// ======================== 管理端 API ========================

export function getExamPaperList(params?: { pageNum?: number; pageSize?: number; courseId?: number; status?: number }) {
  return request.get<any, ApiResponse<PageData<ExamPaperVO>>>('/api/admin/exam-papers', { params })
}

export function getExamPaperDetail(id: number) {
  return request.get<any, ApiResponse<ExamPaperVO>>(`/api/admin/exam-papers/${id}`)
}

export function createExamPaper(data: ExamPaperCreateRequest) {
  return request.post<any, ApiResponse<ExamPaperVO>>('/api/admin/exam-papers', data)
}

export function updateExamPaper(id: number, data: ExamPaperCreateRequest) {
  return request.put<any, ApiResponse<ExamPaperVO>>(`/api/admin/exam-papers/${id}`, data)
}

export function deleteExamPaper(id: number) {
  return request.delete<any, ApiResponse<null>>(`/api/admin/exam-papers/${id}`)
}

export function publishExamPaper(id: number) {
  return request.post<any, ApiResponse<null>>(`/api/admin/exam-papers/${id}/publish`)
}

// ======================== 用户端 API ========================

export function getPublishedPapers(params?: { pageNum?: number; pageSize?: number; courseId?: number }) {
  return request.get<any, ApiResponse<PageData<ExamPaperVO>>>('/api/exam/papers', { params })
}

export function getPaperDetail(id: number) {
  return request.get<any, ApiResponse<ExamPaperVO>>(`/api/exam/papers/${id}`)
}

export function startExam(paperId: number) {
  return request.post<any, ApiResponse<ExamRecordVO>>(`/api/exam/start/${paperId}`)
}

export function submitExam(data: ExamSubmitRequest) {
  return request.post<any, ApiResponse<ExamRecordVO>>('/api/exam/submit', data)
}

export function getExamResult(recordId: number) {
  return request.get<any, ApiResponse<ExamRecordVO>>(`/api/exam/result/${recordId}`)
}

export function getMyExamRecords(params?: { pageNum?: number; pageSize?: number }) {
  return request.get<any, ApiResponse<PageData<ExamRecordVO>>>('/api/exam/records', { params })
}