import { afterEach, describe, it, expect } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ElButton } from 'element-plus'
import NotFoundView from '@/views/NotFoundView.vue'

enableAutoUnmount(afterEach)
function setup() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: ['/', '/courses'].map((path) => ({ path, component: { template: '<div />' } })),
  })
  return { router, wrapper: mount(NotFoundView, { global: { plugins: [router], components: { ElButton } } }) }
}

describe('NotFoundView', () => {
  it('renders 404 heading', () => {
    expect(setup().wrapper.get('h1').text()).toBe('404')
  })

  it('renders error message', () => {
    expect(setup().wrapper.get('p').text()).toBe('这个地址不存在，或内容已移动。')
  })

  it('uses real home and course links while retaining client-side navigation', async () => {
    const { wrapper, router } = setup()
    await router.isReady()
    expect(wrapper.get('a[href="/"]').text()).toBe('返回首页')
    const courses = wrapper.get('a[href="/courses"]')
    expect(courses.text()).toBe('查看课程')
    await courses.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/courses')
  })
})
