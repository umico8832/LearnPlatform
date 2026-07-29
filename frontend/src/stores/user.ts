import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { UserInfo } from '@/types/user'
import { getToken, setToken, removeToken } from '@/utils/auth'
import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(getToken())
  const userInfo = ref<UserInfo | null>(null)

  /**
   * 设置登录信息
   */
  function setLoginInfo(tokenStr: string, user: UserInfo) {
    token.value = tokenStr
    userInfo.value = user
    setToken(tokenStr)
  }

  /**
   * 清除登录信息（退出登录）
   */
  function clearLoginInfo() {
    token.value = null
    userInfo.value = null
    removeToken()
  }

  /**
   * 获取当前用户信息（页面刷新后调用）
   */
  async function fetchUserInfo() {
    if (!token.value) return
    try {
      const res = await request.get<unknown, ApiResponse<UserInfo>>('/auth/me')
      userInfo.value = res.data
    } catch {
      clearLoginInfo()
    }
  }

  /**
   * 是否已登录
   */
  const isLoggedIn = () => !!token.value

  return {
    token,
    userInfo,
    setLoginInfo,
    clearLoginInfo,
    fetchUserInfo,
    isLoggedIn,
  }
})
