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
vi.mock('@/api/course', () => ({ getAllCourses: vi.fn().mockResolvedValue({ data: [] }) }))
vi.mock('@/api/knowledgePoint', () => ({ getKnowledgeTree: vi.fn() }))
function mountView() {
  return shallowMount(CommunityView, {
    global: {
      renderStubDefaultSlot: true,
      stubs: {
        LtPageHeader: false,
        RouterLink: { template: '<a><slot/></a>' },
        LtButton: {
          props: ['disabled', 'type'],
          emits: ['click'],
          template: '<button :disabled="disabled" :type="type" @click="$emit(\'click\')"><slot/></button>',
        },
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
  it('shows a named default in each community filter', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(
      wrapper.findAll('select').map((select) => (select.element as HTMLSelectElement).selectedOptions[0]?.textContent),
    ).toEqual(['全部内容', '全部考试', '全部科目', '全部学校'])
  })
  it('keeps category failures visible while the independent feed loads and retries only categories', async () => {
    categories.mockRejectedValueOnce(new Error('分类暂时无法读取'))
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('分类暂时无法读取')
    expect(
      wrapper
        .findAll('button')
        .find((b) => b.text() === '发起讨论')
        ?.attributes('disabled'),
    ).toBeDefined()
    expect(posts).toHaveBeenCalledOnce()
    await wrapper.get('[role="alert"] button').trigger('click')
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
  it('clears a keyword through the explicit control and refreshes the feed', async () => {
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('#community-query').setValue('栈')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '清空搜索')!
      .trigger('click')
    await flushPromises()
    expect(posts).toHaveBeenCalledTimes(2)
    expect(posts).toHaveBeenLastCalledWith(expect.not.objectContaining({ keyword: expect.anything() }), false, {
      errorDisplay: 'inline',
    })
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

describe('CommunityComposer close guard', () => {
  it('keeps the composer sheet open when the learner continues editing a dirty draft', async () => {
    const wrapper = shallowMount(CommunityComposer, {
      props: { initialType: 'TOPIC', categories: [] },
      global: {
        stubs: {
          LtSheet: { template: '<section><slot /></section>' },
          LtButton: { template: '<button><slot /></button>' },
        },
      },
    })
    expect((wrapper.findAll('select')[3].element as HTMLSelectElement).selectedOptions[0]?.textContent).toBe('不限学校')
    const vm = wrapper.vm as unknown as {
      draft: { title: string }
      sheetOpen: boolean
      open: boolean
      confirming: boolean
    }
    vm.draft.title = '还在编辑'
    vm.sheetOpen = false
    await wrapper.vm.$nextTick()
    expect(vm.open).toBe(true)
    expect(vm.confirming).toBe(true)
    vm.confirming = false
    await wrapper.vm.$nextTick()
    expect(vm.open).toBe(true)
  })
})
