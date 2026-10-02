import { afterEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { RouteLocationNormalized, RouteLocationNormalizedLoaded } from 'vue-router'
import { learnerScrollBehavior } from '@/router/scrollBehavior'

const router = createRouter({
  history: createMemoryHistory(),
  routes: [{ path: '/:pathMatch(.*)*', component: { template: '<div />' } }],
})
const toRoute = (path: string): RouteLocationNormalized => router.resolve(path) as RouteLocationNormalized
const fromRoute = (path: string): RouteLocationNormalizedLoaded => router.resolve(path) as RouteLocationNormalizedLoaded
afterEach(() => {
  document.body.innerHTML = ''
  vi.unstubAllGlobals()
})

describe('学习端滚动位置', () => {
  it('内容已足够高时立即还原历史位置', () => {
    Object.defineProperty(document.documentElement, 'scrollHeight', { configurable: true, value: 2000 })
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: 900 })
    expect(learnerScrollBehavior(toRoute('/profile#learning'), fromRoute('/courses'), { top: 420, left: 0 })).toEqual({
      top: 420,
      left: 0,
    })
  })
  it('新页面回顶部，同页筛选保留当前位置', () => {
    expect(learnerScrollBehavior(toRoute('/courses'), fromRoute('/my-courses'), null)).toEqual({ top: 0, left: 0 })
    expect(learnerScrollBehavior(toRoute('/courses?category=2'), fromRoute('/courses'), null)).toBe(false)
  })
  it('定位包含特殊字符的锚点，并为固定顶栏留出真实样式中的空间', () => {
    const target = document.createElement('section')
    target.id = '学习:记录'
    target.style.scrollMarginTop = '88px'
    document.body.append(target)
    expect(
      learnerScrollBehavior(toRoute(`/profile#${encodeURIComponent(target.id)}`), fromRoute('/courses'), null),
    ).toEqual({
      el: target,
      top: 88,
    })
  })
  it('不存在或编码错误的锚点不会中断导航', () => {
    expect(learnerScrollBehavior(toRoute('/profile#%broken'), fromRoute('/courses'), null)).toEqual({ top: 0, left: 0 })
  })

  it('waits for main content to grow before restoring a saved position', async () => {
    let notify!: () => void
    class TestResizeObserver {
      constructor(callback: () => void) {
        notify = callback
      }
      disconnect() {}
      observe() {}
    }
    vi.stubGlobal('ResizeObserver', TestResizeObserver)
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: 900 })
    Object.defineProperty(document.documentElement, 'scrollHeight', { configurable: true, value: 900 })
    const main = document.createElement('main')
    Object.defineProperty(main, 'scrollHeight', { configurable: true, value: 900 })
    document.body.append(main)

    const pending = learnerScrollBehavior(toRoute('/my-courses/6'), fromRoute('/my-courses'), { top: 1500, left: 0 })
    expect(pending).toBeInstanceOf(Promise)
    Object.defineProperty(main, 'scrollHeight', { configurable: true, value: 2600 })
    notify()

    await expect(pending).resolves.toEqual({ top: 1500, left: 0 })
  })

  it('cancels a pending restore after a new navigation or user scroll intent', async () => {
    class TestResizeObserver {
      disconnect() {}
      observe() {}
    }
    vi.stubGlobal('ResizeObserver', TestResizeObserver)
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: 900 })
    Object.defineProperty(document.documentElement, 'scrollHeight', { configurable: true, value: 900 })
    document.body.append(document.createElement('main'))

    const first = learnerScrollBehavior(toRoute('/my-courses/6'), fromRoute('/my-courses'), { top: 1500, left: 0 })
    expect(learnerScrollBehavior(toRoute('/courses'), fromRoute('/my-courses/6'), null)).toEqual({ top: 0, left: 0 })
    await expect(first).resolves.toBe(false)

    const second = learnerScrollBehavior(toRoute('/my-courses/6'), fromRoute('/my-courses'), { top: 1500, left: 0 })
    window.dispatchEvent(new Event('wheel'))
    await expect(second).resolves.toBe(false)
  })
})
