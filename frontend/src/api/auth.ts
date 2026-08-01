import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { LoginRequest, LoginResponse, RegisterRequest, UserInfo } from '@/types/user'

export function login(data: LoginRequest) {
  return request.post<unknown, ApiResponse<LoginResponse>>('/auth/login', data)
}
export function sendRegisterCode(email: string, turnstileToken: string) {
  return request.post<unknown, ApiResponse<void>>('/auth/email/register-code', { email, turnstileToken })
}
export function verifyRegisterCode(email: string, code: string) {
  return request.post<unknown, ApiResponse<{ verificationTicket: string; expiresIn: number }>>('/auth/email/verify-register-code', { email, code })
}
export function register(data: RegisterRequest) {
  return request.post<unknown, ApiResponse<UserInfo>>('/auth/register', data)
}
export function forgotPassword(email: string, turnstileToken: string) {
  return request.post<unknown, ApiResponse<void>>('/auth/password/forgot', { email, turnstileToken })
}
export function validateResetToken(token: string) {
  return request.get<unknown, ApiResponse<string>>('/auth/password/reset/validate', { params: { token } })
}
export function resetPassword(token: string, password: string) {
  return request.post<unknown, ApiResponse<void>>('/auth/password/reset', { token, password })
}
