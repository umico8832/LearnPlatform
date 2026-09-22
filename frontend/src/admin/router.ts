import { createRouter, createWebHistory } from 'vue-router'
import type { RouterHistory } from 'vue-router'
import { isAuthenticated } from '@/utils/auth'
import { useUserStore } from '@/stores/user'
import { adminRoutes } from './routes'

export function createAdminRouter(history: RouterHistory = createWebHistory(import.meta.env.BASE_URL)) {
  const router = createRouter({ history, routes: adminRoutes })

  router.beforeEach(async (to) => {
    if (to.meta.title) document.title = `${String(to.meta.title)} - LearnPlatform 管理系统`
    const loggedIn = isAuthenticated()
    if (to.name === 'AdminLogin') {
      if (!loggedIn) return true
      const userStore = useUserStore()
      if (!userStore.userInfo) await userStore.fetchUserInfo()
      if (userStore.userInfo?.role === 'ADMIN') return { name: 'AdminDashboard' }
      userStore.clearLoginInfo()
      return true
    }
    if (!loggedIn) return { name: 'AdminLogin', query: { redirect: to.fullPath } }
    const userStore = useUserStore()
    if (!userStore.userInfo) await userStore.fetchUserInfo()
    if (userStore.userInfo?.role !== 'ADMIN') {
      userStore.clearLoginInfo()
      return { name: 'AdminLogin' }
    }
    return true
  })

  return router
}

export default createAdminRouter()
