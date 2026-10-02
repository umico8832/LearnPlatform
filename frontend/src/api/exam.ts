import request from '@/utils/request'
import type { ApiResponse, PageData } from '@/types/api'
import type { RewardFeedback } from '@/api/gamification'

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
export type ExamRequestOptions = { errorDisplay?: 'inline' }

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
  submissionReward?: RewardFeedback | null
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
  reward?: RewardFeedback | null
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
  sourceFormat: 'MARKDOWN' | 'TEXT' | 'PDF' | 'DOCX'
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
  sourceName: string | null
  sourceFormat: 'MARKDOWN' | 'TEXT' | 'PDF' | 'DOCX' | null
  originalFileAvailable: boolean
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
  sourceFormat: 'MARKDOWN' | 'TEXT' | 'PDF' | 'DOCX'
  contentHash: string
  originalContent: string
  originalFileAvailable: boolean
  createTime: string
}

export interface PrivateExamStorageUsage {
  usedBytes: number
  limitBytes: number
  remainingBytes: number
  fileCount: number
}

export interface PrivateExamSourceStorageItem {
  id: number
  sourceName: string
  sourceFormat: 'PDF' | 'DOCX'
  sourceMediaType: string
  sourceSize: number
  createTime: string
  associationType: 'DRAFT' | 'PAPER' | 'UNREFERENCED'
  associationId: number | null
  associationTitle: string | null
  associationStatus: string
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

export function getPublishedPapers(
  params?: { pageNum?: number; pageSize?: number; courseId?: number; paperType?: PaperType; keyword?: string },
  options?: ExamRequestOptions,
) {
  return request.get<unknown, ApiResponse<PageData<ExamPaperVO>>>(
    '/exam/papers',
    options ? { params, ...options } : { params },
  )
}

export function getPaperDetail(id: number, options?: { errorDisplay?: 'inline' }) {
  if (options) return request.get<unknown, ApiResponse<ExamPaperVO>>(`/exam/papers/${id}`, options)
  return request.get<unknown, ApiResponse<ExamPaperVO>>(`/exam/papers/${id}`)
}

function postPrivateImport<T>(url: string, data: unknown, options?: ExamRequestOptions) {
  return options
    ? request.post<unknown, ApiResponse<T>>(url, data, options)
    : request.post<unknown, ApiResponse<T>>(url, data)
}

export function previewPrivateExamImport(data: PrivateExamImportRequest, options?: ExamRequestOptions) {
  return postPrivateImport<PrivateExamImportPreview>('/exam/private-papers/import/preview', data, options)
}

export interface PrivateExamFileMetadata {
  title: string
  courseId: number
  duration: number
}

function fileFormData(metadata: object, file: File) {
  const data = new FormData()
  data.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' }))
  data.append('file', file)
  return data
}

export function previewPrivateExamPdf(metadata: PrivateExamFileMetadata, file: File, options?: ExamRequestOptions) {
  return request.post<unknown, ApiResponse<PrivateExamImportPreview>>(
    '/exam/private-papers/import/pdf/preview',
    fileFormData(metadata, file),
    { headers: { 'Content-Type': 'multipart/form-data' }, ...options },
  )
}

export function confirmPrivateExamPdf(
  metadata: PrivateExamFileMetadata & { expectedContentHash: string; confirmed: true },
  file: File,
  options?: ExamRequestOptions,
) {
  return request.post<unknown, ApiResponse<ExamPaperVO>>(
    '/exam/private-papers/import/pdf/confirm',
    fileFormData(metadata, file),
    { headers: { 'Content-Type': 'multipart/form-data' }, ...options },
  )
}

export function createPrivateExamPdfDraft(
  metadata: PrivateExamFileMetadata & { expectedContentHash: string },
  file: File,
  options?: ExamRequestOptions,
) {
  return request.post<unknown, ApiResponse<PrivateExamDraft>>(
    '/exam/private-papers/drafts/pdf',
    fileFormData(metadata, file),
    { headers: { 'Content-Type': 'multipart/form-data' }, ...options },
  )
}

export function previewPrivateExamDocx(metadata: PrivateExamFileMetadata, file: File, options?: ExamRequestOptions) {
  return request.post<unknown, ApiResponse<PrivateExamImportPreview>>(
    '/exam/private-papers/import/docx/preview',
    fileFormData(metadata, file),
    { headers: { 'Content-Type': 'multipart/form-data' }, ...options },
  )
}

export function confirmPrivateExamDocx(
  metadata: PrivateExamFileMetadata & { expectedContentHash: string; confirmed: true },
  file: File,
  options?: ExamRequestOptions,
) {
  return request.post<unknown, ApiResponse<ExamPaperVO>>(
    '/exam/private-papers/import/docx/confirm',
    fileFormData(metadata, file),
    { headers: { 'Content-Type': 'multipart/form-data' }, ...options },
  )
}

export function createPrivateExamDocxDraft(
  metadata: PrivateExamFileMetadata & { expectedContentHash: string },
  file: File,
  options?: ExamRequestOptions,
) {
  return request.post<unknown, ApiResponse<PrivateExamDraft>>(
    '/exam/private-papers/drafts/docx',
    fileFormData(metadata, file),
    { headers: { 'Content-Type': 'multipart/form-data' }, ...options },
  )
}

export function confirmPrivateExamImport(
  data: PrivateExamImportRequest & { expectedContentHash: string; confirmed: true },
  options?: ExamRequestOptions,
) {
  return postPrivateImport<ExamPaperVO>('/exam/private-papers/import/confirm', data, options)
}

export function createPrivateExamDraft(
  data: PrivateExamImportRequest & { expectedContentHash: string },
  options?: ExamRequestOptions,
) {
  return postPrivateImport<PrivateExamDraft>('/exam/private-papers/drafts', data, options)
}

export function getPrivateExamDrafts(options?: ExamRequestOptions) {
  return options
    ? request.get<unknown, ApiResponse<PrivateExamDraft[]>>('/exam/private-papers/drafts', options)
    : request.get<unknown, ApiResponse<PrivateExamDraft[]>>('/exam/private-papers/drafts')
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

export function confirmPrivateExamDraft(draftId: number, options?: ExamRequestOptions) {
  return postPrivateImport<ExamPaperVO>(
    `/exam/private-papers/drafts/${draftId}/confirm`,
    {
      confirmed: true,
    },
    options,
  )
}

export function deletePrivateExamDraft(draftId: number, options?: ExamRequestOptions) {
  return options
    ? request.delete<unknown, ApiResponse<null>>(`/exam/private-papers/drafts/${draftId}`, options)
    : request.delete<unknown, ApiResponse<null>>(`/exam/private-papers/drafts/${draftId}`)
}

export function deletePrivateExamPaper(paperId: number, options?: ExamRequestOptions) {
  return options
    ? request.delete<unknown, ApiResponse<null>>(`/exam/private-papers/${paperId}`, options)
    : request.delete<unknown, ApiResponse<null>>(`/exam/private-papers/${paperId}`)
}

export function getPrivateExamSource(paperId: number, options?: ExamRequestOptions) {
  return options
    ? request.get<unknown, ApiResponse<PrivateExamSource>>(`/exam/private-papers/${paperId}/source`, options)
    : request.get<unknown, ApiResponse<PrivateExamSource>>(`/exam/private-papers/${paperId}/source`)
}

export function getPrivateExamStorageUsage(options?: ExamRequestOptions) {
  return options
    ? request.get<unknown, ApiResponse<PrivateExamStorageUsage>>('/exam/private-papers/source-storage', options)
    : request.get<unknown, ApiResponse<PrivateExamStorageUsage>>('/exam/private-papers/source-storage')
}

export function getPrivateExamStorageFiles(
  params?: { pageNum?: number; pageSize?: number },
  options?: ExamRequestOptions,
) {
  return request.get<unknown, ApiResponse<PageData<PrivateExamSourceStorageItem>>>(
    '/exam/private-papers/source-storage/files',
    options ? { params, ...options } : { params },
  )
}

export function downloadPrivateExamSourceFile(paperId: number, options?: ExamRequestOptions) {
  return request.get(
    `/exam/private-papers/${paperId}/source/file`,
    options ? { responseType: 'blob', ...options } : { responseType: 'blob' },
  )
}

export function downloadPrivateExamDraftSourceFile(draftId: number, options?: ExamRequestOptions) {
  return request.get(
    `/exam/private-papers/drafts/${draftId}/source/file`,
    options ? { responseType: 'blob', ...options } : { responseType: 'blob' },
  )
}

export function startExam(paperId: number, options?: ExamRequestOptions) {
  return options
    ? request.post<unknown, ApiResponse<ExamRecordVO>>(`/exam/start/${paperId}`, undefined, options)
    : request.post<unknown, ApiResponse<ExamRecordVO>>(`/exam/start/${paperId}`)
}

export function getExamSession(recordId: number, options?: { errorDisplay?: 'inline' }) {
  if (options) return request.get<unknown, ApiResponse<ExamRecordVO>>(`/exam/records/${recordId}/session`, options)
  return request.get<unknown, ApiResponse<ExamRecordVO>>(`/exam/records/${recordId}/session`)
}

export function startExamLearningSession(paperId: number, options?: ExamRequestOptions) {
  return options
    ? request.post<unknown, ApiResponse<ExamLearningSessionVO>>(
        `/exam/papers/${paperId}/learning-sessions`,
        undefined,
        options,
      )
    : request.post<unknown, ApiResponse<ExamLearningSessionVO>>(`/exam/papers/${paperId}/learning-sessions`)
}

export function getExamLearningSession(sessionId: number, options?: { errorDisplay?: 'inline' }) {
  if (!options) return request.get<unknown, ApiResponse<ExamLearningSessionVO>>(`/exam/learning-sessions/${sessionId}`)
  return request.get<unknown, ApiResponse<ExamLearningSessionVO>>(`/exam/learning-sessions/${sessionId}`, options)
}

export function submitExamLearningAnswer(
  sessionId: number,
  data: { questionId: number; userAnswer: string; answerTime?: number },
  options?: { errorDisplay?: 'inline' },
) {
  if (!options)
    return request.post<unknown, ApiResponse<ExamLearningAnswerResultVO>>(
      `/exam/learning-sessions/${sessionId}/answers`,
      data,
    )
  return request.post<unknown, ApiResponse<ExamLearningAnswerResultVO>>(
    `/exam/learning-sessions/${sessionId}/answers`,
    data,
    options,
  )
}

export function completeExamLearningSession(sessionId: number, options?: { errorDisplay?: 'inline' }) {
  if (!options)
    return request.post<unknown, ApiResponse<ExamLearningSessionVO>>(`/exam/learning-sessions/${sessionId}/complete`)
  return request.post<unknown, ApiResponse<ExamLearningSessionVO>>(
    `/exam/learning-sessions/${sessionId}/complete`,
    undefined,
    options,
  )
}

export function submitExam(data: ExamSubmitRequest, options?: { errorDisplay?: 'inline' }) {
  if (options) return request.post<unknown, ApiResponse<ExamRecordVO>>('/exam/submit', data, options)
  return request.post<unknown, ApiResponse<ExamRecordVO>>('/exam/submit', data)
}

export function getExamResult(recordId: number, options?: { errorDisplay?: 'inline' }) {
  if (!options) return request.get<unknown, ApiResponse<ExamRecordVO>>(`/exam/result/${recordId}`)
  return request.get<unknown, ApiResponse<ExamRecordVO>>(`/exam/result/${recordId}`, options)
}

export function getMyExamRecords(params?: { pageNum?: number; pageSize?: number }, options?: ExamRequestOptions) {
  return request.get<unknown, ApiResponse<PageData<ExamRecordVO>>>(
    '/exam/records',
    options ? { params, ...options } : { params },
  )
}
