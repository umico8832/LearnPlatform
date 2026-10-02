import type { SemanticTagType } from '@/utils/errors'
import type { PracticeResultVO } from '@/api/practice'

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

export function isGradedPracticeResult(result: PracticeResultVO | null) {
  return result?.correct !== null && result?.correct !== undefined
}

export function practiceResultTitle(result: PracticeResultVO) {
  if (!isGradedPracticeResult(result)) return '答案已记录，等待判分'
  return result.correct ? '回答正确' : '请结合解析再看一遍'
}
