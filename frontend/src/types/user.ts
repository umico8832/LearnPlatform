/**
 * 用户信息
 */
export interface UserInfo {
  id: number
  username: string
  email?: string
  nickname: string | null
  avatar: string | null
  role: 'USER' | 'ADMIN'
  createTime?: string
}

/**
 * 登录请求
 */
export interface LoginRequest {
  account: string
  password: string
  turnstileToken: string
}

/**
 * 注册请求
 */
export interface RegisterRequest {
  username: string
  email: string
  password: string
  verificationTicket: string
  nickname?: string
}

/**
 * 登录响应
 */
export interface LoginResponse {
  token: string
  tokenType: string
  expiresIn: number
  user: UserInfo
}
