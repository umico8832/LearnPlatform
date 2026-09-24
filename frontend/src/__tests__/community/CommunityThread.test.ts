import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia } from 'pinia'
const mocks = vi.hoisted(() => ({ detail: vi.fn(), comments: vi.fn(), add: vi.fn(), like: vi.fn() }))
vi.mock('@/api/community', async (original) => ({
  ...(await original<typeof import('@/api/community')>()),
  getCommunityPost: mocks.detail,
  getCommunityComments: mocks.comments,
  addCommunityComment: mocks.add,
  setCommunityLike: mocks.like,
}))
import CommunityThread from '@/components/community/CommunityThread.vue'
const post = (id = 1) => ({
  id,
  userId: 42,
  authorName: 'Author',
  title: `Topic ${id}`,
  body: '<img src=x onerror=alert(1)>',
  contentType: 'TOPIC',
  status: 'APPROVED',
  attachments: [],
  questionLinks: [],
  likeCount: 0,
  commentCount: 0,
  liked: false,
})
const slot = { template: '<div><slot /></div>' }
const stubs = {
  LpSkeleton: true,
  ElTag: slot,
  ElAlert: { props: ['title'], template: '<div>{{ title }}<slot /></div>' },
  ElButton: { template: '<button @click="$emit(\'click\')"><slot /></button>' },
  ElInput: {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<textarea :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
}
function render() {
  return mount(CommunityThread, { props: { id: 1 }, global: { plugins: [createPinia()], stubs } })
}
describe('community discussion states', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.detail.mockImplementation((id: number) => Promise.resolve({ data: post(id) }))
    mocks.comments.mockResolvedValue({ data: { records: [], total: 0 } })
  })
  it('renders submitted markup as text', async () => {
    const wrapper = render()
    await flushPromises()
    expect(wrapper.text()).toContain('<img src=x onerror=alert(1)>')
    expect(wrapper.find('img').exists()).toBe(false)
    wrapper.unmount()
  })
  it('retains a draft after reply failure and allows retry', async () => {
    mocks.add.mockRejectedValueOnce(new Error('network unavailable')).mockResolvedValueOnce({ data: 2 })
    const wrapper = render()
    await flushPromises()
    await wrapper.get('textarea').setValue('My explanation')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('textarea').element.value).toBe('My explanation')
    expect(wrapper.text()).toContain('network unavailable')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(mocks.add).toHaveBeenCalledTimes(2)
    expect(wrapper.get('textarea').element.value).toBe('')
    wrapper.unmount()
  })
  it('ignores a late detail response after changing the selected topic', async () => {
    let resolve!: (value: unknown) => void
    mocks.detail.mockReturnValueOnce(
      new Promise((r) => {
        resolve = r
      }),
    )
    const wrapper = render()
    await wrapper.setProps({ id: 2 })
    await flushPromises()
    resolve({ data: post(1) })
    await flushPromises()
    expect(wrapper.text()).toContain('Topic 2')
    expect(wrapper.text()).not.toContain('Topic 1')
    wrapper.unmount()
  })
})
