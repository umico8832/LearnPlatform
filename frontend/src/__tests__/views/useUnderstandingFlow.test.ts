import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { BusinessError } from '@/utils/errors'
import type { AttemptResult, Solution, UnderstandingQuestion, Variant, VariantResult } from '@/api/understanding'

const api = vi.hoisted(() => ({
  getUnderstandingQuestion: vi.fn(),
  submitAttempt: vi.fn(),
  revealSolution: vi.fn(),
  getVariant: vi.fn(),
  submitVariantAttempt: vi.fn(),
}))
vi.mock('@/api/understanding', () => api)
const tutorApi = vi.hoisted(() => ({ getTutorNext: vi.fn(), requestNextHint: vi.fn(), recordVizView: vi.fn() }))
vi.mock('@/api/questionTutor', () => tutorApi)

import { useUnderstandingFlow } from '@/views/question/useUnderstandingFlow'

const question: UnderstandingQuestion = {
  questionId: 17,
  stem: '哪个出栈序列可行？',
  questionType: 'SINGLE_CHOICE',
  options: [
    { label: 'A', content: '可行' },
    { label: 'B', content: '不可行' },
  ],
  position: {
    courseId: 1,
    courseName: '数据结构',
    chapterName: '栈',
    knowledgePointId: 2,
    knowledgePointName: '出栈序列',
  },
  source: { kind: 'AI_GENERATED', title: '种子', url: null, verification: 'REVIEWED' },
}
const wrongViz = {
  title: '误区演示',
  spec: { scene: 'stack', op: 'check', seq: [3, 1, 2], pauseAt: ['stack-buried'] },
  source: { kind: 'CURATED', reviewStatus: 'REVIEWED' },
} as const
const wrongAttempt: AttemptResult = {
  attemptId: 81,
  correct: false,
  answer: 'B',
  misconception: {
    title: '越过栈顶',
    explanation: '不能越过栈顶',
    source: { kind: 'CURATED', reviewStatus: 'REVIEWED' },
  },
  viz: wrongViz as AttemptResult['viz'],
}
const solution: Solution = {
  correctAnswer: 'A',
  explanation: '从栈顶弹出',
  explanationSource: { kind: 'CURATED', reviewStatus: 'REVIEWED' },
  viz: {
    title: '正解演示',
    spec: { scene: 'stack', op: 'check', seq: [3, 2, 1], pauseAt: [] },
    source: { kind: 'CURATED', reviewStatus: 'REVIEWED' },
  } as Solution['viz'],
}
const variant: Variant = {
  variantId: 91,
  stem: '哪个不可能？',
  options: question.options,
  changedCondition: '问法变化',
  source: question.source,
}
const variantResult: VariantResult = {
  variantAttemptId: 92,
  correct: true,
  correctAnswer: 'B',
  explanation: '仍遵守栈顶规则',
  explanationSource: { kind: 'AI_GENERATED', reviewStatus: 'REVIEWED' },
  viz: null,
  assistance: 'UNAIDED',
}

beforeEach(() => {
  vi.clearAllMocks()
  api.getUnderstandingQuestion.mockResolvedValue({ data: question })
  api.submitAttempt.mockResolvedValue({ data: wrongAttempt })
  api.revealSolution.mockResolvedValue({ data: solution })
  api.getVariant.mockResolvedValue({ data: variant })
  api.submitVariantAttempt.mockResolvedValue({ data: variantResult })
  tutorApi.getTutorNext.mockResolvedValue({
    data: {
      action: { type: 'REVEAL_SOLUTION' },
      reason: '看正解',
      reasonSource: { kind: 'CURATED', reviewStatus: 'REVIEWED' },
      state: null,
    },
  })
})

describe('useUnderstandingFlow', () => {
  it('loads the question and distinguishes missing content from retryable errors', async () => {
    const flow = useUnderstandingFlow(() => 17)
    await flow.load()
    expect(flow.loadStatus.value).toBe('ready')
    expect(flow.question.value).toEqual(question)
    api.getUnderstandingQuestion.mockRejectedValueOnce(new BusinessError('没有内容', 1004))
    await flow.load()
    expect(flow.loadStatus.value).toBe('missing')
    api.getUnderstandingQuestion.mockRejectedValueOnce(new Error('断网'))
    await flow.load()
    expect(flow.loadStatus.value).toBe('error')
    expect(flow.loadError.value).toBe('断网')
    await flow.load()
    expect(flow.loadStatus.value).toBe('ready')
  })

  it('wrong answers open only the chosen misconception until the learner requests the solution', async () => {
    const flow = useUnderstandingFlow(() => 17)
    await flow.load()
    flow.selected.value = 'B'
    await flow.submit()
    expect(flow.wrong.value).toBe(true)
    expect(flow.attempt.result.value?.misconception?.title).toBe('越过栈顶')
    expect(api.revealSolution).not.toHaveBeenCalled()
    expect(api.getVariant).not.toHaveBeenCalled()
    await flow.openSolution()
    await flushPromises()
    expect(flow.solution.status.value).toBe('done')
    expect(flow.variant.result.value).toEqual(variant)
    expect(flow.solutionViz.value).toEqual(solution.viz)
  })

  it('right answers open the solution and variant automatically', async () => {
    api.submitAttempt.mockResolvedValueOnce({
      data: { ...wrongAttempt, answer: 'A', correct: true, misconception: null, viz: null },
    })
    const flow = useUnderstandingFlow(() => 17)
    await flow.load()
    flow.selected.value = 'A'
    await flow.submit()
    await flushPromises()
    expect(api.revealSolution).toHaveBeenCalledWith(81)
    expect(flow.solution.status.value).toBe('done')
    expect(api.getVariant).toHaveBeenCalledWith(81)
  })

  it('retries a failed solution and treats a missing variant as a completed absence', async () => {
    api.revealSolution.mockRejectedValueOnce(new Error('稍后重试'))
    api.getVariant.mockRejectedValueOnce(new BusinessError('没有变式', 1004))
    const flow = useUnderstandingFlow(() => 17)
    await flow.load()
    flow.selected.value = 'B'
    await flow.submit()
    await flow.openSolution()
    expect(flow.solution.status.value).toBe('error')
    expect(flow.solution.error.value).toBe('稍后重试')
    await flow.openSolution()
    await flushPromises()
    expect(api.revealSolution).toHaveBeenCalledTimes(2)
    expect(flow.variantMissing.value).toBe(true)
    expect(flow.variant.status.value).toBe('done')
  })

  it('submits an unaided variant with nonnegative integer answer times', async () => {
    const flow = useUnderstandingFlow(() => 17)
    await flow.load()
    flow.selected.value = 'B'
    await flow.submit()
    const originalTime = api.submitAttempt.mock.calls[0][2]
    expect(Number.isInteger(originalTime)).toBe(true)
    expect(originalTime).toBeGreaterThanOrEqual(0)
    await flow.openSolution()
    await flushPromises()
    flow.variantSelected.value = 'B'
    await flow.submitVariant()
    expect(api.submitVariantAttempt).toHaveBeenCalledWith(91, 81, 'B', expect.any(Number))
    const variantTime = api.submitVariantAttempt.mock.calls[0][3]
    expect(Number.isInteger(variantTime)).toBe(true)
    expect(variantTime).toBeGreaterThanOrEqual(0)
    expect(flow.variantAttempt.result.value).toEqual(variantResult)
  })

  it('does not duplicate the misconception player when solution spec differs only by pauseAt', async () => {
    api.revealSolution.mockResolvedValueOnce({
      data: { ...solution, viz: { ...wrongViz, spec: { ...wrongViz.spec, pauseAt: [] } } },
    })
    const flow = useUnderstandingFlow(() => 17)
    await flow.load()
    flow.selected.value = 'B'
    await flow.submit()
    await flow.openSolution()
    expect(flow.solutionViz.value).toBeNull()
  })

  it('asks the tutor again after the attempt, the solution and the variant', async () => {
    const flow = useUnderstandingFlow(() => 17)
    await flow.load()
    flow.selected.value = 'B'
    await flow.submit()
    await flushPromises()
    expect(tutorApi.getTutorNext).toHaveBeenCalledTimes(1)
    await flow.openSolution()
    await flushPromises()
    expect(tutorApi.getTutorNext).toHaveBeenCalledTimes(2)
    flow.variantSelected.value = 'B'
    await flow.submitVariant()
    await flushPromises()
    expect(tutorApi.getTutorNext).toHaveBeenCalledTimes(3)
    expect(tutorApi.getTutorNext).toHaveBeenLastCalledWith(81)
  })

  it('guards both submissions while a request is pending', async () => {
    let finishAttempt!: (value: unknown) => void
    api.submitAttempt.mockReturnValueOnce(
      new Promise((resolve) => {
        finishAttempt = resolve
      }),
    )
    const flow = useUnderstandingFlow(() => 17)
    await flow.load()
    flow.selected.value = 'B'
    const first = flow.submit()
    await flow.submit()
    expect(api.submitAttempt).toHaveBeenCalledTimes(1)
    finishAttempt({ data: wrongAttempt })
    await first
    await flow.openSolution()
    await flushPromises()
    let finishVariant!: (value: unknown) => void
    api.submitVariantAttempt.mockReturnValueOnce(
      new Promise((resolve) => {
        finishVariant = resolve
      }),
    )
    flow.variantSelected.value = 'B'
    const second = flow.submitVariant()
    await flow.submitVariant()
    expect(api.submitVariantAttempt).toHaveBeenCalledTimes(1)
    finishVariant({ data: variantResult })
    await second
  })
})
