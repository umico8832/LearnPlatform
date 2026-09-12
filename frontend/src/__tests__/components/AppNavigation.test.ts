import { afterEach, describe, expect, it } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import ElementPlus, { ElDropdown } from 'element-plus'
import App from '@/App.vue'
import AppLayout from '@/components/layout/AppLayout.vue'

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
          component: AppLayout,
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
      expect(wrapper.find('.app-layout').exists()).toBe(true)
      wrapper.findComponent(ElDropdown).vm.$emit('command', 'logout')
      await flushPromises()
      expect(router.currentRoute.value.path).toBe('/login')
      await expect.poll(() => wrapper!.find('[data-testid="login"]').exists()).toBe(true)
      expect(wrapper.find('.app-layout').exists()).toBe(false)
      expect(wrapper.find('[data-testid="search-dialog"]').exists()).toBe(false)

      await router.push('/my-courses')
      await flushPromises()
      await expect.poll(() => wrapper!.find('.app-layout').exists()).toBe(true)
    }
  })
})
