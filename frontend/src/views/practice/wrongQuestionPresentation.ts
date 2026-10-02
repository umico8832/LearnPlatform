import type { SemanticTagType } from '@/utils/errors'

export function wrongQuestionTypeLabel(type: string) {
  const labels: Record<string, string> = {
    SINGLE_CHOICE: '单选',
    MULTIPLE_CHOICE: '多选',
    TRUE_FALSE: '判断',
    FILL_BLANK: '填空',
    SHORT_ANSWER: '简答',
  }
  return labels[type] || type
}

export function wrongQuestionTypeTag(type: string): SemanticTagType {
  const tags: Record<string, SemanticTagType> = {
    SINGLE_CHOICE: undefined,
    MULTIPLE_CHOICE: 'warning',
    TRUE_FALSE: 'success',
    FILL_BLANK: 'info',
    SHORT_ANSWER: 'danger',
  }
  return tags[type]
}

export function masteryLabel(level: number) {
  return ({ 0: '未掌握', 1: '部分掌握', 2: '已掌握' } as Record<number, string>)[level] || '未知'
}

export function masteryTag(level: number): SemanticTagType {
  return ({ 0: 'danger', 1: 'warning', 2: 'success' } as Record<number, SemanticTagType>)[level] || 'info'
}
