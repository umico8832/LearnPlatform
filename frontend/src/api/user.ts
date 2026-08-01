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

/** 修改个人信息 */
export function updateProfile(data: UpdateProfileRequest) {
  return request.put<unknown, ApiResponse<UserInfo>>('/auth/profile', data)
}

/** 修改密码 */
export function updatePassword(data: UpdatePasswordRequest) {
  return request.put<unknown, ApiResponse<void>>('/auth/password', data)
}
