import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import FavoriteView from '@/views/practice/FavoriteView.vue'
const api = vi.hoisted(() => ({
  getFavorites: vi.fn(),
  removeFavorite: vi.fn(),
  getFavoritePractice: vi.fn(),
  push: vi.fn(),
  back: vi.fn(),
}))
vi.mock('@/api/favorite', () => api)
vi.mock('@/api/practice', () => api)
vi.mock('@/stores/user', () => ({ useUserStore: () => ({ userInfo: { id: 1 } }) }))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: api.push, back: api.back }),
  RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
}))
enableAutoUnmount(afterEach)
const global = {
  stubs: { LtSheet: { props: ['open'], template: '<section v-if="open" role="dialog"><slot /></section>' } },
}
beforeEach(() => {
  vi.clearAllMocks()
  api.getFavorites.mockResolvedValue({
    code: 0,
    data: {
      records: [{ id: 2, questionId: 9, questionContent: '收藏内容', questionType: 'SINGLE_CHOICE' }],
      total: 30,
    },
  })
  api.removeFavorite.mockResolvedValue({ code: 0 })
})
describe('FavoriteView', () => {
  it('keeps an in-app previous page for an ordinary return click', async () => {
    window.history.replaceState({ back: '/questions' }, '', '/favorites')
    const w = mount(FavoriteView, { global })
    await flushPromises()

    await w
      .findAll('a')
      .find((link) => link.text() === '返回练习')!
      .trigger('click')

    expect(api.back).toHaveBeenCalledOnce()
    window.history.replaceState({}, '', '/favorites')
  })

  it('keeps removal behind confirmation and uses the real next page', async () => {
    const w = mount(FavoriteView, { global })
    await flushPromises()
    await w.get('[aria-label="取消收藏第 1 道题"]').trigger('click')
    expect(api.removeFavorite).not.toHaveBeenCalled()
    await w
      .get('[role="dialog"]')
      .findAll('button')
      .find((b) => b.text() === '取消收藏')!
      .trigger('click')
    await flushPromises()
    expect(api.removeFavorite).toHaveBeenCalledWith(9, { errorDisplay: 'inline' })
    await w
      .findAll('button')
      .find((b) => b.text() === '下一页')!
      .trigger('click')
    await flushPromises()
    expect(api.getFavorites).toHaveBeenLastCalledWith({ pageNum: 2, pageSize: 10 }, { errorDisplay: 'inline' })
  })
  it('shows a retryable loading error without a default zero or false empty state', async () => {
    api.getFavorites.mockRejectedValueOnce(new Error('offline'))
    const w = mount(FavoriteView, { global })
    await flushPromises()
    expect(w.text()).toContain('收藏暂时无法加载')
    expect(w.text()).not.toContain('0 道收藏题')
    expect(w.text()).not.toContain('还没有收藏题目')
    await w
      .findAll('button')
      .find((b) => b.text() === '重试')!
      .trigger('click')
    await flushPromises()
    expect(w.text()).toContain('收藏内容')
  })
})
