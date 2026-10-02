import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { setToken } from '@/utils/auth'

const { submitVariantAnswer } = vi.hoisted(() => ({
  submitVariantAnswer: vi.fn(),
}))

vi.mock('@/api/ai', () => ({ submitVariantAnswer }))

vi.mock('@/components/MarkdownRenderer.vue', () => ({
  default: { props: ['content'], template: '<div class="markdown-stub">{{ content }}</div>' },
}))

import AiVariantQuestionCard from '@/components/AiVariantQuestionCard.vue'
import { useGamificationStore } from '@/stores/gamification'
enableAutoUnmount(afterEach)

const question = {
  id: 3,
  questionType: 'SINGLE_CHOICE' as const,
  questionContent: '哪一个选项正确？',
  options: [
    { label: 'A', content: '选项一' },
    { label: 'B', content: '选项二' },
  ],
  difficulty: 3,
}

const global = {
  stubs: {
    'el-tag': { template: '<span><slot /></span>' },
    'el-radio-group': {
      props: ['modelValue'],
      emits: ['update:modelValue'],
      template:
        '<div><button class="select-answer" @click="$emit(\'update:modelValue\', \'B\')">select B</button><slot /></div>',
    },
    'el-radio': { props: ['value'], template: '<label><slot /></label>' },
    'el-button': { template: '<button :disabled="$attrs.disabled" @click="$emit(\'click\')"><slot /></button>' },
  },
}

describe('AiVariantQuestionCard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    setActivePinia(createPinia())
    setToken('variant-session-a')
    submitVariantAnswer.mockResolvedValue({
      code: 0,
      data: {
        questionId: 42,
        assetId: 9,
        status: 'COMPLETED',
        completed: true,
        answered: true,
        correct: true,
        userAnswer: 'B',
        correctAnswer: 'B',
        analysis: 'B 对应核心概念。',
        startedTime: '2026-07-16T12:00:00',
        answeredTime: '2026-07-16T12:05:00',
        completedTime: '2026-07-16T12:05:00',
      },
    })
  })

  it('submits the selected answer and emits the server grading result', async () => {
    const wrapper = mount(AiVariantQuestionCard, {
      props: { questionId: 42, question, training: { answered: false } },
      global,
    })
    await wrapper.find('.select-answer').trigger('click')
    await wrapper.find('.variant-card__actions button').trigger('click')
    await flushPromises()

    expect(submitVariantAnswer).toHaveBeenCalledWith(42, 'B', { errorDisplay: 'inline' })
    expect(wrapper.emitted('answered')?.[0]?.[0]).toMatchObject({ answered: true, correct: true })
  })

  it('shows the persisted first result and analysis after reload', () => {
    const wrapper = mount(AiVariantQuestionCard, {
      props: {
        questionId: 42,
        question,
        training: {
          answered: true,
          correct: false,
          userAnswer: 'A',
          correctAnswer: 'B',
          analysis: 'B 对应核心概念。',
        },
      },
      global,
    })

    expect(wrapper.text()).toContain('这次未答对')
    expect(wrapper.text()).toContain('你的答案：A · 参考答案：B')
    expect(wrapper.text()).toContain('B 对应核心概念。')
    expect(wrapper.find('.variant-card__actions').exists()).toBe(false)
  })

  it('does not accept a reward or emit after an unmounted variant submission resolves', async () => {
    let resolve!: (value: { code: number; data: Record<string, unknown> }) => void
    submitVariantAnswer.mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    const acceptReward = vi.spyOn(useGamificationStore(), 'acceptReward')
    const wrapper = mount(AiVariantQuestionCard, {
      props: { questionId: 42, question, training: { answered: false } },
      global,
    })
    await wrapper.find('.select-answer').trigger('click')
    const pending = wrapper.find('.variant-card__actions button').trigger('click')
    wrapper.unmount()
    resolve({
      code: 0,
      data: {
        questionId: 42,
        assetId: 9,
        status: 'COMPLETED',
        completed: true,
        answered: true,
        correct: true,
        userAnswer: 'B',
        correctAnswer: 'B',
        analysis: '解析',
        reward: {
          eventId: 42,
          awardedXp: 10,
          reason: 'CORRECT_ANSWER',
          eligible: true,
          levelBefore: 1,
          levelAfter: 1,
          leveledUp: false,
          streakDays: 1,
          newAchievements: [],
          summary: { version: 1 },
        },
      },
    })
    await pending
    expect(acceptReward).not.toHaveBeenCalled()
    expect(wrapper.emitted('answered')).toBeUndefined()
  })

  it('将未判分的已保存结果作为自评，不显示错误结论', () => {
    const wrapper = mount(AiVariantQuestionCard, {
      props: {
        questionId: 42,
        question,
        training: { answered: true, correct: null, userAnswer: 'A', correctAnswer: 'B' },
      },
      global,
    })
    expect(wrapper.text()).toContain('已保存，供你自评')
    expect(wrapper.text()).not.toContain('这次未答对')
    expect(wrapper.classes()).not.toContain('has-wrong-result')
    expect(wrapper.text()).toContain('参考答案：B')
  })

  it('clears a failed submission after retry so the saved result becomes visible', async () => {
    submitVariantAnswer.mockRejectedValueOnce(new Error('offline'))
    const wrapper = mount(AiVariantQuestionCard, {
      props: { questionId: 42, question, training: { answered: false } },
      global,
    })
    await wrapper.find('.select-answer').trigger('click')
    await wrapper.find('.variant-card__actions button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('提交答案失败')
    await wrapper.find('.variant-error button').trigger('click')
    await flushPromises()
    expect(submitVariantAnswer).toHaveBeenCalledTimes(2)
    await wrapper.setProps({ training: { answered: true, correct: true, userAnswer: 'B' } })
    expect(wrapper.text()).not.toContain('提交答案失败')
    expect(wrapper.text()).toContain('回答正确')
  })

  it('切换题目后忽略旧题迟到提交结果并清空选择', async () => {
    let resolve!: (value: { code: number; data: Record<string, unknown> }) => void
    submitVariantAnswer.mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    const wrapper = mount(AiVariantQuestionCard, {
      props: { questionId: 42, question, training: { answered: false } },
      global,
    })
    await wrapper.find('.select-answer').trigger('click')
    const pending = wrapper.find('.variant-card__actions button').trigger('click')
    await wrapper.setProps({ questionId: 43, question: { ...question, id: 4 }, training: { answered: false } })
    resolve({ code: 0, data: { questionId: 42, answered: true, correct: true, userAnswer: 'B', correctAnswer: 'B' } })
    await pending
    expect(wrapper.emitted('answered')).toBeUndefined()
    expect(wrapper.find('.variant-card__actions button').attributes('disabled')).toBeDefined()
  })
})
