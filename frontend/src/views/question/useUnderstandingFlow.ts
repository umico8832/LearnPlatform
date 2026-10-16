import { computed, ref, shallowRef } from 'vue'
import type {
  AttemptResult,
  Solution,
  UnderstandingQuestion,
  UnderstandingViz,
  Variant,
  VariantResult,
} from '@/api/understanding'
import { businessCode, errorMessage } from '@/utils/errors'
import { useQuestionTutor } from './useQuestionTutor'
import { liveTransport, type UnderstandingTransport } from './understandingTransport'

const NOT_FOUND = 1004

export type LoadStatus = 'loading' | 'ready' | 'missing' | 'error'
export type TaskStatus = 'idle' | 'pending' | 'done' | 'error'

/** One async step of the flow: its status, its last error, and a guarded runner. */
function useTask<T>() {
  const status = ref<TaskStatus>('idle')
  const error = ref('')
  const result = shallowRef<T | null>(null)

  async function run(work: () => Promise<T>, fallback: string): Promise<T | null> {
    if (status.value === 'pending') return null
    status.value = 'pending'
    error.value = ''
    try {
      const value = await work()
      result.value = value
      status.value = 'done'
      return value
    } catch (e) {
      error.value = errorMessage(e, fallback)
      status.value = 'error'
      return null
    }
  }

  function reset() {
    status.value = 'idle'
    error.value = ''
    result.value = null
  }

  return { status, error, result, run, reset }
}

function seconds(since: number): number {
  return Math.max(0, Math.round((performance.now() - since) / 1000))
}

/** Same scene, operation and data: a second player would only repeat the first one. */
export function sameViz(a: UnderstandingViz | null, b: UnderstandingViz | null): boolean {
  if (!a || !b) return false
  // JSON.stringify drops undefined, so the checkpoints to pause at do not count.
  const story = (viz: UnderstandingViz) => JSON.stringify({ ...viz.spec, pauseAt: undefined })
  return story(a) === story(b)
}

/**
 * "一道题从错到懂": answer → (wrong) the chosen option's misconception, the
 * tutor's hint ladder and a visualization paused at the error step → solution
 * → a variant. The server grades everything, gates the solution behind the
 * hints and decides the next step; answers reach the client only when opened.
 */
export function useUnderstandingFlow(
  questionId: () => number,
  transport: () => UnderstandingTransport = () => liveTransport,
) {
  const loadStatus = ref<LoadStatus>('loading')
  const loadError = ref('')
  const question = shallowRef<UnderstandingQuestion | null>(null)

  const selected = ref<string | null>(null)
  const attempt = useTask<AttemptResult>()
  const solution = useTask<Solution>()
  const variant = useTask<Variant>()
  const variantMissing = ref(false)
  const variantSelected = ref<string | null>(null)
  const variantAttempt = useTask<VariantResult>()
  const tutor = useQuestionTutor(() => attempt.result.value?.attemptId ?? null, transport)

  let shownAt = 0
  let variantShownAt = 0

  async function load() {
    const id = questionId()
    loadStatus.value = 'loading'
    loadError.value = ''
    for (const task of [attempt, solution, variant, variantAttempt]) task.reset()
    tutor.reset()
    selected.value = null
    variantSelected.value = null
    variantMissing.value = false
    try {
      transport().reset()
      const loaded = await transport().question(id)
      if (id !== questionId()) return
      question.value = loaded
      loadStatus.value = 'ready'
      shownAt = performance.now()
    } catch (e) {
      if (id !== questionId()) return
      question.value = null
      loadStatus.value = businessCode(e) === NOT_FOUND ? 'missing' : 'error'
      loadError.value = errorMessage(e, '题目加载失败，请检查网络后重试')
    }
  }

  const answered = computed(() => attempt.result.value !== null)
  const wrong = computed(() => attempt.result.value?.correct === false)

  async function openSolution() {
    const result = attempt.result.value
    if (!result) return
    const opened = await solution.run(() => transport().revealSolution(result.attemptId), '正解加载失败，请重试')
    // Also after a refusal: the gate may have moved on since the last suggestion.
    void tutor.refresh()
    if (opened) void loadVariant()
  }

  async function submit() {
    const current = question.value
    const answer = selected.value
    if (!current || !answer || answered.value) return
    const result = await attempt.run(
      () => transport().submitAttempt(current.questionId, answer, seconds(shownAt)),
      '提交失败，请重试',
    )
    // A right answer goes straight to the full reasoning; a wrong one first meets its misconception.
    if (result?.correct) void openSolution()
    else if (result) void tutor.refresh()
  }

  async function loadVariant() {
    const result = attempt.result.value
    if (!result || variant.status.value === 'pending' || variant.status.value === 'done') return
    variant.status.value = 'pending'
    variant.error.value = ''
    variantMissing.value = false
    try {
      variant.result.value = await transport().variant(result.attemptId)
      variant.status.value = 'done'
      variantShownAt = performance.now()
    } catch (e) {
      if (businessCode(e) === NOT_FOUND) {
        variantMissing.value = true
        variant.status.value = 'done'
        return
      }
      variant.error.value = errorMessage(e, '变式题加载失败，请重试')
      variant.status.value = 'error'
    }
  }

  async function submitVariant() {
    const result = attempt.result.value
    const current = variant.result.value
    const answer = variantSelected.value
    if (!result || !current || !answer || variantAttempt.result.value) return
    await variantAttempt.run(
      () => transport().submitVariant(current.variantId, result.attemptId, answer, seconds(variantShownAt)),
      '提交失败，请重试',
    )
    void tutor.refresh()
  }

  /** The solution's own player, unless it only repeats the misconception's one. */
  const solutionViz = computed(() => {
    const viz = solution.result.value?.viz ?? null
    return wrong.value && sameViz(viz, attempt.result.value?.viz ?? null) ? null : viz
  })

  return {
    loadStatus,
    loadError,
    question,
    load,
    selected,
    attempt,
    answered,
    wrong,
    submit,
    solution,
    solutionViz,
    openSolution,
    variant,
    variantMissing,
    loadVariant,
    variantSelected,
    variantAttempt,
    submitVariant,
    tutor,
  }
}

export type UnderstandingFlow = ReturnType<typeof useUnderstandingFlow>
