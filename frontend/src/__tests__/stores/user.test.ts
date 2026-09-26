import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUserStore } from '@/stores/user'
import request from '@/utils/request'

// Mock request module
vi.mock('@/utils/request', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

describe('user store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  describe('initial state', () => {
    it('starts with null token and null userInfo', () => {
      const store = useUserStore()
      expect(store.token).toBeNull()
      expect(store.userInfo).toBeNull()
    })
  })

  describe('setLoginInfo', () => {
    it('sets token, userInfo and persists token to localStorage', () => {
      const store = useUserStore()
      const user = { id: 1, username: 'testuser', nickname: 'Test', avatar: null, role: 'USER' as const }
      store.setLoginInfo('jwt-token-123', user)

      expect(store.token).toBe('jwt-token-123')
      expect(store.userInfo).toEqual(user)
      expect(localStorage.getItem('learn_platform_token')).toBe('jwt-token-123')
    })
  })

  describe('clearLoginInfo', () => {
    it('clears token, userInfo and removes token from localStorage', () => {
      const store = useUserStore()
      store.setLoginInfo('token', { id: 1, username: 'u', nickname: 'n', avatar: null, role: 'USER' })
      store.clearLoginInfo()

      expect(store.token).toBeNull()
      expect(store.userInfo).toBeNull()
      expect(localStorage.getItem('learn_platform_token')).toBeNull()
    })
  })

  it('clears private practice caches when logging out, including legacy caches', () => {
    const store = useUserStore()
    store.setLoginInfo('test-token', { id: 1, username: 'a', nickname: 'A', avatar: null, role: 'USER' })
    sessionStorage.setItem('practice_questions', JSON.stringify([{ id: 1, content: 'private paper' }]))
    sessionStorage.setItem('practice_mode', 'favorite')
    sessionStorage.setItem('practice_user_id', '1')
    store.clearLoginInfo()
    expect(sessionStorage.getItem('practice_questions')).toBeNull()
    expect(sessionStorage.getItem('practice_mode')).toBeNull()
    expect(sessionStorage.getItem('practice_user_id')).toBeNull()
  })

  it.each([false, true])('ignores a previous login user lookup (fails: %s)', async (fails) => {
    const store = useUserStore()
    const previous = { id: 1, username: 'a', nickname: 'A', avatar: null, role: 'USER' as const }
    const current = { ...previous, id: 2, username: 'b' }
    store.setLoginInfo('old-test-token', previous)
    let resolveLookup: (value: unknown) => void = () => undefined
    let rejectLookup: (reason: Error) => void = () => undefined
    vi.mocked(request.get).mockImplementationOnce(
      () =>
        new Promise((resolve, reject) => {
          resolveLookup = resolve
          rejectLookup = reject
        }),
    )
    const pending = store.fetchUserInfo()
    store.clearLoginInfo()
    store.setLoginInfo('new-test-token', current)
    if (fails) rejectLookup(new Error('old lookup failed'))
    else resolveLookup({ data: previous })
    await pending
    expect(store.userInfo).toEqual(current)
    expect(store.token).toBe('new-test-token')
  })

  describe('isLoggedIn', () => {
    it('returns false when not logged in', () => {
      const store = useUserStore()
      expect(store.isLoggedIn()).toBe(false)
    })

    it('returns true after setLoginInfo', () => {
      const store = useUserStore()
      store.setLoginInfo('token', { id: 1, username: 'u', nickname: 'n', avatar: null, role: 'USER' })
      expect(store.isLoggedIn()).toBe(true)
    })

    it('returns false after clearLoginInfo', () => {
      const store = useUserStore()
      store.setLoginInfo('token', { id: 1, username: 'u', nickname: 'n', avatar: null, role: 'USER' })
      store.clearLoginInfo()
      expect(store.isLoggedIn()).toBe(false)
    })
  })

  describe('fetchUserInfo', () => {
    it('does nothing when no token exists', async () => {
      const store = useUserStore()
      await store.fetchUserInfo()
      expect(store.userInfo).toBeNull()
    })
  })
})
