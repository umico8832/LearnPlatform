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
  paperType: PaperType
  examName: string | null
  examYear: number | null
  sourceReference: string | null
  sourceVerified: boolean
  createTime: string
  questions: ExamQuestionItem[]
}

export interface ExamQuestionItem {
  questionId: number
  sortOrder: number
  score: number
  content: string
  questionType: string
  sectionTitle: string | null
  majorQuestionNumber: string | null
  minorQuestionNumber: string | null
  subquestionNumber: string | null
  displayNumber: string | null
  options: { id: number; content: string; optionLabel: string; sortOrder: number }[]
}

export type PaperType = 'PRACTICE' | 'OFFICIAL_EXAM'
export type ExamStatus = 0 | 1 | 2

export interface ExamPaperCreateRequest {
  title: string
  description?: string
  courseId?: number
  duration?: number
  status?: number
  paperType?: PaperType
  examName?: string
  examYear?: number
  sourceReference?: string
  sourceVerified?: boolean
  questions?: {
    questionId: number
    sortOrder?: number
    score?: number
    sectionTitle?: string
    majorQuestionNumber?: string
    minorQuestionNumber?: string
    subquestionNumber?: string
    displayNumber?: string
  }[]
}

export interface ExamRecordVO {
  id: number
  examPaperId: number
  examTitle: string
  courseId: number | null
  paperType: PaperType
  examName: string | null
  examYear: number | null
  sourceReference: string | null
  sourceVerified: boolean | null
  startTime: string
  deadline: string | null
  serverTime: string | null
  endTime: string | null
  score: number | null
  totalScore: number
  status: ExamStatus
  duration: number
  answers: ExamAnswerVO[] | null
}

export interface ExamAnswerVO {
  questionId: number
  content: string
  questionType: string
  sortOrder: number
  fullScore: number
  sectionTitle: string | null
  majorQuestionNumber: string | null
  minorQuestionNumber: string | null
  subquestionNumber: string | null
  displayNumber: string | null
  userAnswer: string
  isCorrect: number
  score: number
  correctAnswer: string
  analysis: string | null
}

export interface ExamSubmitRequest {
  examRecordId: number
  answers: { questionId: number; userAnswer: string }[]
}

export interface ExamLearningAnswerResultVO {
  answerId: number
  questionId: number
  attemptNo: number
  userAnswer: string
  correct: boolean
  score: number
  fullScore: number
  correctAnswer: string
  analysis: string | null
  answeredAt: string
}

export interface ExamLearningQuestionItem {
  questionId: number
  sortOrder: number
  score: number
  content: string
  questionType: string
  sectionTitle: string | null
  majorQuestionNumber: string | null
  minorQuestionNumber: string | null
  subquestionNumber: string | null
  displayNumber: string | null
  options: { id: number; content: string; optionLabel: string; sortOrder: number }[]
  latestAnswer: ExamLearningAnswerResultVO | null
}

export interface ExamLearningSessionVO {
  id: number
  examPaperId: number
  paperTitle: string
  courseId: number
  paperType: PaperType
  examName: string | null
  examYear: number | null
  sourceReference: string | null
  sourceVerified: boolean
  status: number
  currentQuestionId: number
  answeredQuestionCount: number
  correctQuestionCount: number
  startTime: string
  completeTime: string | null
  questions: ExamLearningQuestionItem[]
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

export function getExamSession(recordId: number) {
  return request.get<unknown, ApiResponse<ExamRecordVO>>(`/exam/records/${recordId}/session`)
}

export function startExamLearningSession(paperId: number) {
  return request.post<unknown, ApiResponse<ExamLearningSessionVO>>(`/exam/papers/${paperId}/learning-sessions`)
}

export function getExamLearningSession(sessionId: number) {
  return request.get<unknown, ApiResponse<ExamLearningSessionVO>>(`/exam/learning-sessions/${sessionId}`)
}

export function submitExamLearningAnswer(
  sessionId: number,
  data: { questionId: number; userAnswer: string; answerTime?: number },
) {
  return request.post<unknown, ApiResponse<ExamLearningAnswerResultVO>>(
    `/exam/learning-sessions/${sessionId}/answers`,
    data,
  )
}

export function completeExamLearningSession(sessionId: number) {
  return request.post<unknown, ApiResponse<ExamLearningSessionVO>>(
    `/exam/learning-sessions/${sessionId}/complete`,
  )
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
