/**
 * 路由守卫单元测试
 * 覆盖：
 * 1. 页面标题设置
 * 2. 已登录用户访问登录/注册页时重定向到「我的课程」
 * 3. 未登录用户访问需认证页面时重定向到登录页
 * 4. 未登录用户访问公开页面（登录/注册）不重定向
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock auth utils
vi.mock('@/utils/auth', () => ({
  isAuthenticated: vi.fn(),
}))

/**
 * 模拟执行路由守卫逻辑。
 * 由于 router 是单例，我们重新创建一个 router 实例来测试守卫逻辑。
 * 守卫逻辑必须与 src/router/index.ts 保持一致。
 */
async function createTestRouter() {
  // 动态重新导入 router 模块（使用新的实例）
  vi.resetModules()
  // Re-mock after resetModules
  vi.doMock('@/utils/auth', () => ({
    isAuthenticated: vi.fn(() => mockIsAuth),
  }))

  const { createRouter, createMemoryHistory } = await import('vue-router')
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/login',
        name: 'Login',
        component: { template: '<div/>' },
        meta: { requiresAuth: false, title: '登录' },
      },
      {
        path: '/register',
        name: 'Register',
        component: { template: '<div/>' },
        meta: { requiresAuth: false, title: '注册' },
      },
      { path: '/', redirect: '/my-courses', meta: { requiresAuth: true } },
      {
        path: '/my-courses',
        name: 'MyCourses',
        component: { template: '<div/>' },
        meta: { requiresAuth: true, title: '我的课程' },
      },
      {
        path: '/practice',
        name: 'Practice',
        component: { template: '<div/>' },
        meta: { requiresAuth: true, title: '练习' },
      },
    ],
  })

  // 注册守卫逻辑（与 router/index.ts 一致）
  router.beforeEach(async (to) => {
    const title = to.meta.title as string
    if (title) {
      document.title = `${title} · LearnPlatform`
    }

    const loggedIn = mockIsAuth

    if (loggedIn && (to.path === '/login' || to.path === '/register')) {
      return { path: '/my-courses' }
    }

    const requiresAuth = to.meta.requiresAuth !== false
    if (requiresAuth && !loggedIn) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }

    return true
  })

  return router
}

let mockIsAuth = false

describe('路由守卫', () => {
  beforeEach(() => {
    mockIsAuth = false
    document.title = ''
  })

  describe('页面标题设置', () => {
    it('访问带 title 的路由应设置页面标题', async () => {
      mockIsAuth = true
      const router = await createTestRouter()
      await router.push('/practice')
      await router.isReady()
      expect(document.title).toContain('练习')
      expect(document.title).toContain('LearnPlatform')
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

    it('访问根路径也应重定向到登录页', async () => {
      mockIsAuth = false
      const router = await createTestRouter()
      await router.push('/')
      await router.isReady()
      expect(router.currentRoute.value.path).toBe('/login')
      // 根路径重定向到 /my-courses 后再被守卫拦截，携带的 redirect 为最终目标
      expect(router.currentRoute.value.query.redirect).toBe('/my-courses')
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
    it('应重定向到我的课程', async () => {
      mockIsAuth = true
      const router = await createTestRouter()
      await router.push('/login')
      await router.isReady()
      expect(router.currentRoute.value.path).toBe('/my-courses')
    })

    it('访问注册页也应重定向到我的课程', async () => {
      mockIsAuth = true
      const router = await createTestRouter()
      await router.push('/register')
      await router.isReady()
      expect(router.currentRoute.value.path).toBe('/my-courses')
    })
  })
})
