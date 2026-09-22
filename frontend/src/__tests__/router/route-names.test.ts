/**
 * 路由名解析测试：课程空间学习工具入口使用已注册的 router name 跳转，
 * 这里直接解析真实路由表，保证「route name 写错」时测试立即失败。
 */
import { describe, expect, it } from 'vitest'
import router, { createLearnerRoutes } from '@/router'

const TOOL_ROUTE_NAMES = ['Practice', 'Review', 'WrongQuestions', 'ExamList', 'QuestionList'] as const
const PUBLIC_PLACEHOLDER_ROUTES = [
  ['Product', '/product'],
  ['Learning', '/learning'],
  ['Resources', '/resources'],
  ['Roadmap', '/roadmap'],
  ['About', '/about'],
] as const

describe('课程空间学习工具路由名', () => {
  it('开发页仅在开发路由表注册，生产路由表只包含业务页面', () => {
    expect(createLearnerRoutes(true).map((route) => route.name)).toEqual(
      expect.arrayContaining(['AuthPreview', 'DevPages']),
    )
    const productionNames = createLearnerRoutes(false).map((route) => route.name)
    expect(productionNames).not.toContain('AuthPreview')
    expect(productionNames).not.toContain('DevPages')
  })

  it('Home 是公开的首页路由', () => {
    const resolved = router.resolve({ name: 'Home' })
    expect(resolved.path).toBe('/')
    expect(resolved.meta.requiresAuth).toBe(false)
  })

  it.each(TOOL_ROUTE_NAMES)('%s 是已注册的命名路由', (name) => {
    const resolved = router.resolve({ name })
    expect(resolved.name).toBe(name)
    expect(resolved.matched.length).toBeGreaterThan(0)
    expect(resolved.meta.requiresAuth).not.toBe(false)
  })

  it.each(PUBLIC_PLACEHOLDER_ROUTES)('%s 是公开的占位页面路由', (name, path) => {
    const resolved = router.resolve({ name })
    expect(resolved.path).toBe(path)
    expect(resolved.meta.requiresAuth).toBe(false)
  })

  it('课程库 /courses 仍是需要登录的原有路由', () => {
    const resolved = router.resolve({ name: 'CourseList' })
    expect(resolved.path).toBe('/courses')
    expect(resolved.meta.requiresAuth).not.toBe(false)
  })
})
