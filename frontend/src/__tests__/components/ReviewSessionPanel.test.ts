import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ReviewScheduleVO } from '@/api/review'

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
  beforeEach(() => {
    vi.clearAllMocks()
    submitReview.mockResolvedValue({ data: { ...card, repetitions: 1, intervalDays: 3 } })
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
})
