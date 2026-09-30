import { clearPracticeSession } from './practiceSession'

const TOKEN_KEY = 'learn_platform_token'
let sessionVersion = 0
const sessionListeners = new Set<() => void>()

export function getAuthSessionVersion() {
  return sessionVersion
}

export function onAuthSessionChange(listener: () => void) {
  sessionListeners.add(listener)
  return () => sessionListeners.delete(listener)
}

function invalidateSession() {
  sessionVersion++
  sessionListeners.forEach((listener) => listener())
}

/**
 * 获取 Token
 */
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

/**
 * 设置 Token
 */
export function setToken(token: string): void {
  const changed = getToken() !== token
  if (changed) clearPracticeSession()
  localStorage.setItem(TOKEN_KEY, token)
  if (changed) invalidateSession()
}

/**
 * 清除 Token
 */
export function removeToken(): void {
  clearPracticeSession()
  localStorage.removeItem(TOKEN_KEY)
  invalidateSession()
}

/**
 * 是否已登录
 */
export function isAuthenticated(): boolean {
  return !!getToken()
}
