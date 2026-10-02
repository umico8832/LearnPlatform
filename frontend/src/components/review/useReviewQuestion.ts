import { onScopeDispose, ref, watch, type Ref } from 'vue'
import { getQuestionById, type QuestionVO } from '@/api/question'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'

export function useReviewQuestion(questionId: Ref<number | null>) {
  const question = ref<QuestionVO | null>(null)
  const loading = ref(false)
  const failed = ref(false)
  let requestVersion = 0
  let alive = true

  function clear() {
    question.value = null
    loading.value = false
    failed.value = false
  }

  async function load() {
    const id = questionId.value
    const request = ++requestVersion
    if (!id) {
      clear()
      return
    }
    const session = getAuthSessionVersion()
    const current = () =>
      alive && request === requestVersion && session === getAuthSessionVersion() && id === questionId.value
    question.value = null
    failed.value = false
    loading.value = true
    try {
      const response = await getQuestionById(id, { errorDisplay: 'inline' })
      if (!current()) return
      if (response.code !== 0 || !response.data || response.data.id !== id) throw new Error('Missing review question')
      question.value = response.data
    } catch {
      if (current()) failed.value = true
    } finally {
      if (current()) loading.value = false
    }
  }

  watch(questionId, () => void load(), { immediate: true })
  onScopeDispose(
    onAuthSessionChange(() => {
      requestVersion++
      clear()
    }),
  )
  onScopeDispose(() => {
    alive = false
    requestVersion++
  })

  return { question, loading, failed, load }
}
