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
  ownerUserId: number | null
  visibility: 'PUBLIC' | 'PRIVATE'
  paperType: PaperType
  examName: string | null
  examYear: number | null
  sourceReference: string | null
  sourceVerified: boolean
  importStatus: 'CONFIRMED' | null
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

export type PaperType = 'PRACTICE' | 'OFFICIAL_EXAM' | 'USER_PRIVATE'
export type ExamStatus = 0 | 1 | 2 | 3

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
  isCorrect: number | null
  score: number | null
  correctAnswer: string | null
  analysis: string | null
  gradingStatus: 'AUTO_GRADED' | 'PENDING' | 'REVIEWED'
  reviewComment: string | null
  reviewDetailJson: string | null
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
  correct: boolean | null
  score: number | null
  fullScore: number
  correctAnswer: string
  analysis: string | null
  answeredAt: string
  gradingStatus: 'AUTO_GRADED' | 'SELF_REVIEW'
}

export interface SubjectiveGradingPointVO {
  pointKey: string
  title: string
  description: string
  referenceAnswer: string
  maxScore: number
  sortOrder: number
}

export interface SubjectiveAnswerReviewVO {
  answerId: number
  examRecordId: number
  userId: number
  examTitle: string
  displayNumber: string
  content: string
  userAnswer: string
  fullScore: number
  gradingStatus: 'PENDING' | 'REVIEWED'
  score: number | null
  reviewComment: string | null
  reviewDetailJson: string | null
  submittedAt: string
  gradingPoints: SubjectiveGradingPointVO[]
}

export interface SubjectiveGradingRequest {
  points: { pointKey: string; awardedScore: number; comment?: string }[]
  reviewComment?: string
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

export interface PrivateExamImportRequest {
  title: string
  courseId: number
  duration: number
  sourceName: string
  sourceFormat: 'MARKDOWN' | 'TEXT'
  content: string
}

export interface PrivateExamImportPreview extends PrivateExamImportRequest {
  contentHash: string
  questionCount: number
  totalScore: number
  requiresAnswerReview: boolean
  questions: {
    content: string
    questionType: string
    answer: string | null
    analysis: string | null
    score: number
    answerComplete: boolean
    options: { label: string; content: string; correct: boolean }[]
  }[]
}

export type PrivateExamDraftStatus = 'DRAFT' | 'AI_GENERATED' | 'REVIEWING' | 'READY' | 'CONFIRMED'

export interface PrivateExamDraft {
  id: number
  title: string
  courseId: number
  duration: number
  status: PrivateExamDraftStatus
  confirmedPaperId: number | null
  reviewedQuestionCount: number
  questionCount: number
  createTime: string
  questions: {
    id: number
    sortOrder: number
    content: string
    questionType: 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE'
    score: number
    options: { label: string; content: string }[]
    originalAnswerLabels: string[]
    originalAnalysis: string | null
    aiAnswerLabels: string[]
    aiAnalysis: string | null
    generationStatus: 'NOT_REQUIRED' | 'PENDING' | 'GENERATED'
    finalAnswerLabels: string[]
    finalAnalysis: string | null
    reviewStatus: 'PENDING' | 'REVIEWED'
  }[]
}

export interface PrivateExamSource {
  paperId: number
  sourceName: string
  sourceFormat: 'MARKDOWN' | 'TEXT'
  contentHash: string
  originalContent: string
  createTime: string
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

export function getPendingSubjectiveReviews() {
  return request.get<unknown, ApiResponse<SubjectiveAnswerReviewVO[]>>('/admin/exam-papers/subjective-reviews/pending')
}

export function gradeSubjectiveAnswer(answerId: number, data: SubjectiveGradingRequest) {
  return request.post<unknown, ApiResponse<SubjectiveAnswerReviewVO>>(
    `/admin/exam-papers/subjective-reviews/${answerId}`,
    data,
  )
}

// ======================== 用户端 API ========================

export function getPublishedPapers(params?: { pageNum?: number; pageSize?: number; courseId?: number }) {
  return request.get<unknown, ApiResponse<PageData<ExamPaperVO>>>('/exam/papers', { params })
}

export function getPaperDetail(id: number) {
  return request.get<unknown, ApiResponse<ExamPaperVO>>(`/exam/papers/${id}`)
}

export function previewPrivateExamImport(data: PrivateExamImportRequest) {
  return request.post<unknown, ApiResponse<PrivateExamImportPreview>>('/exam/private-papers/import/preview', data)
}

export function confirmPrivateExamImport(
  data: PrivateExamImportRequest & { expectedContentHash: string; confirmed: true },
) {
  return request.post<unknown, ApiResponse<ExamPaperVO>>('/exam/private-papers/import/confirm', data)
}

export function createPrivateExamDraft(data: PrivateExamImportRequest & { expectedContentHash: string }) {
  return request.post<unknown, ApiResponse<PrivateExamDraft>>('/exam/private-papers/drafts', data)
}

export function getPrivateExamDrafts() {
  return request.get<unknown, ApiResponse<PrivateExamDraft[]>>('/exam/private-papers/drafts')
}

export function getPrivateExamDraft(draftId: number) {
  return request.get<unknown, ApiResponse<PrivateExamDraft>>(`/exam/private-papers/drafts/${draftId}`)
}

export function generatePrivateExamDraftAnswer(draftId: number, questionId: number) {
  return request.post<unknown, ApiResponse<PrivateExamDraft>>(
    `/exam/private-papers/drafts/${draftId}/questions/${questionId}/ai-answer`,
  )
}

export function reviewPrivateExamDraftQuestion(
  draftId: number,
  questionId: number,
  data: { answerLabels: string[]; analysis: string },
) {
  return request.put<unknown, ApiResponse<PrivateExamDraft>>(
    `/exam/private-papers/drafts/${draftId}/questions/${questionId}/review`,
    data,
  )
}

export function confirmPrivateExamDraft(draftId: number) {
  return request.post<unknown, ApiResponse<ExamPaperVO>>(`/exam/private-papers/drafts/${draftId}/confirm`, {
    confirmed: true,
  })
}

export function deletePrivateExamDraft(draftId: number) {
  return request.delete<unknown, ApiResponse<null>>(`/exam/private-papers/drafts/${draftId}`)
}

export function deletePrivateExamPaper(paperId: number) {
  return request.delete<unknown, ApiResponse<null>>(`/exam/private-papers/${paperId}`)
}

export function getPrivateExamSource(paperId: number) {
  return request.get<unknown, ApiResponse<PrivateExamSource>>(`/exam/private-papers/${paperId}/source`)
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
  return request.post<unknown, ApiResponse<ExamLearningSessionVO>>(`/exam/learning-sessions/${sessionId}/complete`)
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
