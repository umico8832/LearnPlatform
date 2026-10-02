export const practiceQuestionTypes = [
  { value: 'SINGLE_CHOICE', label: '单选题' },
  { value: 'MULTIPLE_CHOICE', label: '多选题' },
  { value: 'TRUE_FALSE', label: '判断题' },
  { value: 'FILL_BLANK', label: '填空题' },
  { value: 'SHORT_ANSWER', label: '简答题' },
]

export function questionTypeLabel(type: string) {
  return practiceQuestionTypes.find((item) => item.value === type)?.label || type
}

export function difficultyLabel(difficulty: number) {
  return ['入门', '基础', '进阶', '挑战', '综合'][difficulty - 1] || '难度未标注'
}

export function recordTime(time: string | null | undefined) {
  return time ? time.replace('T', ' ').slice(0, 16) : '时间未记录'
}

export function answerStatus(correct: number | null | undefined) {
  return correct === 1 ? '正确' : correct === 0 ? '需复习' : '待判分'
}
