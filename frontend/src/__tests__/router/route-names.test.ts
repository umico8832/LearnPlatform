/**
 * 路由名解析测试：课程空间学习工具入口使用已注册的 router name 跳转，
 * 这里直接解析真实路由表，保证「route name 写错」时测试立即失败。
 */
import { describe, expect, it } from 'vitest'
import router from '@/router'

const TOOL_ROUTE_NAMES = ['Practice', 'Review', 'WrongQuestions', 'ExamList', 'QuestionList'] as const

describe('课程空间学习工具路由名', () => {
  it.each(TOOL_ROUTE_NAMES)('%s 是已注册的命名路由', (name) => {
    const resolved = router.resolve({ name })
    expect(resolved.name).toBe(name)
    expect(resolved.matched.length).toBeGreaterThan(0)
    expect(resolved.meta.requiresAuth).not.toBe(false)
  })
})
