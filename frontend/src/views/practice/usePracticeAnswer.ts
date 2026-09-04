import { computed, ref, type Ref } from 'vue'
import type { PracticeQuestionVO } from '@/api/practice'

export function usePracticeAnswer(currentQuestion: Ref<PracticeQuestionVO | null>) {
  const userAnswer = ref('')
  const multiAnswers = ref<Set<string>>(new Set())

  const canSubmit = computed(() => {
    if (!currentQuestion.value) return false
    if (currentQuestion.value.questionType === 'MULTIPLE_CHOICE') return multiAnswers.value.size > 0
    return userAnswer.value.trim().length > 0
  })

  const toggleMulti = (label: string) => {
    const selected = new Set(multiAnswers.value)
    if (selected.has(label)) selected.delete(label)
    else selected.add(label)
    multiAnswers.value = selected
  }

  const answer = () =>
    currentQuestion.value?.questionType === 'MULTIPLE_CHOICE'
      ? Array.from(multiAnswers.value).sort().join(',')
      : userAnswer.value.trim()

  const reset = () => {
    userAnswer.value = ''
    multiAnswers.value = new Set()
  }

  return { userAnswer, multiAnswers, canSubmit, toggleMulti, answer, reset }
}
