import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import type { ReviewScheduleVO } from '@/api/review'

const { getQuestionById, submitReview } = vi.hoisted(() => ({ getQuestionById: vi.fn(), submitReview: vi.fn() }))
vi.mock('@/api/question', () => ({ getQuestionById }))
vi.mock('@/api/review', () => ({ submitReview }))
vi.mock('@/stores/gamification', () => ({ useGamificationStore: () => ({ acceptReward: () => true }) }))

import ReviewClassicSession from '@/views/practice/ReviewClassicSession.vue'
import { removeToken, setToken } from '@/utils/auth'

const card = {
  id: 1,
  questionId: 11,
  hasUnderstanding: false,
  questionContent: '请作答',
  questionType: 'SHORT_ANSWER',
  difficulty: 1,
  courseId: 1,
  courseName: '数据结构',
  easeFactor: null,
  intervalDays: 1,
  repetitions: 0,
  nextReviewDate: '2026-11-02',
  lastReviewDate: '',
  lastQuality: 0,
  totalReviews: 0,
  statusLabel: '新卡片',
  overdue: false,
  overdueDays: 0,
  schedulingState: 'UNRATED',
  lastRating: null,
} satisfies ReviewScheduleVO

describe('ReviewClassicSession', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getQuestionById.mockResolvedValue({
      code: 0,
      data: { id: 11, content: '请作答', questionType: 'SHORT_ANSWER', options: [] },
    })
  })

  afterEach(() => removeToken())

  it('uses automatic scheduling and presents server grading', async () => {
    submitReview.mockResolvedValue({ data: { correct: false, nextReviewDate: '2026-11-02', reward: null } })
    const wrapper = mount(ReviewClassicSession, { props: { cards: [card] } })
    ;(wrapper.vm as { start: () => void }).start()
    await flushPromises()
    await wrapper.get('textarea').setValue('A')
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(submitReview).toHaveBeenCalledWith({ questionId: 11, userAnswer: 'A' }, { errorDisplay: 'inline' })
    expect(wrapper.text()).toContain('服务端判分：回答错误')
    expect(wrapper.text()).toContain('下次复习：2026-11-02')
    expect(wrapper.get('[role="region"][aria-label="复习作答"]')).toBeTruthy()
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="region"][aria-label="本组复习完成"]')).toBeTruthy()
    expect(wrapper.text()).toContain('本组复习完成')
  })

  it('keeps the answer editable after a failed submission and offers retry', async () => {
    submitReview
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce({ data: { correct: true, reward: null } })
    const wrapper = mount(ReviewClassicSession, { props: { cards: [card] } })
    ;(wrapper.vm as { start: () => void }).start()
    await flushPromises()
    await wrapper.get('textarea').setValue('answer')
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('offline')
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('answer')
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(submitReview).toHaveBeenCalledTimes(2)
  })

  it('does not apply a late submission after the authenticated session changes', async () => {
    let resolve!: (value: unknown) => void
    submitReview.mockImplementationOnce(
      () =>
        new Promise((next) => {
          resolve = next
        }),
    )
    setToken('review-session-a')
    const wrapper = mount(ReviewClassicSession, { props: { cards: [card] } })
    ;(wrapper.vm as { start: () => void }).start()
    await flushPromises()
    await wrapper.get('textarea').setValue('answer')
    const pending = wrapper.get('button').trigger('click')
    setToken('review-session-b')
    resolve({ data: { correct: true, reward: null } })
    await pending
    await flushPromises()
    expect(wrapper.text()).not.toContain('服务端判分')
  })
})
