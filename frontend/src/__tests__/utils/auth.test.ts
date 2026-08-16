import { describe, it, expect, beforeEach } from 'vitest'
import { getToken, setToken, removeToken, isAuthenticated } from '@/utils/auth'

const TOKEN_KEY = 'learn_platform_token'

describe('auth utils', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  describe('getToken', () => {
    it('returns null when no token is stored', () => {
      expect(getToken()).toBeNull()
    })

    it('returns the stored token', () => {
      localStorage.setItem(TOKEN_KEY, 'abc123')
      expect(getToken()).toBe('abc123')
    })
  })

  describe('setToken', () => {
    it('stores the token in localStorage', () => {
      setToken('xyz789')
      expect(localStorage.getItem(TOKEN_KEY)).toBe('xyz789')
    })

    it('overwrites an existing token', () => {
      setToken('old-token')
      setToken('new-token')
      expect(localStorage.getItem(TOKEN_KEY)).toBe('new-token')
    })
  })

  describe('removeToken', () => {
    it('removes the token from localStorage', () => {
      localStorage.setItem(TOKEN_KEY, 'to-be-removed')
      removeToken()
      expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
    })

    it('does nothing if no token exists', () => {
      expect(() => removeToken()).not.toThrow()
      expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
    })
  })

  describe('isAuthenticated', () => {
    it('returns false when no token is stored', () => {
      expect(isAuthenticated()).toBe(false)
    })

    it('returns true when a token is stored', () => {
      localStorage.setItem(TOKEN_KEY, 'some-token')
      expect(isAuthenticated()).toBe(true)
    })

    it('returns false when token is empty string', () => {
      localStorage.setItem(TOKEN_KEY, '')
      expect(isAuthenticated()).toBe(false)
    })
  })
})
