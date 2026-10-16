import { computed, ref, shallowRef } from 'vue'
import type { TutorHint, TutorNext, VizContext } from '@/api/questionTutor'
import { errorMessage } from '@/utils/errors'
import { liveTransport, type UnderstandingTransport } from './understandingTransport'

/**
 * The tutor beside one attempt: which step the server suggests next, the hint
 * ladder the learner has climbed, and which players have started. The server
 * owns every rule (levels, the solution gate); the page only follows `next`.
 */
export function useQuestionTutor(
  attemptId: () => number | null,
  transport: () => UnderstandingTransport = () => liveTransport,
) {
  const next = shallowRef<TutorNext | null>(null)
  const nextError = ref('')
  const hints = shallowRef<TutorHint[]>([])
  const hintPending = ref(false)
  const hintError = ref('')
  const reported = new Set<VizContext>()
  // One click is one request id; a retry after a failure sends the same id, so it never skips a level.
  let pendingRequestId: string | null = null
  let generation = 0

  function reset() {
    generation += 1
    next.value = null
    nextError.value = ''
    hints.value = []
    hintPending.value = false
    hintError.value = ''
    reported.clear()
    pendingRequestId = null
  }

  async function refresh() {
    const id = attemptId()
    if (id === null) return
    const current = generation
    nextError.value = ''
    try {
      const suggestion = await transport().tutorNext(id)
      if (current === generation) next.value = suggestion
    } catch (e) {
      if (current === generation) nextError.value = errorMessage(e, '没能取到下一步建议，请重试')
    }
  }

  async function takeHint() {
    const id = attemptId()
    if (id === null || hintPending.value) return
    const current = generation
    pendingRequestId ??= crypto.randomUUID()
    hintPending.value = true
    hintError.value = ''
    try {
      const hint = await transport().nextHint(id, pendingRequestId)
      if (current !== generation) return
      pendingRequestId = null
      if (!hints.value.some((taken) => taken.level === hint.level)) {
        hints.value = [...hints.value, hint].sort((a, b) => a.level - b.level)
      }
      await refresh()
    } catch (e) {
      if (current === generation) hintError.value = errorMessage(e, '提示没有取到，请重试')
    } finally {
      if (current === generation) hintPending.value = false
    }
  }

  /** Recorded once per context when the learner starts a player; a failed record never blocks learning. */
  async function vizStarted(context: VizContext) {
    const id = attemptId()
    if (id === null || reported.has(context)) return
    reported.add(context)
    const current = generation
    try {
      await transport().vizStarted(id, context)
      if (current === generation) await refresh()
    } catch {
      if (current === generation) reported.delete(context)
    }
  }

  const action = computed(() => next.value?.action ?? null)
  const state = computed(() => next.value?.state ?? null)
  /** No further hint is coming: the ladder is done or this question has none. The server omits null fields. */
  const hintsSettled = computed(() => state.value !== null && (state.value.nextHintLevel ?? null) === null)

  return {
    next,
    nextError,
    action,
    state,
    hintsSettled,
    hints,
    hintPending,
    hintError,
    refresh,
    takeHint,
    vizStarted,
    reset,
  }
}

export type QuestionTutor = ReturnType<typeof useQuestionTutor>
