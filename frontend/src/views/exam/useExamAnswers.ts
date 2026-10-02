import { computed, ref } from 'vue'

export function useExamAnswers(totalQuestions: () => number) {
  const answers = ref<Record<number, string>>({})
  const multiAnswers = ref<Record<number, Set<string>>>({})
  const answeredCount = computed(() => Object.values(answers.value).filter(Boolean).length)
  const progressPercent = computed(() =>
    totalQuestions() ? Math.round((answeredCount.value / totalQuestions()) * 100) : 0,
  )
  const isMultiSelected = (questionId: number, label: string) => multiAnswers.value[questionId]?.has(label) || false

  const toggleMulti = (questionId: number, label: string) => {
    const selected = (multiAnswers.value[questionId] ??= new Set())
    if (selected.has(label)) selected.delete(label)
    else selected.add(label)
    answers.value[questionId] = Array.from(selected).sort().join(',')
  }

  return { answers, answeredCount, progressPercent, isMultiSelected, toggleMulti }
}
