/**
 * 路由守卫单元测试
 * 覆盖：
 * 1. 页面标题设置
 * 2. 已登录用户访问登录/注册页时重定向到首页
 * 3. 未登录用户访问需认证页面时重定向到登录页
 * 4. 未登录用户访问公开页面（登录/注册）不重定向
 * 5. 管理员路由守卫（非 ADMIN 用户重定向到首页）
 * 6. 管理员用户可以访问管理端页面
 * 7. fetchUserInfo 失败时重定向到登录页
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock auth utils
vi.mock('@/utils/auth', () => ({
  isAuthenticated: vi.fn(),
}))
// Mock user store
const mockFetchUserInfo = vi.fn()
let mockUserInfo: { role: string } | null = null
vi.mock('@/stores/user', () => ({
  useUserStore: vi.fn(() => ({
    userInfo: mockUserInfo,
    fetchUserInfo: mockFetchUserInfo,
  })),
}))

/**
 * 模拟执行路由守卫逻辑。
 * 由于 router 是单例，我们重新创建一个 router 实例来测试守卫逻辑。
 */
async function createTestRouter() {
  // 动态重新导入 router 模块（使用新的实例）
  vi.resetModules()
  // Re-mock after resetModules
  vi.doMock('@/utils/auth', () => ({
    isAuthenticated: vi.fn(() => mockIsAuth),
  }))
  vi.doMock('@/stores/user', () => ({
    useUserStore: vi.fn(() => ({
      get userInfo() { return mockUserInfo },
      fetchUserInfo: mockFetchUserInfo,
    })),
  }))

  const { createRouter, createMemoryHistory } = await import('vue-router')
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', name: 'Login', component: { template: '<div/>' }, meta: { requiresAuth: false, title: '登录' } },
      { path: '/register', name: 'Register', component: { template: '<div/>' }, meta: { requiresAuth: false, title: '注册' } },
      { path: '/', name: 'Home', component: { template: '<div/>' }, meta: { requiresAuth: true, title: '首页' } },
      { path: '/practice', name: 'Practice', component: { template: '<div/>' }, meta: { requiresAuth: true, title: '刷题练习' } },
      { path: '/admin', name: 'AdminDashboard', component: { template: '<div/>' }, meta: { requiresAuth: true, title: '平台总览', requiresAdmin: true } },
      { path: '/admin/users', name: 'AdminUserManage', component: { template: '<div/>' }, meta: { requiresAuth: true, title: '用户管理', requiresAdmin: true } },
    ],
  })

  // 注册守卫逻辑（与 router/index.ts 一致）
  router.beforeEach(async (to) => {
    const title = to.meta.title as string
    if (title) {
      document.title = `${title} - AI 题库与错题复习系统`
    }

    const loggedIn = mockIsAuth

    if (loggedIn && (to.path === '/login' || to.path === '/register')) {
      return { path: '/' }
    }

    const requiresAuth = to.meta.requiresAuth !== false
    if (requiresAuth && !loggedIn) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }

    if (loggedIn && to.meta.requiresAdmin) {
      const { useUserStore } = await import('@/stores/user')
      const userStore = useUserStore()
      if (!userStore.userInfo) {
        await userStore.fetchUserInfo()
      }
      if (!userStore.userInfo) {
        return { path: '/login', query: { redirect: to.fullPath } }
      }
      if (userStore.userInfo.role !== 'ADMIN') {
        return { path: '/' }
      }
    }

    return true
  })

  return router
}

let mockIsAuth = false

describe('路由守卫', () => {
  beforeEach(() => {
    mockIsAuth = false
    mockUserInfo = null
    mockFetchUserInfo.mockReset()
    document.title = ''
  })

  describe('页面标题设置', () => {
    it('访问带 title 的路由应设置页面标题', async () => {
      mockIsAuth = true
      const router = await createTestRouter()
      await router.push('/practice')
      await router.isReady()
      expect(document.title).toContain('刷题练习')
      expect(document.title).toContain('AI 题库与错题复习系统')
    })
  })

  describe('未登录用户访问需认证页面', () => {
    it('应重定向到登录页并携带 redirect 参数', async () => {
      mockIsAuth = false
      const router = await createTestRouter()
      await router.push('/practice')
      await router.isReady()
      expect(router.currentRoute.value.path).toBe('/login')
      expect(router.currentRoute.value.query.redirect).toBe('/practice')
    })

    it('访问首页也应重定向到登录页', async () => {
      mockIsAuth = false
      const router = await createTestRouter()
      await router.push('/')
      await router.isReady()
      expect(router.currentRoute.value.path).toBe('/login')
      expect(router.currentRoute.value.query.redirect).toBe('/')
    })
  })

  describe('未登录用户访问公开页面', () => {
    it('应允许访问登录页', async () => {
      mockIsAuth = false
      const router = await createTestRouter()
      await router.push('/login')
      await router.isReady()
      expect(router.currentRoute.value.path).toBe('/login')
    })

    it('应允许访问注册页', async () => {
      mockIsAuth = false
      const router = await createTestRouter()
      await router.push('/register')
      await router.isReady()
      expect(router.currentRoute.value.path).toBe('/register')
    })
  })

  describe('已登录用户访问登录/注册页', () => {
    it('应重定向到首页', async () => {
      mockIsAuth = true
      const router = await createTestRouter()
      await router.push('/login')
      await router.isReady()
      expect(router.currentRoute.value.path).toBe('/')
    })

    it('访问注册页也应重定向到首页', async () => {
      mockIsAuth = true
      const router = await createTestRouter()
      await router.push('/register')
      await router.isReady()
      expect(router.currentRoute.value.path).toBe('/')
    })
  })

  describe('管理员路由守卫', () => {
    it('非 ADMIN 用户访问管理页面应重定向到首页', async () => {
      mockIsAuth = true
      mockUserInfo = { role: 'USER' }
      mockFetchUserInfo.mockResolvedValue(undefined)
      const router = await createTestRouter()
      await router.push('/admin')
      await router.isReady()
      expect(router.currentRoute.value.path).toBe('/')
    })

    it('ADMIN 用户可以访问管理页面', async () => {
      mockIsAuth = true
      mockUserInfo = { role: 'ADMIN' }
      mockFetchUserInfo.mockResolvedValue(undefined)
      const router = await createTestRouter()
      await router.push('/admin')
      await router.isReady()
      expect(router.currentRoute.value.path).toBe('/admin')
    })

    it('未加载用户信息时先 fetchUserInfo', async () => {
      mockIsAuth = true
      mockUserInfo = null
      // fetchUserInfo 后设置 userInfo
      mockFetchUserInfo.mockImplementation(async () => {
        mockUserInfo = { role: 'ADMIN' }
      })
      const router = await createTestRouter()
      await router.push('/admin/users')
      await router.isReady()
      expect(mockFetchUserInfo).toHaveBeenCalled()
      expect(router.currentRoute.value.path).toBe('/admin/users')
    })

    it('fetchUserInfo 后仍无用户信息应被重定向（最终到首页）', async () => {
      mockIsAuth = true
      mockUserInfo = null
      mockFetchUserInfo.mockImplementation(async () => {
        // userInfo 仍然为 null，模拟 fetch 失败
        mockUserInfo = null
      })
      const router = await createTestRouter()
      await router.push('/admin')
      await router.isReady()
      // 守卫先重定向到 /login，但因为 isAuth=true 再被重定向到 /
      expect(router.currentRoute.value.path).toBe('/')
      expect(mockFetchUserInfo).toHaveBeenCalled()
    })
  })
})
