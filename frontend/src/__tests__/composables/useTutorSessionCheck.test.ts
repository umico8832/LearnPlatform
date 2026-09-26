import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { get, start, submit } = vi.hoisted(() => ({ get: vi.fn(), start: vi.fn(), submit: vi.fn() }))
vi.mock('@/api/course', () => ({
  getTutorSession: (...args: unknown[]) => get(...args),
  startTutorSession: (...args: unknown[]) => start(...args),
  submitTutorCheck: (...args: unknown[]) => submit(...args),
}))
import { useTutorSessionCheck } from '@/composables/useTutorSessionCheck'
import type { TutorCheckResultVO } from '@/api/course'

const result: TutorCheckResultVO = {
  correct: true,
  explanation: '正确',
  guidanceType: null,
  guidanceTitle: null,
  guidanceDescription: null,
  guidanceKnowledgePointId: null,
}
const session = (key = 's', answer: string | null = null, checkResult: TutorCheckResultVO | null = null) => ({
  sessionKey: key,
  agentAvailable: true,
  title: '课',
  lesson: { summary: '', steps: [], visualizationId: '' },
  check: {
    id: 'c',
    prompt: '',
    options: [
      { id: 'A', text: 'A' },
      { id: 'B', text: 'B' },
    ],
  },
  checkAnswer: answer,
  checkResult,
  learningContext: {
    paperAnswerCount: 0,
    paperIncorrectCount: 0,
    paperAiAssistanceCount: 0,
    unresolvedWrongCount: 0,
    dueReviewCount: 0,
    reviewAnswerCount: 0,
    latestEvidenceAt: null,
  },
})

describe('useTutorSessionCheck', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
  })
  it('uses the refreshed server session after a successful check submission', async () => {
    start.mockResolvedValue({ data: session() })
    submit.mockResolvedValue({ data: result })
    get.mockResolvedValue({ data: session('s', 'B', result) })
    const state = useTutorSessionCheck(ref(1), ref(2))
    await state.load()
    state.optionId.value = 'A'
    await state.submit()
    expect(state.optionId.value).toBe('B')
    expect(state.result.value).toEqual(result)
    expect(get).toHaveBeenCalledWith(1, 's')
  })
  it('keeps a saved result locked when refresh after submission fails', async () => {
    start.mockResolvedValue({ data: session() })
    submit.mockResolvedValue({ data: result })
    get.mockRejectedValue(new Error('offline'))
    const state = useTutorSessionCheck(ref(1), ref(2))
    await state.load()
    state.optionId.value = 'A'
    await state.submit()
    expect(state.result.value).toEqual(result)
    expect(state.checkFailure.value).toContain('已保存')
  })
  it('keeps a failed restored key until the learner explicitly restarts', async () => {
    sessionStorage.setItem('lp:tutor-session:1:2', 'old')
    get.mockRejectedValue(new Error('offline'))
    start.mockResolvedValue({ data: session('new') })
    const state = useTutorSessionCheck(ref(1), ref(2))
    await state.load()
    expect(sessionStorage.getItem('lp:tutor-session:1:2')).toBe('old')
    await state.load(true)
    expect(start).toHaveBeenCalledWith(1, 2)
  })

  it('ignores a late submission after a new Tutor session starts', async () => {
    let resolve!: (value: { data: TutorCheckResultVO }) => void
    start.mockResolvedValueOnce({ data: session('old') }).mockResolvedValueOnce({ data: session('new') })
    submit.mockReturnValueOnce(
      new Promise((done) => {
        resolve = done
      }),
    )
    const state = useTutorSessionCheck(ref(1), ref(2))
    await state.load()
    state.optionId.value = 'A'
    const pending = state.submit()
    await state.load(true)
    resolve({ data: result })
    await pending
    expect(state.session.value?.sessionKey).toBe('new')
    expect(state.result.value).toBeUndefined()
    expect(state.submitting.value).toBe(false)
  })

  it('does not show an old session refresh failure after switching sessions', async () => {
    let reject!: (error: Error) => void
    start.mockResolvedValueOnce({ data: session('old') }).mockResolvedValueOnce({ data: session('new') })
    submit.mockResolvedValueOnce({ data: result })
    get.mockReturnValueOnce(
      new Promise((_resolve, fail) => {
        reject = fail
      }),
    )
    const state = useTutorSessionCheck(ref(1), ref(2))
    await state.load()
    state.optionId.value = 'A'
    const pending = state.submit()
    await vi.waitFor(() => expect(get).toHaveBeenCalledWith(1, 'old'))
    await state.load(true)
    reject(new Error('old refresh failed'))
    await pending
    expect(state.session.value?.sessionKey).toBe('new')
    expect(state.checkFailure.value).toBe('')
  })
})
