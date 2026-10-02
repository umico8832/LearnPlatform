import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, shallowMount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useUserStore } from '@/stores/user'
import CommunityView from '@/views/community/CommunityView.vue'
import CommunityComposer from '@/components/community/CommunityComposer.vue'
import { setToken } from '@/utils/auth'

enableAutoUnmount(afterEach)
const { categories, posts } = vi.hoisted(() => ({ categories: vi.fn(), posts: vi.fn() }))
vi.mock('@/api/community', () => ({
  getCommunityCategories: categories,
  getCommunityPosts: posts,
  communityTypes: { TOPIC: '讨论' },
  communityStatuses: { APPROVED: '已公开' },
}))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
function mountView() {
  return shallowMount(CommunityView, {
    global: {
      renderStubDefaultSlot: true,
      stubs: {
        LpPageHeader: { template: '<header><slot name="actions"/></header>' },
        'el-button': { props: ['disabled'], template: '<button :disabled="disabled"><slot/></button>' },
        'el-alert': { props: ['title'], template: '<div role="alert">{{ title }}<slot/></div>' },
      },
    },
  })
}
describe('community categories recovery', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    useUserStore().setLoginInfo('test-token', {
      id: 7,
      username: 'learner',
      nickname: 'Learner',
      avatar: null,
      role: 'USER',
    })
    vi.resetAllMocks()
    categories.mockResolvedValue({ data: [{ id: 1, name: '考试分类', kind: 'EXAM' }] })
    posts.mockResolvedValue({ data: { total: 0, records: [] } })
  })
  it('keeps category failures visible while the independent feed loads and retries only categories', async () => {
    categories.mockRejectedValueOnce(new Error('分类暂时无法读取'))
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('分类暂时无法读取')
    expect(
      wrapper
        .findAll('button')
        .find((b) => b.text() === '发起讨论')!
        .attributes('disabled'),
    ).toBeDefined()
    expect(posts).toHaveBeenCalledOnce()
    await wrapper.get('[data-testid="categories-retry"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).not.toContain('分类暂时无法读取')
    expect(posts).toHaveBeenCalledOnce()
  })
  it('does not delay feed loading until a slow category read completes', async () => {
    categories.mockReturnValue(new Promise(() => undefined))
    mountView()
    await flushPromises()
    expect(posts).toHaveBeenCalledOnce()
  })
  it('closes the previous accounts composer when the auth session changes', async () => {
    const wrapper = mountView()
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '发起讨论')!
      .trigger('click')
    expect(wrapper.findComponent(CommunityComposer).exists()).toBe(true)
    setToken('other-test-session')
    await flushPromises()
    expect(wrapper.findComponent(CommunityComposer).exists()).toBe(false)
  })
})
