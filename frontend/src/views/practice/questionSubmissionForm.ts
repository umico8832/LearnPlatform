import type { SubmissionForm } from '@/api/submission'
import { practiceQuestionTypes } from './practiceLibraryPresentation'

export interface SubmissionDraft {
  courseId: number | undefined
  questionType: string
  difficulty: number
  content: string
  correctAnswer: string
  analysis: string
  tags: string
  source: string
}
export interface SubmissionOption {
  key: number
  content: string
  isCorrect: boolean
}

export function emptySubmissionDraft(): SubmissionDraft {
  return {
    courseId: undefined,
    questionType: '',
    difficulty: 3,
    content: '',
    correctAnswer: '',
    analysis: '',
    tags: '',
    source: '',
  }
}

export function submissionPayload(draft: SubmissionDraft, options: SubmissionOption[]): SubmissionForm {
  if (!draft.courseId || !Number.isSafeInteger(draft.courseId) || draft.courseId < 1)
    throw new Error('请选择所属课程。')
  if (!practiceQuestionTypes.some((item) => item.value === draft.questionType)) throw new Error('请选择题型。')
  if (!draft.content.trim()) throw new Error('请填写题干内容。')
  if (!Number.isInteger(draft.difficulty) || draft.difficulty < 1 || draft.difficulty > 5)
    throw new Error('请选择题目难度。')
  const payload: SubmissionForm = {
    courseId: draft.courseId,
    questionType: draft.questionType,
    difficulty: draft.difficulty,
    content: draft.content.trim(),
    analysis: draft.analysis.trim() || undefined,
    tags: draft.tags.trim() || undefined,
    source: draft.source.trim() || undefined,
  }
  if (draft.questionType === 'SINGLE_CHOICE' || draft.questionType === 'MULTIPLE_CHOICE') {
    const filled = options.filter((option) => option.content.trim())
    if (filled.length < 2) throw new Error('请至少填写两个选项。')
    const correct = filled.filter((option) => option.isCorrect).length
    if (!correct) throw new Error('请标记正确答案。')
    if (draft.questionType === 'SINGLE_CHOICE' && correct !== 1) throw new Error('单选题只能标记一个正确答案。')
    payload.optionsJson = JSON.stringify(
      filled.map((option, index) => ({
        label: String.fromCharCode(65 + index),
        content: option.content.trim(),
        isCorrect: option.isCorrect,
      })),
    )
  } else {
    const answer = draft.correctAnswer.trim()
    if (!answer) throw new Error(draft.questionType === 'TRUE_FALSE' ? '请选择判断题的正确答案。' : '请填写参考答案。')
    if (draft.questionType === 'TRUE_FALSE' && !['TRUE', 'FALSE'].includes(answer))
      throw new Error('请选择有效的判断题答案。')
    payload.correctAnswer = answer
  }
  return payload
}

export function submissionStatus(status: number) {
  return ['待审核', '已通过', '未通过', '已入库'][status] || '状态未知'
}

export function submissionOptions(json: string | null): { label: string; content: string; isCorrect: boolean }[] {
  if (!json) return []
  try {
    const items: unknown = JSON.parse(json)
    if (!Array.isArray(items)) return []
    return items
      .filter((item) => item && typeof item.content === 'string')
      .map((item, index) => ({
        content: item.content,
        label: typeof item.label === 'string' ? item.label : String.fromCharCode(65 + index),
        isCorrect: item.isCorrect === true || item.isCorrect === 1,
      }))
  } catch {
    return []
  }
}
