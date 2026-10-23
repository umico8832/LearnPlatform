import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
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
  deleteComment: vi.fn(),
  push: vi.fn(),
}))
vi.mock('@/api/community', async (original) => ({
  ...(await original<typeof import('@/api/community')>()),
  getCommunityPost: mocks.detail,
  getCommunityComments: mocks.comments,
  getCommunityReviews: mocks.reviews,
  addCommunityComment: mocks.add,
  setCommunityLike: mocks.like,
  deleteCommunityPost: mocks.deletePost,
  deleteCommunityComment: mocks.deleteComment,
}))
vi.mock('@/stores/user', () => ({ useUserStore: () => ({ userInfo: { id: 42, role: 'USER' } }) }))
vi.mock('vue-router', async (original) => ({
  ...(await original<typeof import('vue-router')>()),
  useRoute: () => ({ params: { id: '1' } }),
  useRouter: () => ({ push: mocks.push }),
}))
import CommunityDetailView from '@/views/community/CommunityDetailView.vue'

const post = (overrides = {}) => ({
  id: 1,
  userId: 42,
  authorName: 'Author',
  title: '如何理解栈的后进先出？',
  body: '<img src=x onerror=alert(1)>',
  contentType: 'TOPIC',
  status: 'APPROVED',
  examName: '408',
  subjectName: '数据结构',
  schoolName: null,
  courseName: null,
  conceptName: '栈',
  sourceNote: '',
  reviewNote: '',
  createdAt: '2026-10-23T10:00:00',
  attachments: [],
  questionLinks: [],
  likeCount: 0,
  commentCount: 1,
  liked: false,
  ...overrides,
})
const comment = {
  id: 9,
  userId: 7,
  authorName: 'Reader',
  body: '可以用叠放的书本来理解。',
  replyToName: null,
  createdAt: '2026-10-23T10:05:00',
  likeCount: 0,
  liked: false,
  deleted: false,
}
const stubs = {
  RouterLink: RouterLinkStub,
  LtSheet: {
    props: ['open', 'title'],
    emits: ['update:open'],
    template: '<div v-if="open" role="dialog" :aria-label="title"><slot /></div>',
  },
}
function render() {
  return mount(CommunityDetailView, { global: { plugins: [createPinia()], stubs } })
}
const buttonIn = (root: ReturnType<typeof render>, text: string) =>
  root.findAll('button').find((item) => item.text() === text)!

describe('community detail page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setToken('community-detail-test')
    mocks.detail.mockResolvedValue({ data: post() })
    mocks.comments.mockResolvedValue({ data: { records: [comment], total: 1 } })
    mocks.reviews.mockResolvedValue({ data: [] })
    mocks.like.mockResolvedValue({})
    mocks.deletePost.mockResolvedValue({})
  })
  afterEach(() => removeToken())

  it('reads the post as text with its exam and subject beside it', async () => {
    const wrapper = render()
    await flushPromises()

    expect(wrapper.get('h1').text()).toBe('如何理解栈的后进先出？')
    expect(wrapper.find('.post-body img').exists()).toBe(false)
    expect(wrapper.get('.post-body').text()).toContain('<img')
    expect(wrapper.get('.facts').text()).toContain('数据结构')
    expect(wrapper.get('.community-comment').text()).toContain('可以用叠放的书本来理解。')
    expect(wrapper.findComponent(RouterLinkStub).props('to')).toBe('/community')
  })

  it('likes the post and marks the button pressed after reload', async () => {
    const wrapper = render()
    await flushPromises()
    mocks.detail.mockResolvedValue({ data: post({ liked: true, likeCount: 1 }) })

    await buttonIn(wrapper, '赞同 · 0').trigger('click')
    await flushPromises()

    expect(mocks.like).toHaveBeenCalledWith(1, true, undefined)
    expect(buttonIn(wrapper, '已赞同 · 1').attributes('aria-pressed')).toBe('true')
  })

  it('keeps the post when the delete sheet is cancelled and leaves after a confirmed delete', async () => {
    const wrapper = render()
    await flushPromises()

    await buttonIn(wrapper, '删除内容').trigger('click')
    const sheet = wrapper.get('[role="dialog"][aria-label="删除这条内容？"]')
    expect(sheet.text()).toContain('已整理的题目投稿会保留')
    await buttonIn(wrapper, '取消').trigger('click')
    await flushPromises()
    expect(mocks.deletePost).not.toHaveBeenCalled()
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)

    await buttonIn(wrapper, '删除内容').trigger('click')
    const confirm = wrapper
      .get('[role="dialog"]')
      .findAll('button')
      .find((item) => item.text() === '删除内容')!
    await confirm.trigger('click')
    await flushPromises()
    expect(mocks.deletePost).toHaveBeenCalledWith(1, { errorDisplay: 'inline' })
    expect(mocks.push).toHaveBeenCalledWith('/community')
  })

  it('offers no reply form before the post is public', async () => {
    mocks.detail.mockResolvedValue({ data: post({ status: 'PENDING' }) })
    const wrapper = render()
    await flushPromises()

    expect(wrapper.find('textarea').exists()).toBe(false)
    expect(wrapper.text()).toContain('内容公开后才能参与讨论')
    expect(wrapper.get('.tag[data-status="PENDING"]').text()).toBe('待审核')
  })

  it('shows a retry when the post cannot be read', async () => {
    mocks.detail.mockRejectedValueOnce(new Error('gone'))
    const wrapper = render()
    await flushPromises()

    expect(wrapper.get('h1').text()).toBe('内容没有加载出来')
    await buttonIn(wrapper, '重新加载').trigger('click')
    await flushPromises()
    expect(wrapper.get('h1').text()).toBe('如何理解栈的后进先出？')
  })
})
