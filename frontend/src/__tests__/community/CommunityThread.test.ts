import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia } from 'pinia'
import { removeToken, setToken } from '@/utils/auth'
const mocks = vi.hoisted(() => ({
  detail: vi.fn(),
  comments: vi.fn(),
  reviews: vi.fn(),
  add: vi.fn(),
  like: vi.fn(),
  deletePost: vi.fn(),
  download: vi.fn(),
  confirm: vi.fn(),
}))
vi.mock('@/api/community', async (original) => ({
  ...(await original<typeof import('@/api/community')>()),
  getCommunityPost: mocks.detail,
  getCommunityComments: mocks.comments,
  getCommunityReviews: mocks.reviews,
  addCommunityComment: mocks.add,
  setCommunityLike: mocks.like,
  deleteCommunityPost: mocks.deletePost,
  downloadCommunityAttachment: mocks.download,
}))
vi.mock('@/stores/user', () => ({ useUserStore: () => ({ userInfo: { id: 42, role: 'USER' } }) }))
vi.mock('element-plus', async (original) => ({
  ...(await original<typeof import('element-plus')>()),
  ElMessageBox: { confirm: mocks.confirm },
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
  ElButton: {
    props: ['disabled'],
    template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
  },
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
    setToken('community-thread-test')
    mocks.detail.mockImplementation((id: number) => Promise.resolve({ data: post(id) }))
    mocks.comments.mockResolvedValue({ data: { records: [], total: 0 } })
    mocks.reviews.mockResolvedValue({ data: [] })
  })
  afterEach(() => removeToken())
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
    expect(
      wrapper
        .findAll('button')
        .find((button) => button.text().startsWith('赞同'))!
        .attributes('disabled'),
    ).toBeUndefined()
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
  it('does not delete when a post-delete confirmation resolves after unmount or session change', async () => {
    let resolve!: () => void
    mocks.confirm.mockReturnValue(
      new Promise<void>((done) => {
        resolve = done
      }),
    )
    const wrapper = render()
    await flushPromises()
    await wrapper.get('button').trigger('click')
    wrapper.unmount()
    resolve()
    await flushPromises()
    expect(mocks.deletePost).not.toHaveBeenCalled()
  })
  it('does not clear a newer reply draft after the old reply succeeds', async () => {
    let resolve!: (value: unknown) => void
    mocks.add.mockReturnValueOnce(
      new Promise((done) => {
        resolve = done
      }),
    )
    const wrapper = render()
    await flushPromises()
    await wrapper.get('textarea').setValue('old draft')
    await wrapper.get('form').trigger('submit')
    await wrapper.get('textarea').setValue('new draft')
    resolve({ data: 2 })
    await flushPromises()
    expect(wrapper.get('textarea').element.value).toBe('new draft')
    wrapper.unmount()
  })
  it('keeps page one when a reply raises exactly 20 comments to 21', async () => {
    mocks.comments.mockResolvedValue({ data: { records: [], total: 20 } })
    mocks.add.mockResolvedValue({ data: 21 })
    const wrapper = render()
    await flushPromises()
    await wrapper.get('textarea').setValue('boundary reply')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(mocks.comments).toHaveBeenLastCalledWith(1, 1, { errorDisplay: 'inline' })
    wrapper.unmount()
  })
  it('aborts an attachment download when the thread unmounts and ignores its late completion', async () => {
    let resolve!: () => void
    mocks.detail.mockResolvedValue({
      data: { ...post(), attachments: [{ id: 8, name: 'private.txt', sizeBytes: 1 }] },
    })
    mocks.download.mockImplementation(
      (_file: unknown, options: { signal: AbortSignal }) =>
        new Promise<void>((done) => {
          expect(options.signal.aborted).toBe(false)
          resolve = done
        }),
    )
    const wrapper = render()
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '下载')!
      .trigger('click')
    const signal = mocks.download.mock.calls[0][1].signal as AbortSignal
    wrapper.unmount()
    expect(signal.aborted).toBe(true)
    resolve()
    await flushPromises()
    expect(wrapper.emitted('changed')).toBeUndefined()
  })
  it('locks duplicate delete confirmations while the first confirmation is pending', async () => {
    mocks.confirm.mockReturnValue(new Promise<void>(() => undefined))
    const wrapper = render()
    await flushPromises()
    const remove = wrapper.findAll('button').find((button) => button.text() === '删除内容')!
    await remove.trigger('click')
    await remove.trigger('click')
    expect(mocks.confirm).toHaveBeenCalledOnce()
    wrapper.unmount()
  })
  it('does not emit changed when an action completes after an account session change', async () => {
    let resolve!: (value: unknown) => void
    mocks.like.mockReturnValueOnce(
      new Promise((done) => {
        resolve = done
      }),
    )
    const wrapper = render()
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text().startsWith('赞同'))!
      .trigger('click')
    setToken('community-thread-other-session')
    resolve({ data: null })
    await flushPromises()
    expect(wrapper.emitted('changed')).toBeUndefined()
    wrapper.unmount()
  })
})
