import type { SemanticTagType } from '@/utils/errors'

export interface SubmissionOption {
  content: string
  label: string
  isCorrect: boolean
}

export function questionTypeLabel(type: string) {
  const labels: Record<string, string> = {
    SINGLE_CHOICE: '单选题',
    MULTIPLE_CHOICE: '多选题',
    TRUE_FALSE: '判断题',
    FILL_BLANK: '填空题',
    SHORT_ANSWER: '简答题',
  }
  return labels[type] || type
}

export function submissionStatusLabel(status: number) {
  const labels: Record<number, string> = { 0: '待审核', 1: '已通过', 2: '已拒绝', 3: '已入库' }
  return labels[status] || '未知'
}

export function submissionStatusTag(status: number): SemanticTagType {
  const tags: Record<number, SemanticTagType> = { 0: 'warning', 1: 'success', 2: 'danger', 3: undefined }
  return tags[status] || 'info'
}

export function formatSubmissionTime(value: string | null) {
  return value ? value.replace('T', ' ').substring(0, 19) : ''
}

export function parseSubmissionOptions(json: string | null): SubmissionOption[] {
  if (!json) return []
  try {
    const value: unknown = JSON.parse(json)
    return Array.isArray(value) ? (value as SubmissionOption[]) : []
  } catch {
    return []
  }
}
