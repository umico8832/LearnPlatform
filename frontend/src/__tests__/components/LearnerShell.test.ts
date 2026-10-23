import { afterEach, describe, expect, it } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import ElementPlus from 'element-plus'
import App from '@/App.vue'
import LearnerShell from '@/components/layout/LearnerShell.vue'

let wrapper: VueWrapper | undefined

afterEach(() => {
  wrapper?.unmount()
  document.body.innerHTML = ''
})

describe('application route transitions', () => {
  it('renders login after logout and supports returning to the application again', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/login', component: { template: '<main data-testid="login">登录</main>' } },
        {
          path: '/',
          component: LearnerShell,
          children: [
            { path: 'my-courses', component: { template: '<h1>我的课程</h1>' } },
            { path: 'courses', component: { template: '<h1>课程库</h1>' } },
          ],
        },
      ],
    })
    await router.push('/my-courses')
    await router.isReady()
    wrapper = mount(App, {
      attachTo: document.body,
      global: {
        plugins: [createPinia(), router, ElementPlus],
        stubs: {
          transition: false,
          'transition-group': false,
          GlobalSearchDialog: { template: '<div data-testid="search-dialog" />' },
        },
      },
    })
    await flushPromises()

    for (let cycle = 0; cycle < 2; cycle++) {
      expect(wrapper.find('.learner-shell').exists()).toBe(true)
      await wrapper.get('button[aria-label="用户菜单"]').trigger('click')
      const logout = wrapper.findAll('.entry').find((entry) => entry.text() === '退出登录')
      await logout!.trigger('click')
      await flushPromises()
      expect(router.currentRoute.value.path).toBe('/login')
      await expect.poll(() => wrapper!.find('[data-testid="login"]').exists()).toBe(true)
      expect(wrapper.find('.learner-shell').exists()).toBe(false)
      expect(wrapper.find('[data-testid="search-dialog"]').exists()).toBe(false)

      await router.push('/my-courses')
      await flushPromises()
      await expect.poll(() => wrapper!.find('.learner-shell').exists()).toBe(true)
    }
  })
})

it('keeps community and course navigation highlights independent', async () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/my-courses', component: { template: '<h1>我的课程</h1>' } },
      { path: '/courses', component: { template: '<h1>课程库</h1>' } },
      { path: '/community/:id?', component: { template: '<h1>社区</h1>' } },
    ],
  })
  await router.push('/community/7')
  await router.isReady()
  wrapper = mount(LearnerShell, {
    global: { plugins: [createPinia(), router, ElementPlus], stubs: { GlobalSearchDialog: true } },
  })
  await flushPromises()
  expect(wrapper.get('a[href="/community"]').classes()).toContain('is-active')
  expect(wrapper.get('a[href="/community"]').attributes('aria-current')).toBe('page')
  expect(wrapper.get('a[href="/courses"]').classes()).not.toContain('is-active')
  expect(wrapper.get('a[href="/courses"]').attributes('aria-current')).toBeUndefined()
  await router.push('/courses')
  await flushPromises()
  expect(wrapper.get('a[href="/community"]').classes()).not.toContain('is-active')
  expect(wrapper.get('a[href="/courses"]').classes()).toContain('is-active')
  expect(wrapper.get('a[href="/courses"]').attributes('aria-current')).toBe('page')
})

it('moves focus to main on the first path navigation but not on a query-only update', async () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/my-courses', component: { template: '<h1>我的课程</h1>' } },
      { path: '/courses', component: { template: '<h1>课程库</h1>' } },
      { path: '/community', component: { template: '<h1>社区</h1>' } },
    ],
  })
  await router.push('/my-courses')
  await router.isReady()
  wrapper = mount(LearnerShell, {
    attachTo: document.body,
    global: { plugins: [createPinia(), router, ElementPlus], stubs: { GlobalSearchDialog: true } },
  })
  await flushPromises()

  await router.push('/courses')
  await flushPromises()
  expect(document.activeElement).toBe(wrapper.get('main').element)

  const outside = document.createElement('button')
  document.body.append(outside)
  outside.focus()
  await router.push({ path: '/courses', query: { filter: 'active' } })
  await flushPromises()
  expect(document.activeElement).toBe(outside)
})

it('treats wrong questions as part of review and leaves no section current on the profile', async () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/review', component: { template: '<h1>复习</h1>' } },
      { path: '/wrong-questions', component: { template: '<h1>错题</h1>' } },
      { path: '/profile', component: { template: '<h1>个人中心</h1>' } },
    ],
  })
  await router.push('/wrong-questions')
  await router.isReady()
  wrapper = mount(LearnerShell, {
    global: { plugins: [createPinia(), router, ElementPlus], stubs: { GlobalSearchDialog: true } },
  })
  await flushPromises()
  expect(wrapper.get('a[href="/review"]').attributes('aria-current')).toBe('page')
  await router.push('/profile')
  await flushPromises()
  expect(wrapper.find('nav[aria-label="主导航"] [aria-current="page"]').exists()).toBe(false)
})

it('closes the account menu with Escape and returns focus to the avatar', async () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/my-courses', component: { template: '<h1>我的课程</h1>' } }],
  })
  await router.push('/my-courses')
  await router.isReady()
  wrapper = mount(LearnerShell, {
    attachTo: document.body,
    global: { plugins: [createPinia(), router, ElementPlus], stubs: { GlobalSearchDialog: true } },
  })
  await flushPromises()
  const avatar = wrapper.get('button[aria-label="用户菜单"]')
  await avatar.trigger('click')
  expect(avatar.attributes('aria-expanded')).toBe('true')
  document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
  await flushPromises()
  expect(avatar.attributes('aria-expanded')).toBe('false')
  expect(document.activeElement).toBe(avatar.element)
})
