import { afterEach, describe, it, expect } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import NotFoundView from '@/views/NotFoundView.vue'

enableAutoUnmount(afterEach)
function setup() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: ['/', '/courses'].map((path) => ({ path, component: { template: '<div />' } })),
  })
  return { router, wrapper: mount(NotFoundView, { global: { plugins: [router] } }) }
}

describe('NotFoundView', () => {
  it('names the missing page in the heading and keeps the status code visible', () => {
    const { wrapper } = setup()
    expect(wrapper.get('h1').text()).toBe('这个地址不存在')
    expect(wrapper.get('.code').text()).toBe('404')
  })

  it('offers the home page and courses to a visitor who is not signed in', () => {
    const { wrapper } = setup()
    expect(wrapper.find('a[href="/my-courses"]').exists()).toBe(false)
    expect(wrapper.get('.lt-link-button[data-variant="primary"]').text()).toBe('返回首页')
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
