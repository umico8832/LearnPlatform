import type { ExamPaperCreateRequest, ExamPaperVO, PaperType } from '@/api/exam'

export interface ExamPaperFormQuestion {
  questionId: number
  sortOrder: number
  score: number
  sectionTitle: string
  majorQuestionNumber: string
  minorQuestionNumber: string
  subquestionNumber: string
  displayNumber: string
}

export interface ExamPaperForm {
  title: string
  description: string
  courseId: number | undefined
  duration: number
  status: number
  paperType: PaperType
  examName: string
  examYear: number | undefined
  sourceReference: string
  sourceVerified: boolean
  questions: ExamPaperFormQuestion[]
}

export function createEmptyExamPaperForm(): ExamPaperForm {
  return {
    title: '',
    description: '',
    courseId: undefined,
    duration: 60,
    status: 0,
    paperType: 'PRACTICE',
    examName: '',
    examYear: undefined,
    sourceReference: '',
    sourceVerified: false,
    questions: [],
  }
}

export function createExamPaperForm(detail: ExamPaperVO): ExamPaperForm {
  return {
    title: detail.title,
    description: detail.description || '',
    courseId: detail.courseId || undefined,
    duration: detail.duration || 60,
    status: detail.status || 0,
    paperType: detail.paperType || 'PRACTICE',
    examName: detail.examName || '',
    examYear: detail.examYear || undefined,
    sourceReference: detail.sourceReference || '',
    sourceVerified: detail.sourceVerified || false,
    questions: (detail.questions || []).map((question, index) => ({
      questionId: question.questionId,
      sortOrder: question.sortOrder ?? index,
      score: question.score || 1,
      sectionTitle: question.sectionTitle || '',
      majorQuestionNumber: question.majorQuestionNumber || '',
      minorQuestionNumber: question.minorQuestionNumber || '',
      subquestionNumber: question.subquestionNumber || '',
      displayNumber: question.displayNumber || '',
    })),
  }
}

export function validateExamPaperForm(form: ExamPaperForm): string | null {
  if (!form.title.trim()) return '请输入试卷名称'
  if (form.paperType !== 'OFFICIAL_EXAM' || form.status !== 1) return null
  if (!form.examName.trim() || !form.examYear || !form.sourceReference.trim()) {
    return '发布官方试卷前请填写考试名称、年份和来源'
  }
  if (!form.sourceVerified) return '发布官方试卷前必须完成人工来源核验'
  if (form.questions.some((question) => !question.displayNumber.trim())) {
    return '官方试卷每道题都必须填写展示题号'
  }
  return null
}

export function createExamPaperRequest(form: ExamPaperForm): ExamPaperCreateRequest {
  return {
    title: form.title,
    description: form.description || undefined,
    courseId: form.courseId,
    duration: form.duration,
    status: form.status,
    paperType: form.paperType,
    examName: form.examName || undefined,
    examYear: form.examYear,
    sourceReference: form.sourceReference || undefined,
    sourceVerified: form.sourceVerified,
    questions: form.questions.map((question, index) => ({
      questionId: question.questionId,
      sortOrder: question.sortOrder ?? index,
      score: question.score,
      sectionTitle: question.sectionTitle || undefined,
      majorQuestionNumber: question.majorQuestionNumber || undefined,
      minorQuestionNumber: question.minorQuestionNumber || undefined,
      subquestionNumber: question.subquestionNumber || undefined,
      displayNumber: question.displayNumber || undefined,
    })),
  }
}
