import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import type { ReviewScheduleVO } from '@/api/review'

const api = vi.hoisted(() => ({
  getDueReviewCards: vi.fn(),
  getAllReviewCards: vi.fn(),
  getReviewStats: vi.fn(),
  removeFromReviewPlan: vi.fn(),
  resetReviewProgress: vi.fn(),
}))
vi.mock('@/api/review', () => api)
const route = reactive({ query: {} as Record<string, string> })
const router = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn() }))
vi.mock('vue-router', async (original) => ({
  ...(await original<typeof import('vue-router')>()),
  useRoute: () => route,
  useRouter: () => router,
}))

import ReviewView from '@/views/review/ReviewView.vue'
import { dueReason, nextDueDate } from '@/views/review/useReview'

function card(questionId: number, overrides: Partial<ReviewScheduleVO> = {}) {
  return {
    id: questionId,
    questionId,
    questionContent: `第 ${questionId} 题`,
    questionType: 'SINGLE_CHOICE',
    courseId: 6,
    courseName: '408 数据结构',
    nextReviewDate: '2026-10-16',
    overdueDays: 0,
    statusLabel: '间隔复习',
    schedulingState: 'REVIEW',
    lastRating: 'GOOD',
    availableForReview: true,
    hasUnderstanding: true,
    ...overrides,
  } as ReviewScheduleVO
}
const stats = { totalCards: 2, dueToday: 1, overdue: 0, reviewedToday: 0, lastAgainCards: 1 }

describe('dueReason', () => {
  it('names the latest rating first, then a fresh card, then the schedule', () => {
    expect(dueReason({ lastRating: 'AGAIN', schedulingState: 'REVIEW' }).text).toBe('上次答错')
    expect(dueReason({ lastRating: 'HARD', schedulingState: 'REVIEW' }).text).toBe('上次觉得难')
    expect(dueReason({ lastRating: null, schedulingState: 'UNRATED' }).text).toBe('首次复习')
    expect(dueReason({ lastRating: 'GOOD', schedulingState: 'REVIEW' }).text).toBe('按排期到期')
  })
})

describe('nextDueDate', () => {
  it('skips cards already due and cards that cannot be answered yet', () => {
    const due = [card(1)]
    const all = [
      card(1),
      card(2, { nextReviewDate: '2026-10-20', availableForReview: false }),
      card(3, { nextReviewDate: '2026-10-23' }),
      card(4, { nextReviewDate: '2026-10-18' }),
    ]
    expect(nextDueDate(all, due)).toBe('2026-10-18')
    expect(nextDueDate([card(1)], due)).toBeNull()
  })
})

describe('ReviewView', () => {
  const mounted: ReturnType<typeof mount>[] = []
  function render() {
    const wrapper = mount(ReviewView)
    mounted.push(wrapper)
    return wrapper
  }
  function button(wrapper: ReturnType<typeof mount>, name: string) {
    const found = wrapper.findAll('button').filter((item) => item.text().includes(name))
    expect(found.length).toBeGreaterThan(0)
    return found
  }

  beforeEach(() => {
    route.query = {}
    api.getDueReviewCards.mockResolvedValue({ data: [card(26, { lastRating: 'AGAIN' })] })
    api.getAllReviewCards.mockResolvedValue({
      data: [card(99, { nextReviewDate: '2026-10-20', hasUnderstanding: false }), card(26)],
    })
    api.getReviewStats.mockResolvedValue({ data: stats })
    api.removeFromReviewPlan.mockResolvedValue({ data: null })
  })
  afterEach(() => {
    mounted.splice(0).forEach((wrapper) => wrapper.unmount())
    vi.clearAllMocks()
  })

  it('opens a main-line question on the understanding page and others in the old review tools', async () => {
    const wrapper = render()
    await flushPromises()
    expect(wrapper.text()).toContain('上次答错')
    await button(wrapper, '从第一道开始')[0].trigger('click')
    expect(router.push).toHaveBeenCalledWith({ name: 'QuestionUnderstanding', params: { id: 26 } })

    api.getDueReviewCards.mockResolvedValueOnce({ data: [card(99, { hasUnderstanding: false })] })
    route.query = { courseId: '6' }
    await flushPromises()
    await button(wrapper, '复习')[0].trigger('click')
    expect(router.push).toHaveBeenLastCalledWith({
      name: 'ReviewClassic',
      query: { courseId: '6', questionId: '99' },
    })
  })

  it('passes the route scope to the queue and lists every card soonest first', async () => {
    route.query = { courseId: '6', questionId: '26' }
    const wrapper = render()
    await flushPromises()
    expect(api.getDueReviewCards).toHaveBeenCalledWith(6, 50, 26, undefined, { errorDisplay: 'inline' })
    expect(wrapper.text()).toContain('只看这一道题')
    route.query = {}
    await flushPromises()
    const stems = wrapper.findAll('.all .stem').map((item) => item.text())
    expect(stems).toEqual(['第 26 题', '第 99 题'])
  })

  it('asks once before removing a card and reloads the plan', async () => {
    const wrapper = render()
    await flushPromises()
    await button(wrapper, '移出')[0].trigger('click')
    expect(api.removeFromReviewPlan).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('移出复习计划？')
    await button(wrapper, '移出')[0].trigger('click')
    await flushPromises()
    expect(api.removeFromReviewPlan).toHaveBeenCalledWith(26, { errorDisplay: 'inline' })
    expect(api.getDueReviewCards).toHaveBeenCalledTimes(2)
  })

  it('says when the next card comes due when nothing is due today', async () => {
    api.getDueReviewCards.mockResolvedValue({ data: [] })
    api.getAllReviewCards.mockResolvedValue({ data: [card(3, { nextReviewDate: '2099-03-05' })] })
    const wrapper = render()
    await flushPromises()
    expect(wrapper.text()).toContain('今天没有到期的题')
    expect(wrapper.text()).toContain('下一道在 2099 年 3 月 5 日 到期')
  })

  it('stays in place with recovery when the plan fails to load', async () => {
    api.getReviewStats.mockRejectedValueOnce(new Error('offline'))
    const wrapper = render()
    await flushPromises()
    expect(wrapper.text()).toContain('复习计划没有加载出来')
    await button(wrapper, '重试')[0].trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('今天到期')
    await button(wrapper, '打开复习工具')[0].trigger('click')
    expect(router.push).toHaveBeenCalledWith({ name: 'ReviewClassic' })
  })
})
