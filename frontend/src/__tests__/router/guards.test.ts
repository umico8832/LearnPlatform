import { beforeEach, describe, expect, it, vi } from 'vitest'

const auth = vi.hoisted(() => ({ loggedIn: false }))
vi.mock('@/utils/auth', () => ({ isAuthenticated: () => auth.loggedIn }))
vi.mock('vue-router', async (importOriginal) => {
  const router = await importOriginal<typeof import('vue-router')>()
  return { ...router, createWebHistory: router.createMemoryHistory }
})

async function createTestRouter() {
  vi.resetModules()
  const { default: router } = await import('@/router')
  for (const record of router.getRoutes()) {
    if (record.components) record.components.default = { template: '<div />' }
  }
  return router
}

beforeEach(() => {
  auth.loggedIn = false
  document.title = ''
})

describe('真实学习端路由守卫', () => {
  it('匿名未知路由显示404，已知学习路由仍需登录', async () => {
    const router = await createTestRouter()
    await router.push('/does-not-exist-t11')
    expect(router.currentRoute.value.name).toBe('NotFound')
    expect(document.title).toBe('页面不存在 · LearnPlatform')
    await router.push('/my-courses')
    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/my-courses')
  })

  it('登录后进入练习更新标题，取消离开时保留当前标题', async () => {
    auth.loggedIn = true
    const router = await createTestRouter()
    await router.push('/practice')
    expect(document.title).toBe('练习 · LearnPlatform')
    const removeGuard = router.beforeEach(() => false)
    await router.push('/exams')
    expect(router.currentRoute.value.path).toBe('/practice')
    expect(document.title).toBe('练习 · LearnPlatform')
    removeGuard()
  })

  it('未登录进入学习页会保留完整目的地，并使用登录页标题', async () => {
    const router = await createTestRouter()
    await router.push('/practice?courseId=6#progress')
    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/practice?courseId=6#progress')
    expect(document.title).toBe('登录 · LearnPlatform')
  })

  it.each([false, true])('首页保持公开，登录状态为 %s', async (loggedIn) => {
    auth.loggedIn = loggedIn
    const router = await createTestRouter()
    await router.push('/')
    expect(router.currentRoute.value.path).toBe('/')
  })

  it.each([
    '/login',
    '/register',
    '/forgot-password',
    '/reset-password',
    '/product',
    '/learning',
    '/resources',
    '/roadmap',
    '/about',
  ])('匿名可以访问 %s', async (path) => {
    const router = await createTestRouter()
    await router.push(path)
    expect(router.currentRoute.value.path).toBe(path)
  })

  it.each(['/login', '/register', '/forgot-password', '/reset-password'])(
    '登录后访问 %s 会进入我的课程',
    async (path) => {
      auth.loggedIn = true
      const router = await createTestRouter()
      await router.push(path)
      expect(router.currentRoute.value.path).toBe('/my-courses')
      expect(document.title).toBe('我的课程 · LearnPlatform')
    },
  )
})
