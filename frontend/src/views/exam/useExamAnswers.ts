import { computed, ref } from 'vue'

export function useExamAnswers(totalQuestions: () => number) {
  const answers = ref<Record<number, string>>({})
  const answeredCount = computed(() => Object.values(answers.value).filter((answer) => answer.trim()).length)
  const progressPercent = computed(() =>
    totalQuestions() ? Math.round((answeredCount.value / totalQuestions()) * 100) : 0,
  )
  const isMultiSelected = (questionId: number, label: string) =>
    answers.value[questionId]?.split(',').includes(label) || false

  const toggleMulti = (questionId: number, label: string) => {
    const selected = new Set(answers.value[questionId]?.split(',').filter(Boolean) || [])
    if (selected.has(label)) selected.delete(label)
    else selected.add(label)
    answers.value[questionId] = Array.from(selected).sort().join(',')
  }

  return { answers, answeredCount, progressPercent, isMultiSelected, toggleMulti }
}
