import type { ExamPaperVO } from '@/api/exam'

export function questionTypeLabel(type: string) {
  const labels: Record<string, string> = {
    SINGLE_CHOICE: '单选',
    MULTIPLE_CHOICE: '多选',
    TRUE_FALSE: '判断',
    FILL_BLANK: '填空',
    SHORT_ANSWER: '简答',
  }
  return labels[type] || type
}

export function paperTypeLabel(paper: Pick<ExamPaperVO, 'paperType' | 'sourceVerified'>) {
  if (paper.paperType === 'OFFICIAL_EXAM') return paper.sourceVerified ? '官方原题' : '官方待核验'
  return '普通练习'
}

export function paperTypeTag(paper: Pick<ExamPaperVO, 'paperType' | 'sourceVerified'>) {
  if (paper.paperType === 'OFFICIAL_EXAM') return paper.sourceVerified ? 'success' : 'warning'
  return 'info'
}
