import request from '@/utils/request'

/** 用户 VO */
export interface AdminUserVO {
  id: number
  username: string
  nickname: string
  avatar: string | null
  role: string
  status: number
  /** null 表示继承全局 AI 日配额，0 表示不限次数 */
  aiDailyQuota?: number | null
  createTime: string
}

/** 用户列表响应 */
export interface AdminUserListResponse {
  records: AdminUserVO[]
  total: number
  current: number
  size: number
}

/** 创建用户请求 */
export interface AdminCreateUserRequest {
  username: string
  password: string
  nickname?: string
  role?: string
}

/** 用户统计 */
export interface AdminUserStats {
  total: number
  active: number
  disabled: number
  admins: number
}

/** 获取用户分页列表 */
export function getAdminUserList(params: {
  page?: number
  size?: number
  keyword?: string
  role?: string
  status?: number | string
}) {
  return request.get<AdminUserListResponse>('/admin/users', { params })
}

/** 管理员创建用户 */
export function createAdminUser(data: AdminCreateUserRequest) {
  return request.post<AdminUserVO>('/admin/users', data)
}

/** 修改用户角色 */
export function updateUserRole(id: number, role: string) {
  return request.put(`/admin/users/${id}/role`, { role })
}

/** 启用/禁用用户 */
export function updateUserStatus(id: number, status: number) {
  return request.put(`/admin/users/${id}/status`, { status })
}

/** 设置用户级 AI 日配额；null 表示恢复继承全局配置，0 表示不限次数 */
export function updateUserAiDailyQuota(id: number, dailyQuota: number | null) {
  return request.put(`/admin/users/${id}/ai-daily-quota`, { dailyQuota })
}

/** 重置用户密码 */
export function resetUserPassword(id: number, newPassword: string) {
  return request.put(`/admin/users/${id}/reset-password`, { newPassword })
}

/** 删除用户 */
export function deleteAdminUser(id: number) {
  return request.delete(`/admin/users/${id}`)
}

/** 获取用户统计 */
export function getAdminUserStats() {
  return request.get<AdminUserStats>('/admin/users/stats')
}
