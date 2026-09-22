import { describe, expect, it, vi } from 'vitest'
import type { RouteRecordRaw } from 'vue-router'

import { adminRoutes } from '@/admin/routes'
import { learnerRoutes } from '@/router/routes'
import { createPageCatalog, pages } from '@/views/dev/pageCatalog'

function namedBusinessRoutes(records: readonly RouteRecordRaw[]): string[] {
  return records.flatMap((record) => [
    ...(typeof record.name === 'string' && !record.redirect ? [record.name] : []),
    ...(record.children ? namedBusinessRoutes(record.children) : []),
  ])
}

describe('开发页面目录', () => {
  it('从两套业务路由列出所有命名页面，但不把布局和跳转项列为页面', () => {
    expect(pages.map((page) => page.name).sort()).toEqual(
      [...namedBusinessRoutes(learnerRoutes), ...namedBusinessRoutes(adminRoutes)].sort(),
    )
    expect(pages).not.toEqual(expect.arrayContaining([expect.objectContaining({ name: undefined })]))
    expect(pages).not.toEqual(expect.arrayContaining([expect.objectContaining({ name: 'AuthPreview' })]))
  })

  it('继承学习端布局的登录要求，保留动态参数和隐藏页面', () => {
    expect(pages.find((page) => page.name === 'CourseDetail')).toMatchObject({
      app: 'learner',
      access: 'login',
      params: ['id'],
    })
    expect(pages.find((page) => page.name === 'LearningDiagnosis')).toMatchObject({ hidden: true })
    expect(pages.find((page) => page.name === 'NotFound')).toMatchObject({ params: [] })
    expect(pages.find((page) => page.name === 'PracticeSession')).toMatchObject({ entryPath: '/practice' })
  })

  it('保留管理端的独立路径和管理员访问要求', () => {
    expect(pages.find((page) => page.name === 'AdminLogin')).toMatchObject({
      app: 'admin',
      path: '/login',
      access: 'public',
    })
    expect(pages.find((page) => page.name === 'AdminQuestionManage')).toMatchObject({
      app: 'admin',
      path: '/questions',
      access: 'admin',
    })
  })

  it('不会执行路由组件加载器来生成页面目录', () => {
    const component = vi.fn(() => Promise.resolve({ default: {} }))
    const catalog = createPageCatalog([{ path: '/catalog-test', name: 'CatalogNoLoad', component }], [])
    expect(component).not.toHaveBeenCalled()
    expect(catalog).toEqual([expect.objectContaining({ name: 'CatalogNoLoad' })])
  })
})
