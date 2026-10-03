import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ReviewScheduleVO } from '@/api/review'
import { useGamificationStore } from '@/stores/gamification'

const { message, submitReview, getQuestionById } = vi.hoisted(() => ({
  message: { error: vi.fn(), info: vi.fn() },
  submitReview: vi.fn(),
  getQuestionById: vi.fn(),
}))

vi.mock('@/api/review', () => ({ submitReview }))
vi.mock('@/api/question', () => ({ getQuestionById }))
vi.mock('element-plus', () => ({ ElMessage: message }))

import ReviewSessionPanel from '@/components/review/ReviewSessionPanel.vue'

const card: ReviewScheduleVO = {
  id: 1,
  questionId: 21,
  questionContent: '课程目标复习题',
  questionType: 'SHORT_ANSWER',
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
    getQuestionById.mockResolvedValue({
      code: 0,
      data: { id: card.questionId, content: card.questionContent, questionType: 'SHORT_ANSWER', options: [] },
    })
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
    await flushPromises()
    await wrapper.find('textarea').setValue('答案')
    await wrapper
      .findAll('button')
      .find((b) => b.text().includes('提交答案'))!
      .trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('回答错误')
    expect(wrapper.text()).not.toContain('服务端判分：回答正确')
    wrapper.unmount()
  })

  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    submitReview.mockResolvedValue({ data: { ...card, repetitions: 1, intervalDays: 3, correct: true } })
    getQuestionById.mockImplementation((id: number) =>
      Promise.resolve({
        code: 0,
        data: { id, content: card.questionContent, questionType: 'SHORT_ANSWER', options: [] },
      }),
    )
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
    await flushPromises()
    await wrapper.find('textarea').setValue('答案')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('提交答案'))!
      .trigger('click')
    await flushPromises()

    expect(submitReview).toHaveBeenCalledWith({ questionId: 21, userAnswer: '答案' }, { errorDisplay: 'inline' })
    expect(wrapper.emitted('reviewed')).toHaveLength(1)
    expect(wrapper.text()).toContain('服务端判分：回答正确')
  })

  it('loads the learner-safe question and submits selected labels for choice questions', async () => {
    const choiceCard = { ...card, questionType: 'SINGLE_CHOICE' }
    getQuestionById.mockResolvedValueOnce({
      code: 0,
      data: {
        id: card.questionId,
        content: card.questionContent,
        questionType: 'SINGLE_CHOICE',
        options: [
          { id: 1, optionLabel: 'A', content: '选项 A' },
          { id: 2, optionLabel: 'B', content: '选项 B' },
        ],
      },
    })
    const wrapper = mount(ReviewSessionPanel, { props: { cards: [choiceCard] }, global: { stubs: reviewStubs } })
    ;(wrapper.vm as unknown as { start: () => void }).start()
    await flushPromises()

    expect(getQuestionById).toHaveBeenCalledWith(21, { errorDisplay: 'inline' })
    expect(wrapper.text()).toContain('单选题')
    expect(wrapper.text()).not.toContain('EF')
    await wrapper.find('input[type="radio"][value="B"]').setValue()
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('提交答案'))!
      .trigger('click')
    await flushPromises()

    expect(submitReview).toHaveBeenLastCalledWith({ questionId: 21, userAnswer: 'B' }, { errorDisplay: 'inline' })
  })

  it('blocks submission until the learner-safe question loads and offers an inline retry', async () => {
    getQuestionById.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({
      code: 0,
      data: { id: card.questionId, content: card.questionContent, questionType: 'SHORT_ANSWER', options: [] },
    })
    const wrapper = mount(ReviewSessionPanel, { props: { cards: [card] }, global: { stubs: reviewStubs } })
    ;(wrapper.vm as unknown as { start: () => void }).start()
    await flushPromises()

    expect(wrapper.text()).toContain('题目暂时无法加载')
    expect(
      wrapper
        .findAll('button')
        .find((button) => button.text().includes('提交答案'))!
        .attributes('disabled'),
    ).toBeDefined()
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('重新加载题目'))!
      .trigger('click')
    await flushPromises()

    expect(wrapper.find('textarea').exists()).toBe(true)
    expect(getQuestionById).toHaveBeenCalledTimes(2)
  })

  it('moves keyboard focus to the sole next action after a successful submission', async () => {
    const wrapper = mount(ReviewSessionPanel, {
      attachTo: document.body,
      props: { cards: [card] },
      global: { stubs: reviewStubs },
    })
    ;(wrapper.vm as unknown as { start: () => void }).start()
    await flushPromises()
    await wrapper.find('textarea').setValue('答案')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('提交答案'))!
      .trigger('click')
    await flushPromises()

    expect((document.activeElement as HTMLButtonElement).textContent).toContain('完成复习')
    wrapper.unmount()
  })

  it('keeps a failed review submission beside the answer with a retry, without a global error toast', async () => {
    submitReview.mockRejectedValueOnce(new Error('Network Error'))
    const acceptReward = vi.spyOn(useGamificationStore(), 'acceptReward')
    const wrapper = mount(ReviewSessionPanel, {
      props: { cards: [card] },
      global: { stubs: reviewStubs },
    })
    ;(wrapper.vm as unknown as { start: () => void }).start()
    await flushPromises()
    await wrapper.find('textarea').setValue('答案')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('提交答案'))!
      .trigger('click')
    await flushPromises()
    expect(acceptReward).not.toHaveBeenCalled()
    expect(wrapper.emitted('reviewed')).toBeUndefined()
    expect(wrapper.text()).not.toContain('作答已记录')
    expect(wrapper.get('[role="alert"]').text()).toContain('提交失败，请重试')
    expect(wrapper.get('[role="alert"] button').text()).toBe('重试')
    expect(message.error).not.toHaveBeenCalled()
  })

  it('keeps a server grading-basis rejection beside the selected answer without completing the review', async () => {
    const rejection = '题目尚未配置可用判分依据，暂时无法提交练习'
    submitReview.mockRejectedValueOnce(new Error(rejection))
    const acceptReward = vi.spyOn(useGamificationStore(), 'acceptReward')
    const wrapper = mount(ReviewSessionPanel, { props: { cards: [card] }, global: { stubs: reviewStubs } })
    ;(wrapper.vm as unknown as { start: () => void }).start()
    await flushPromises()
    const input = wrapper.find('textarea')
    await input.setValue('答案')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('提交答案'))!
      .trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain(rejection)
    expect((input.element as HTMLTextAreaElement).value).toBe('答案')
    expect(wrapper.emitted('reviewed')).toBeUndefined()
    expect(wrapper.text()).not.toContain('服务端判分：')
    expect(acceptReward).not.toHaveBeenCalled()
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
    await flushPromises()
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
    expect(wrapper.text()).not.toContain('服务端判分：回答正确')
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
    await flushPromises()
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

  it('shows pending reviews alongside a partial graded correct rate in the summary', async () => {
    submitReview.mockResolvedValueOnce({ data: { ...card, correct: true } }).mockResolvedValueOnce({
      data: { ...card, questionId: 22, correct: null },
    })
    const wrapper = mount(ReviewSessionPanel, {
      props: { cards: [card, { ...card, id: 2, questionId: 22, questionContent: '待判分题' }] },
      global: { stubs: reviewStubs },
    })
    ;(wrapper.vm as unknown as { start: () => void }).start()
    await flushPromises()
    await wrapper.find('textarea').setValue('A')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('提交答案'))!
      .trigger('click')
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('下一题'))!
      .trigger('click')
    await wrapper.find('textarea').setValue('主观题答案')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('提交答案'))!
      .trigger('click')
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('完成复习'))!
      .trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('已判分正确率')
    expect(wrapper.text()).toContain('100%')
    expect(wrapper.text()).toContain('待判分1 题')
  })

  it('keeps an ungraded answer out of the correctness denominator', async () => {
    submitReview.mockResolvedValueOnce({ data: { ...card, correct: null } })
    const wrapper = mount(ReviewSessionPanel, {
      props: { cards: [card] },
      global: { stubs: reviewStubs },
    })
    ;(wrapper.vm as unknown as { start: () => void }).start()
    await flushPromises()
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
