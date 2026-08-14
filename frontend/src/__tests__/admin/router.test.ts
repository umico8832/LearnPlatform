import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import { removeToken, setToken } from '@/utils/auth'

const { mockClearLoginInfo, mockFetchUserInfo, state } = vi.hoisted(() => ({
  mockClearLoginInfo: vi.fn(),
  mockFetchUserInfo: vi.fn(),
  state: { userInfo: null as null | { role: 'USER' | 'ADMIN' } },
}))

vi.mock('@/stores/user', () => ({
  useUserStore: () => ({
    userInfo: state.userInfo,
    fetchUserInfo: mockFetchUserInfo,
    clearLoginInfo: mockClearLoginInfo,
  }),
}))

import { createAdminRouter } from '@/admin/router'

describe('独立管理端路由守卫', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    removeToken()
    state.userInfo = null
    mockClearLoginInfo.mockReset()
    mockFetchUserInfo.mockReset()
  })

  it('未登录用户访问批阅页时进入管理端登录页', async () => {
    const router = createAdminRouter(createMemoryHistory('/admin/'))
    await router.push('/subjective-reviews')
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('AdminLogin')
  })

  it('只有管理员可以进入批阅页', async () => {
    setToken('token')
    state.userInfo = { role: 'ADMIN' }
    const router = createAdminRouter(createMemoryHistory('/admin/'))
    await router.push('/subjective-reviews')
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('AdminSubjectiveReviews')
  })

  it('普通学习者不能复用学习端令牌进入管理端', async () => {
    setToken('learner-token')
    state.userInfo = { role: 'USER' }
    const router = createAdminRouter(createMemoryHistory('/admin/'))
    await router.push('/login')
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('AdminLogin')
    expect(mockClearLoginInfo).toHaveBeenCalled()
  })

  it('独立管理端提供总览和试卷管理路由', async () => {
    setToken('token')
    state.userInfo = { role: 'ADMIN' }
    const router = createAdminRouter(createMemoryHistory('/admin/'))
    await router.push('/')
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('AdminDashboard')
    await router.push('/exams')
    expect(router.currentRoute.value.name).toBe('AdminExamManage')
  })

  it('独立管理端提供课程知识点和题目管理路由', async () => {
    setToken('token')
    state.userInfo = { role: 'ADMIN' }
    const router = createAdminRouter(createMemoryHistory('/admin/'))
    await router.push('/courses')
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('AdminCourseManage')
    await router.push('/knowledge-points?courseId=1')
    expect(router.currentRoute.value.name).toBe('AdminKPManage')
    await router.push('/questions')
    expect(router.currentRoute.value.name).toBe('AdminQuestionManage')
  })

  it('独立管理端提供用户投稿和 AI 运营路由', async () => {
    setToken('token')
    state.userInfo = { role: 'ADMIN' }
    const router = createAdminRouter(createMemoryHistory('/admin/'))
    for (const [path, name] of [
      ['/users', 'AdminUserManage'],
      ['/submissions', 'AdminSubmissionManage'],
      ['/ai-usage', 'AdminAiUsage'],
    ] as const) {
      await router.push(path)
      await router.isReady()
      expect(router.currentRoute.value.name).toBe(name)
    }
  })
})
