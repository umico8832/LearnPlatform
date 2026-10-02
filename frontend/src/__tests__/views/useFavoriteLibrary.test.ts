import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick } from 'vue'
import { removeToken, setToken } from '@/utils/auth'
import { useUserStore } from '@/stores/user'

const { push, getFavorites, removeFavorite, getFavoritePractice, savePracticeSession } = vi.hoisted(() => ({
  push: vi.fn(),
  getFavorites: vi.fn(),
  removeFavorite: vi.fn(),
  getFavoritePractice: vi.fn(),
  savePracticeSession: vi.fn(),
}))
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))
vi.mock('@/api/favorite', () => ({ getFavorites, removeFavorite }))
vi.mock('@/api/practice', () => ({ getFavoritePractice }))
vi.mock('@/utils/practiceSession', () => ({ savePracticeSession, clearPracticeSession: vi.fn() }))

import { useFavoriteLibrary } from '@/views/practice/useFavoriteLibrary'

const favorite = { id: 1, questionId: 9, questionContent: '收藏题', questionType: 'SINGLE_CHOICE' }

describe('useFavoriteLibrary', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    setToken('favorite-a')
    useUserStore().setLoginInfo('favorite-a', {
      id: 7,
      username: 'learner',
      nickname: 'Learner',
      avatar: null,
      role: 'USER',
    })
    getFavorites.mockResolvedValue({ code: 0, data: { records: [favorite], total: 1 } })
  })
  afterEach(() => removeToken())

  it('requires a valid count before starting a group of questions', async () => {
    const scope = effectScope()
    const library = scope.run(useFavoriteLibrary)!
    library.practiceCount.value = 0
    await library.startPractice()
    expect(getFavoritePractice).not.toHaveBeenCalled()
    expect(library.actionError.value).toContain('1 到 50')
    scope.stop()
  })

  it('prevents duplicate single-question starts and only navigates after a valid response', async () => {
    let resolve!: (value: unknown) => void
    getFavoritePractice.mockImplementation(() => new Promise((next) => (resolve = next)))
    savePracticeSession.mockReturnValue(true)
    const scope = effectScope()
    const library = scope.run(useFavoriteLibrary)!

    const first = library.startPractice(9)
    const duplicate = library.startPractice(9)
    expect(getFavoritePractice).toHaveBeenCalledTimes(1)
    resolve({ code: 0, data: [favorite] })
    await Promise.all([first, duplicate])

    expect(savePracticeSession).toHaveBeenCalledWith(7, [favorite], 'favorite')
    expect(push).toHaveBeenCalledWith({ name: 'PracticeSession' })
    scope.stop()
  })

  it('keeps the learner on the page after a failed group start and permits retry', async () => {
    getFavoritePractice.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({ code: 0, data: [favorite] })
    savePracticeSession.mockReturnValue(true)
    const scope = effectScope()
    const library = scope.run(useFavoriteLibrary)!

    await library.startPractice()
    expect(library.actionError.value).toContain('暂时无法开始')
    expect(push).not.toHaveBeenCalled()
    await library.startPractice()

    expect(getFavoritePractice).toHaveBeenCalledTimes(2)
    expect(push).toHaveBeenCalledTimes(1)
    scope.stop()
  })

  it('does not navigate or cache a stale practice response after an account switch', async () => {
    let resolve!: (value: unknown) => void
    getFavoritePractice.mockImplementation(() => new Promise((next) => (resolve = next)))
    savePracticeSession.mockReturnValue(true)
    const scope = effectScope()
    const library = scope.run(useFavoriteLibrary)!

    const pending = library.startPractice(9)
    setToken('favorite-b')
    await nextTick()
    resolve({ code: 0, data: [favorite] })
    await pending

    expect(savePracticeSession).not.toHaveBeenCalled()
    expect(push).not.toHaveBeenCalled()
    expect(library.startingId.value).toBeNull()
    scope.stop()
  })
})
