import {
  getUnderstandingQuestion,
  getVariant,
  revealSolution,
  submitAttempt,
  submitVariantAttempt,
  type AttemptResult,
  type Solution,
  type UnderstandingQuestion,
  type Variant,
  type VariantResult,
} from '@/api/understanding'
import {
  getTutorNext,
  recordVizView,
  requestNextHint,
  type TutorHint,
  type TutorNext,
  type VizContext,
} from '@/api/questionTutor'
import {
  previewUnderstandingQuestion,
  type PreviewProgress,
  type UnderstandingPreview,
} from '@/api/understandingPreview'
import { BusinessError } from '@/utils/errors'

/**
 * Everything the "一道题从错到懂" page asks the server, in one place. The live
 * transport talks to the real attempt and tutor endpoints; the preview one
 * replays the same steps against the admin preview, which recomputes the
 * whole page from the progress it is sent and records nothing.
 */
export interface UnderstandingTransport {
  readonly preview: boolean
  /** Start over: a preview forgets its progress; the live transport has nothing to forget. */
  reset(): void
  question(questionId: number): Promise<UnderstandingQuestion>
  submitAttempt(questionId: number, answer: string, answerTime: number): Promise<AttemptResult>
  revealSolution(attemptId: number): Promise<Solution>
  /** Rejects with code 1004 when the question has no variant. */
  variant(attemptId: number): Promise<Variant>
  submitVariant(variantId: number, attemptId: number, answer: string, answerTime: number): Promise<VariantResult>
  tutorNext(attemptId: number): Promise<TutorNext>
  nextHint(attemptId: number, requestId: string): Promise<TutorHint>
  vizStarted(attemptId: number, context: VizContext): Promise<void>
}

export const liveTransport: UnderstandingTransport = {
  preview: false,
  reset() {},
  question: (id) => getUnderstandingQuestion(id).then((r) => r.data),
  submitAttempt: (id, answer, time) => submitAttempt(id, answer, time).then((r) => r.data),
  revealSolution: (attemptId) => revealSolution(attemptId).then((r) => r.data),
  variant: (attemptId) => getVariant(attemptId).then((r) => r.data),
  submitVariant: (variantId, attemptId, answer, time) =>
    submitVariantAttempt(variantId, attemptId, answer, time).then((r) => r.data),
  tutorNext: (attemptId) => getTutorNext(attemptId).then((r) => r.data),
  nextHint: (attemptId, requestId) => requestNextHint(attemptId, requestId).then((r) => r.data),
  vizStarted: (attemptId, context) => recordVizView(attemptId, context).then(() => undefined),
}

/** A preview has no real attempt; the flow only needs some id to hang its steps on. */
const PREVIEW_ID = 0
const NOT_FOUND = 1004

export function previewTransport(questionId: () => number): UnderstandingTransport {
  let progress: PreviewProgress = {}

  /** Ask with the progress one step further; keep that step only if the server accepts it. */
  async function step(change: PreviewProgress = {}): Promise<UnderstandingPreview> {
    const next = { ...progress, ...change }
    const response = await previewUnderstandingQuestion(questionId(), next)
    progress = next
    return response.data
  }

  function missing(what: string): never {
    throw new BusinessError(`预览里没有${what}`, NOT_FOUND)
  }

  return {
    preview: true,
    reset() {
      progress = {}
    },
    async question() {
      progress = {}
      return (await step()).question
    },
    async submitAttempt(_id, answer) {
      const data = await step({ answer })
      return data.attempt ? { ...data.attempt, attemptId: PREVIEW_ID } : missing('作答结果')
    },
    async revealSolution() {
      const data = await step({ revealSolution: true })
      return data.solution ?? missing('正解')
    },
    async variant() {
      const data = await step()
      return data.variant ?? missing('变式题')
    },
    async submitVariant(_variantId, _attemptId, variantAnswer) {
      const data = await step({ variantAnswer })
      return data.variantResult ? { ...data.variantResult, variantAttemptId: PREVIEW_ID } : missing('变式结果')
    },
    async tutorNext() {
      const data = await step()
      return (data.next as TutorNext | null) ?? missing('下一步建议')
    },
    async nextHint() {
      const data = await step({ hintLevel: (progress.hintLevel ?? 0) + 1 })
      const hint = data.hints.at(-1)
      if (!hint || !data.next) return missing('提示')
      return { ...hint, state: data.next.state } as TutorHint
    },
    async vizStarted(_attemptId, context) {
      await step(context === 'MISCONCEPTION' ? { misconceptionVizViewed: true } : { solutionVizViewed: true })
    },
  }
}
