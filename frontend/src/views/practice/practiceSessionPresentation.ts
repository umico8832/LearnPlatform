import type { SemanticTagType } from '@/utils/errors'

export function practiceQuestionTypeLabel(type: string) {
  const labels: Record<string, string> = {
    SINGLE_CHOICE: '单选题',
    MULTIPLE_CHOICE: '多选题',
    TRUE_FALSE: '判断题',
    FILL_BLANK: '填空题',
    SHORT_ANSWER: '简答题',
  }
  return labels[type] || type
}

export function practiceQuestionTypeTag(type: string): SemanticTagType {
  const tags: Record<string, SemanticTagType> = {
    SINGLE_CHOICE: undefined,
    MULTIPLE_CHOICE: 'warning',
    TRUE_FALSE: 'success',
    FILL_BLANK: 'info',
    SHORT_ANSWER: 'danger',
  }
  return tags[type]
}

export function practiceReturnRoute(mode: string) {
  if (mode === 'wrong_question' || mode === 'similar') return { name: 'WrongQuestions' }
  if (mode === 'favorite') return { name: 'Favorites' }
  if (mode === 'recommended') return { name: 'LearningDiagnosis' }
  return { name: 'Practice' }
}
