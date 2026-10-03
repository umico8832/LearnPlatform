import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import AdminLayout from '@/admin/AdminLayout.vue'
vi.mock('@/stores/user', () => ({
  useUserStore: () => ({ userInfo: { username: 'audit-admin', nickname: '审核员' }, clearLoginInfo: vi.fn() }),
}))
const wrappers: ReturnType<typeof mount>[] = []
afterEach(() => wrappers.splice(0).forEach((wrapper) => wrapper.unmount()))
it('groups management navigation, keeps the course context and moves focus after navigation', async () => {
  const router = createRouter({
    history: createMemoryHistory('/admin/'),
    routes: [{ path: '/:pathMatch(.*)*', component: { template: '<h1>内容页面</h1>' } }],
  })
  await router.push('/')
  const wrapper = mount(AdminLayout, {
    attachTo: document.body,
    global: { plugins: [router], stubs: { 'el-button': { template: '<button><slot/></button>' } } },
  })
  wrappers.push(wrapper)
  expect(wrapper.get('a.admin-skip-link').attributes('href')).toBe('#admin-main')
  expect(wrapper.findAll('.admin-nav-group')).toHaveLength(3)
  expect(wrapper.get('.admin-account').text()).toContain('审核员')
  await router.push('/knowledge-points?courseId=1')
  await flushPromises()
  expect(document.activeElement?.id).toBe('admin-main')
  expect(wrapper.get('.admin-overview-link').classes()).not.toContain('is-active')
  expect(wrapper.get('a[href="/admin/courses"]').attributes('aria-current')).toBe('page')
})
