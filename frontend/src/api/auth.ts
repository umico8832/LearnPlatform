import axios from 'axios'
import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { LoginRequest, LoginResponse, RegisterRequest, UserInfo } from '@/types/user'

export type AuthRequestOptions = { errorDisplay?: 'inline' }

export function login(data: LoginRequest, options?: AuthRequestOptions) {
  if (options) return request.post<unknown, ApiResponse<LoginResponse>>('/auth/login', data, options)
  return request.post<unknown, ApiResponse<LoginResponse>>('/auth/login', data)
}
export function sendRegisterCode(email: string, turnstileToken: string, options?: AuthRequestOptions) {
  if (options)
    return request.post<unknown, ApiResponse<void>>('/auth/email/register-code', { email, turnstileToken }, options)
  return request.post<unknown, ApiResponse<void>>('/auth/email/register-code', { email, turnstileToken })
}
export function verifyRegisterCode(email: string, code: string, options?: AuthRequestOptions) {
  if (options)
    return request.post<unknown, ApiResponse<{ verificationTicket: string; expiresIn: number }>>(
      '/auth/email/verify-register-code',
      { email, code },
      options,
    )
  return request.post<unknown, ApiResponse<{ verificationTicket: string; expiresIn: number }>>(
    '/auth/email/verify-register-code',
    { email, code },
  )
}
export function register(data: RegisterRequest, options?: AuthRequestOptions) {
  if (options) return request.post<unknown, ApiResponse<UserInfo>>('/auth/register', data, options)
  return request.post<unknown, ApiResponse<UserInfo>>('/auth/register', data)
}
export function forgotPassword(email: string, turnstileToken: string, options?: AuthRequestOptions) {
  if (options)
    return request.post<unknown, ApiResponse<void>>('/auth/password/forgot', { email, turnstileToken }, options)
  return request.post<unknown, ApiResponse<void>>('/auth/password/forgot', { email, turnstileToken })
}
export function validateResetToken(token: string, options?: AuthRequestOptions) {
  if (options)
    return request.get<unknown, ApiResponse<string>>('/auth/password/reset/validate', { params: { token }, ...options })
  return request.get<unknown, ApiResponse<string>>('/auth/password/reset/validate', { params: { token } })
}
export function resetPassword(token: string, password: string, options?: AuthRequestOptions) {
  if (options) return request.post<unknown, ApiResponse<void>>('/auth/password/reset', { token, password }, options)
  return request.post<unknown, ApiResponse<void>>('/auth/password/reset', { token, password })
}
export async function getOAuthProviders() {
  // 可选的匿名入口查询失败不能触发会话过期跳转。
  const response = await axios.get<ApiResponse<{ google: boolean }>>('/auth/oauth/providers', {
    baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
    timeout: 15000,
  })
  if (response.data.code !== 0) {
    throw new Error(response.data.message || '第三方登录暂不可用')
  }
  return response.data
}
export function exchangeOAuthTicket(ticket: string, options?: AuthRequestOptions) {
  if (options) return request.post<unknown, ApiResponse<LoginResponse>>('/auth/oauth/exchange', { ticket }, options)
  return request.post<unknown, ApiResponse<LoginResponse>>('/auth/oauth/exchange', { ticket })
}
