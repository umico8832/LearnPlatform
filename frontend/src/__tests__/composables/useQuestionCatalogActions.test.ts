import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { QuestionVO } from '@/api/question'

const mocks = vi.hoisted(() => ({
  add: vi.fn(),
  remove: vi.fn(),
  ids: vi.fn(),
  question: vi.fn(),
  correction: vi.fn(),
  push: vi.fn(),
  auth: { authenticated: true, version: 1, listeners: new Set<() => void>() },
  user: { userInfo: { id: 1 } },
}))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: mocks.push }) }))
vi.mock('@/stores/user', () => ({ useUserStore: () => mocks.user }))
vi.mock('@/api/favorite', () => ({ addFavorite: mocks.add, removeFavorite: mocks.remove, getFavoriteIds: mocks.ids }))
vi.mock('@/api/question', () => ({ getQuestionById: mocks.question, submitQuestionCorrectionReport: mocks.correction }))
vi.mock('@/utils/auth', () => ({
  isAuthenticated: () => mocks.auth.authenticated,
  getAuthSessionVersion: () => mocks.auth.version,
  onAuthSessionChange: (fn: () => void) => {
    mocks.auth.listeners.add(fn)
    return () => mocks.auth.listeners.delete(fn)
  },
}))

import { useQuestionCatalogActions } from '@/views/course/useQuestionCatalogActions'

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: Error) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}
const sample = {
  id: 12,
  content: '题目',
  questionType: 'SINGLE_CHOICE',
  courseId: 7,
  difficulty: 2,
  score: 2,
  options: [{ id: 2, optionLabel: 'A', content: '选项', sortOrder: 0, isCorrect: 1 }],
  analysis: '隐藏解析',
} as QuestionVO

describe('question catalog actions', () => {
  const wrappers: Array<{ unmount: () => void }> = []
  function setup() {
    let state!: ReturnType<typeof useQuestionCatalogActions>
    const wrapper = mount(
      defineComponent({
        setup() {
          state = useQuestionCatalogActions()
          return () => h('div')
        },
      }),
    )
    wrappers.push(wrapper)
    return { state, wrapper }
  }
  function changeAccount() {
    mocks.user.userInfo.id = 2
    mocks.auth.version++
    mocks.auth.listeners.forEach((fn) => fn())
  }
  beforeEach(() => {
    vi.resetAllMocks()
    sessionStorage.clear()
    mocks.auth.authenticated = true
    mocks.auth.version = 1
    mocks.auth.listeners.clear()
    mocks.user.userInfo.id = 1
    mocks.ids.mockResolvedValue({ data: [12] })
    mocks.question.mockResolvedValue({ data: sample })
    mocks.push.mockResolvedValue(undefined)
  })
  afterEach(() => wrappers.splice(0).forEach((wrapper) => wrapper.unmount()))

  it('keeps unknown favorites distinct from an empty collection and retries without allowing a write', async () => {
    mocks.ids.mockRejectedValueOnce(new Error('offline'))
    const { state } = setup()
    await flushPromises()
    expect(state.favoritesReady.value).toBe(false)
    expect(state.favoritesError.value).not.toBe('')
    await state.toggleFavorite(12)
    expect(mocks.remove).not.toHaveBeenCalled()
    await state.loadFavoriteIds()
    expect(state.favoritesReady.value).toBe(true)
    expect(state.favoriteSet.value.has(12)).toBe(true)
  })

  it('deduplicates favorite writes and keeps the confirmed value after failure', async () => {
    const pending = deferred<void>()
    mocks.remove.mockReturnValueOnce(pending.promise)
    const { state } = setup()
    await flushPromises()
    const first = state.toggleFavorite(12)
    await state.toggleFavorite(12)
    expect(mocks.remove).toHaveBeenCalledTimes(1)
    pending.reject(new Error('offline'))
    await first
    expect(state.favoriteSet.value.has(12)).toBe(true)
    expect(state.favoritePending.value.size).toBe(0)
    expect(state.actionError.value).toContain('重试')
    await state.toggleFavorite(12)
    expect(state.favoriteSet.value.has(12)).toBe(false)
  })

  it('does not apply an old favorite mutation to a new account', async () => {
    const pending = deferred<void>()
    mocks.remove.mockReturnValueOnce(pending.promise)
    const { state } = setup()
    await flushPromises()
    const first = state.toggleFavorite(12)
    mocks.ids.mockResolvedValue({ data: [12, 24] })
    changeAccount()
    await flushPromises()
    pending.resolve()
    await first
    expect([...state.favoriteSet.value]).toEqual([12, 24])
    expect(state.notice.value).toBe('')
  })

  it('retains a correction on failure and close/reopen, prevents duplicate submission, then clears on success', async () => {
    const pending = deferred<void>()
    mocks.correction.mockReturnValueOnce(pending.promise)
    const { state } = setup()
    state.openCorrectionDialog(sample)
    state.correctionForm.description = '  题干有歧义  '
    const first = state.submitCorrection()
    await state.submitCorrection()
    expect(mocks.correction).toHaveBeenCalledTimes(1)
    expect(mocks.correction).toHaveBeenCalledWith(
      12,
      { reportType: 'CONTENT', description: '题干有歧义' },
      { errorDisplay: 'inline' },
    )
    pending.reject(new Error('offline'))
    await first
    expect(state.correctionDialogVisible.value).toBe(true)
    expect(state.correctionError.value).toContain('内容已保留')
    state.correctionDialogVisible.value = false
    state.openCorrectionDialog(sample)
    expect(state.correctionForm.description).toBe('  题干有歧义  ')
    await state.submitCorrection()
    expect(state.correctionForm.description).toBe('')
    expect(state.correctionDialogVisible.value).toBe(false)
  })

  it('clears private correction text on account change and ignores its late success', async () => {
    const pending = deferred<void>()
    mocks.correction.mockReturnValueOnce(pending.promise)
    const { state } = setup()
    state.openCorrectionDialog(sample)
    state.correctionForm.description = '旧账号草稿'
    const first = state.submitCorrection()
    changeAccount()
    pending.resolve()
    await first
    expect(state.correctionForm.description).toBe('')
    expect(state.correctionDialogVisible.value).toBe(false)
    expect(state.notice.value).toBe('')
  })

  it('fetches the current question once and saves no answer or analysis before entering practice', async () => {
    const pending = deferred<{ data: QuestionVO }>()
    mocks.question.mockReturnValueOnce(pending.promise)
    const { state } = setup()
    const first = state.startQuestionPractice(12)
    await state.startQuestionPractice(12)
    expect(mocks.question).toHaveBeenCalledTimes(1)
    pending.resolve({ data: sample })
    await first
    const saved = JSON.parse(sessionStorage.getItem('practice_questions') || '[]')
    expect(saved).toHaveLength(1)
    expect(saved[0].id).toBe(12)
    expect(saved[0]).not.toHaveProperty('analysis')
    expect(saved[0].options[0]).not.toHaveProperty('isCorrect')
    expect(sessionStorage.getItem('practice_user_id')).toBe('1')
    expect(mocks.push).toHaveBeenCalledWith({ name: 'PracticeSession' })
  })

  it.each(['account', 'unmount'])('discards a late practice response after %s', async (boundary) => {
    const pending = deferred<{ data: QuestionVO }>()
    mocks.question.mockReturnValueOnce(pending.promise)
    const { state, wrapper } = setup()
    const first = state.startQuestionPractice(12)
    if (boundary === 'account') changeAccount()
    else wrapper.unmount()
    pending.resolve({ data: sample })
    await first
    expect(sessionStorage.getItem('practice_questions')).toBeNull()
    expect(mocks.push).not.toHaveBeenCalled()
  })

  it('keeps the catalog visible and allows retry when a question is unavailable', async () => {
    mocks.question.mockRejectedValueOnce(new Error('unavailable'))
    const { state } = setup()
    await state.startQuestionPractice(12)
    expect(state.practiceStartingId.value).toBeNull()
    expect(state.actionError.value).toContain('重试')
    expect(mocks.push).not.toHaveBeenCalled()
    await state.startQuestionPractice(12)
    expect(mocks.push).toHaveBeenCalledTimes(1)
  })
})
