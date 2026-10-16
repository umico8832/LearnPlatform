import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

const api = vi.hoisted(() => ({ getTutorNext: vi.fn(), requestNextHint: vi.fn(), recordVizView: vi.fn() }))
vi.mock('@/api/questionTutor', () => api)

import { useQuestionTutor } from '@/views/question/useQuestionTutor'

const reviewed = { kind: 'AI_GENERATED', reviewStatus: 'REVIEWED' } as const
function stateAt(hintsUsed: number) {
  return {
    nextHintLevel: hintsUsed < 3 ? hintsUsed + 1 : null,
    hintsUsed,
    misconceptionVizViewed: false,
    solutionVizViewed: false,
    solutionRevealed: false,
    variantAttempted: false,
  }
}
function hint(level: number) {
  return { data: { level, text: `提示 ${level}`, source: reviewed, state: stateAt(level) } }
}

beforeEach(() => {
  vi.clearAllMocks()
  api.getTutorNext.mockResolvedValue({
    data: { action: { type: 'HINT', level: 1 }, reason: '先想概念', reasonSource: reviewed, state: stateAt(0) },
  })
  api.recordVizView.mockResolvedValue({ data: { context: 'MISCONCEPTION', viewed: true, state: stateAt(0) } })
})

describe('useQuestionTutor', () => {
  it('reuses one request id for a retried click and takes a new one for the next click', async () => {
    const tutor = useQuestionTutor(() => 81)
    api.requestNextHint
      .mockRejectedValueOnce(new Error('网络断了'))
      .mockResolvedValueOnce(hint(1))
      .mockResolvedValueOnce(hint(2))
    await tutor.takeHint()
    expect(tutor.hintError.value).toBe('网络断了')
    expect(tutor.hints.value).toHaveLength(0)
    await tutor.takeHint()
    await tutor.takeHint()
    const ids = api.requestNextHint.mock.calls.map((call) => call[1] as string)
    expect(ids[0]).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
    expect(ids[1]).toBe(ids[0])
    expect(ids[2]).not.toBe(ids[0])
    expect(tutor.hints.value.map((item) => item.level)).toEqual([1, 2])
    expect(api.getTutorNext).toHaveBeenCalledTimes(2)
  })

  it('ignores a second click while a hint is on its way and never lists a level twice', async () => {
    let finish!: (value: unknown) => void
    api.requestNextHint.mockReturnValueOnce(new Promise((resolve) => (finish = resolve))).mockResolvedValueOnce(hint(1))
    const tutor = useQuestionTutor(() => 81)
    const first = tutor.takeHint()
    await tutor.takeHint()
    expect(api.requestNextHint).toHaveBeenCalledTimes(1)
    finish(hint(1))
    await first
    await tutor.takeHint()
    expect(tutor.hints.value.map((item) => item.level)).toEqual([1])
  })

  it('records each player once, and again only after a failed record', async () => {
    const tutor = useQuestionTutor(() => 81)
    api.recordVizView.mockRejectedValueOnce(new Error('稍后'))
    await tutor.vizStarted('MISCONCEPTION')
    await tutor.vizStarted('MISCONCEPTION')
    await tutor.vizStarted('MISCONCEPTION')
    await tutor.vizStarted('SOLUTION')
    expect(api.recordVizView.mock.calls).toEqual([
      [81, 'MISCONCEPTION'],
      [81, 'MISCONCEPTION'],
      [81, 'SOLUTION'],
    ])
  })

  it('drops answers that arrive after a reset and waits for an attempt', async () => {
    const attemptId = ref<number | null>(null)
    const tutor = useQuestionTutor(() => attemptId.value)
    await tutor.refresh()
    await tutor.takeHint()
    expect(api.getTutorNext).not.toHaveBeenCalled()
    attemptId.value = 81
    let finish!: (value: unknown) => void
    api.getTutorNext.mockReturnValueOnce(new Promise((resolve) => (finish = resolve)))
    const pending = tutor.refresh()
    tutor.reset()
    finish({ data: { action: { type: 'DONE' }, reason: '', reasonSource: reviewed, state: stateAt(3) } })
    await pending
    expect(tutor.next.value).toBeNull()
    expect(tutor.hintsSettled.value).toBe(false)
  })

  it('treats an omitted next hint level as no further hint', async () => {
    const withoutLevel: Partial<ReturnType<typeof stateAt>> = stateAt(3)
    delete withoutLevel.nextHintLevel
    api.getTutorNext.mockResolvedValueOnce({
      data: { action: { type: 'REVEAL_SOLUTION' }, reason: '', reasonSource: reviewed, state: withoutLevel },
    })
    const tutor = useQuestionTutor(() => 81)
    await tutor.refresh()
    expect(tutor.hintsSettled.value).toBe(true)
  })
})
