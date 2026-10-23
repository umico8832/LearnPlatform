import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import CatalogSearch from '@/views/course/CatalogSearch.vue'
import CatalogDiscussion from '@/views/course/CatalogDiscussion.vue'

const api = vi.hoisted(() => ({
  globalSearch: vi.fn(),
  getComments: vi.fn(),
  addComment: vi.fn(),
  deleteComment: vi.fn(),
  toggleLike: vi.fn(),
}))
vi.mock('@/api/search', () => ({ globalSearch: api.globalSearch }))
vi.mock('@/api/comment', () => api)
vi.mock('@/stores/user', () => ({ useUserStore: () => ({ userInfo: { id: 1 } }) }))
enableAutoUnmount(afterEach)
const global = {
  stubs: {
    RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
    LtSheet: { props: ['open'], template: '<section v-if="open"><slot /></section>' },
  },
}
const comment = {
  id: 7,
  questionId: 3,
  userId: 1,
  nickname: '学员',
  content: '讨论内容',
  createTime: '',
  likeCount: 0,
  likedByMe: false,
  replies: [],
}
beforeEach(() => {
  vi.clearAllMocks()
  api.getComments.mockResolvedValue({ data: [{ ...comment, replies: [] }] })
  api.toggleLike.mockResolvedValue({ data: true })
  api.deleteComment.mockResolvedValue({})
  api.addComment.mockResolvedValue({ data: { ...comment, id: 8, content: '新评论' } })
})
describe('CatalogTools', () => {
  it('searches through the real search API and keeps deep links', async () => {
    api.globalSearch.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({
      data: { questions: [{ id: 3, title: '栈题', subtitle: '数据结构', link: '/questions?questionId=3' }] },
    })
    const w = mount(CatalogSearch, { global })
    await w.get('input').setValue('栈')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(w.text()).toContain('搜索暂时不可用')
    await w
      .findAll('button')
      .find((button) => button.text() === '重试搜索')!
      .trigger('click')
    await flushPromises()
    expect(api.globalSearch).toHaveBeenLastCalledWith('栈', 20)
    expect(w.get('a').attributes('href')).toBe('/questions?questionId=3')
  })
  it('shows failed loading with recovery and adds replies with their parent', async () => {
    api.getComments.mockRejectedValueOnce(new Error('offline'))
    const w = mount(CatalogDiscussion, { props: { questionId: 3 }, global })
    await flushPromises()
    expect(w.text()).toContain('讨论暂时无法加载')
    await w
      .findAll('button')
      .find((b) => b.text() === '重试')!
      .trigger('click')
    await flushPromises()
    await w
      .findAll('button')
      .find((b) => b.text() === '回复')!
      .trigger('click')
    await w.get('textarea').setValue('新评论')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(api.addComment).toHaveBeenCalledWith(
      { questionId: 3, content: '新评论', parentId: 7, replyToUserId: 1 },
      { errorDisplay: 'inline' },
    )
    expect(w.text()).toContain('新评论')
  })
  it('persists likes and waits for confirmation before deleting', async () => {
    const w = mount(CatalogDiscussion, { props: { questionId: 3 }, global })
    await flushPromises()
    await w
      .findAll('button')
      .find((b) => b.text() === '点赞')!
      .trigger('click')
    await flushPromises()
    expect(w.find('[aria-pressed="true"]').exists()).toBe(true)
    await w
      .findAll('button')
      .find((b) => b.text() === '删除')!
      .trigger('click')
    expect(api.deleteComment).not.toHaveBeenCalled()
    await w
      .findAll('button')
      .find((b) => b.text() === '确定')!
      .trigger('click')
    await flushPromises()
    expect(api.deleteComment).toHaveBeenCalledWith(7, { errorDisplay: 'inline' })
    expect(w.text()).not.toContain('讨论内容')
  })
})
