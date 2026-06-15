import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { UserInfo } from '@/types/user'

/** 修改个人信息请求 */
export interface UpdateProfileRequest {
  nickname: string
}

/** 修改密码请求 */
export interface UpdatePasswordRequest {
  oldPassword: string
  newPassword: string
}

/** 验证码响应 */
export interface CaptchaData {
  captchaId: string
  image: string
}

/** 修改个人信息 */
export function updateProfile(data: UpdateProfileRequest) {
  return request.put<ApiResponse<UserInfo>>('/auth/profile', data)
}

/** 修改密码 */
export function updatePassword(data: UpdatePasswordRequest) {
  return request.put<ApiResponse<void>>('/auth/password', data)
}

/** 获取验证码 */
export function getCaptcha() {
  return request.get<ApiResponse<CaptchaData>>('/auth/captcha')
}