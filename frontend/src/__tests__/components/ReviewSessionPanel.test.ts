import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ReviewScheduleVO } from '@/api/review'
import { useGamificationStore } from '@/stores/gamification'

const { message, submitReview } = vi.hoisted(() => ({
  message: { error: vi.fn(), info: vi.fn() },
  submitReview: vi.fn(),
}))

vi.mock('@/api/review', () => ({ submitReview }))
vi.mock('element-plus', () => ({ ElMessage: message }))

import ReviewSessionPanel from '@/components/review/ReviewSessionPanel.vue'

const card: ReviewScheduleVO = {
  id: 1,
  questionId: 21,
  questionContent: '课程目标复习题',
  questionType: 'SINGLE_CHOICE',
  difficulty: 2,
  courseId: 408,
  courseName: '408 数据结构',
  easeFactor: 2.5,
  intervalDays: 1,
  repetitions: 0,
  nextReviewDate: '2026-09-06',
  lastReviewDate: '2026-09-05',
  lastQuality: 0,
  totalReviews: 0,
  overdue: false,
  overdueDays: 0,
  statusLabel: '新卡片',
}

describe('ReviewSessionPanel', () => {
  it('uses the returned grading result even when a schedule has an earlier long interval', async () => {
    submitReview.mockResolvedValue({ data: { ...card, repetitions: 2, intervalDays: 3, correct: false } })
    const wrapper = mount(ReviewSessionPanel, {
      props: { cards: [card] },
      global: {
        stubs: {
          'el-card': { template: '<section><slot name="header" /><slot /></section>' },
          'el-input': {
            props: ['modelValue'],
            emits: ['update:modelValue'],
            template: '<textarea :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
          },
          'el-button': { emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
          'el-alert': { props: ['title'], template: '<div>{{ title }}</div>' },
        },
      },
    })
    ;(wrapper.vm as unknown as { start: () => void }).start()
    await wrapper.vm.$nextTick()
    await wrapper.find('textarea').setValue('答案')
    await wrapper
      .findAll('button')
      .find((b) => b.text().includes('提交答案'))!
      .trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('回答错误')
    expect(wrapper.text()).not.toContain('回答正确')
    wrapper.unmount()
  })

  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    submitReview.mockResolvedValue({ data: { ...card, repetitions: 1, intervalDays: 3, correct: true } })
  })

  it('owns answer submission and reports a completed review fact to the parent', async () => {
    const wrapper = mount(ReviewSessionPanel, {
      props: { cards: [card] },
      global: {
        stubs: {
          'el-card': { template: '<section><slot name="header" /><slot /></section>' },
          'el-tag': { template: '<span><slot /></span>' },
          'el-input': {
            props: ['modelValue'],
            emits: ['update:modelValue'],
            template: '<textarea :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
          },
          'el-button': {
            props: ['disabled'],
            emits: ['click'],
            template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
          },
          'el-alert': { props: ['title'], template: '<div>{{ title }}</div>' },
        },
      },
    })

    ;(wrapper.vm as unknown as { start: () => void }).start()
    await wrapper.vm.$nextTick()
    await wrapper.find('textarea').setValue('答案')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('提交答案'))!
      .trigger('click')
    await flushPromises()

    expect(submitReview).toHaveBeenCalledWith({ questionId: 21, userAnswer: '答案' })
    expect(wrapper.emitted('reviewed')).toHaveLength(1)
    expect(wrapper.text()).toContain('回答正确')
  })

  it('does not accept a reward or report completion when review submission fails', async () => {
    submitReview.mockRejectedValueOnce(new Error('network'))
    const acceptReward = vi.spyOn(useGamificationStore(), 'acceptReward')
    const wrapper = mount(ReviewSessionPanel, {
      props: { cards: [card] },
      global: { stubs: reviewStubs },
    })
    ;(wrapper.vm as unknown as { start: () => void }).start()
    await wrapper.vm.$nextTick()
    await wrapper.find('textarea').setValue('答案')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('提交答案'))!
      .trigger('click')
    await flushPromises()
    expect(acceptReward).not.toHaveBeenCalled()
    expect(wrapper.emitted('reviewed')).toBeUndefined()
    expect(wrapper.text()).not.toContain('作答已记录')
    expect(message.error).toHaveBeenCalled()
  })

  it('ignores a late response after the learner ends the review session', async () => {
    let resolve!: (value: { data: ReviewScheduleVO }) => void
    submitReview.mockImplementationOnce(
      () =>
        new Promise((next) => {
          resolve = next
        }),
    )
    const acceptReward = vi.spyOn(useGamificationStore(), 'acceptReward')
    const wrapper = mount(ReviewSessionPanel, {
      props: { cards: [card] },
      global: { stubs: reviewStubs },
    })
    ;(wrapper.vm as unknown as { start: () => void }).start()
    await wrapper.vm.$nextTick()
    await wrapper.find('textarea').setValue('答案')
    const pending = wrapper
      .findAll('button')
      .find((button) => button.text().includes('提交答案'))!
      .trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('结束复习'))!
      .trigger('click')
    resolve({ data: { ...card, correct: true } })
    await pending
    await flushPromises()
    expect(acceptReward).not.toHaveBeenCalled()
    expect(wrapper.emitted('reviewed')).toBeUndefined()
    expect(wrapper.text()).not.toContain('回答正确')
  })

  it('does not process a late response after the review panel unmounts', async () => {
    let resolve!: (value: { data: ReviewScheduleVO }) => void
    submitReview.mockImplementationOnce(
      () =>
        new Promise((next) => {
          resolve = next
        }),
    )
    const acceptReward = vi.spyOn(useGamificationStore(), 'acceptReward')
    const wrapper = mount(ReviewSessionPanel, {
      props: { cards: [card] },
      global: { stubs: reviewStubs },
    })
    ;(wrapper.vm as unknown as { start: () => void }).start()
    await wrapper.vm.$nextTick()
    await wrapper.find('textarea').setValue('答案')
    const pending = wrapper
      .findAll('button')
      .find((button) => button.text().includes('提交答案'))!
      .trigger('click')
    wrapper.unmount()
    resolve({ data: { ...card, correct: true } })
    await pending
    expect(acceptReward).not.toHaveBeenCalled()
  })

  it('keeps an ungraded answer out of the correctness denominator', async () => {
    submitReview.mockResolvedValueOnce({ data: { ...card, correct: null } })
    const wrapper = mount(ReviewSessionPanel, {
      props: { cards: [card] },
      global: { stubs: reviewStubs },
    })
    ;(wrapper.vm as unknown as { start: () => void }).start()
    await wrapper.vm.$nextTick()
    await wrapper.find('textarea').setValue('主观题答案')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('提交答案'))!
      .trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('本题尚未判分')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('完成复习'))!
      .trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('本组复习完成')
    expect(wrapper.text()).toContain('待判分')
    expect(wrapper.text()).toContain('已判分正确率')
    expect(wrapper.text()).not.toContain('0%')
  })
})

const reviewStubs = {
  'el-card': { template: '<section><slot name="header" /><slot /></section>' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-input': {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<textarea :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
  'el-button': {
    props: ['disabled', 'loading'],
    emits: ['click'],
    template: '<button :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>',
  },
  'el-alert': { props: ['title', 'description'], template: '<div>{{ title }} {{ description }}</div>' },
}
