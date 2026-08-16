import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { submitVariantAnswer } = vi.hoisted(() => ({
  submitVariantAnswer: vi.fn(),
}))

vi.mock('@/api/ai', () => ({ submitVariantAnswer }))

vi.mock('@/components/MarkdownRenderer.vue', () => ({
  default: { props: ['content'], template: '<div class="markdown-stub">{{ content }}</div>' },
}))

import AiVariantQuestionCard from '@/components/AiVariantQuestionCard.vue'

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

    expect(submitVariantAnswer).toHaveBeenCalledWith(42, 'B')
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
    expect(wrapper.text()).toContain('你的答案：A · 正确答案：B')
    expect(wrapper.text()).toContain('B 对应核心概念。')
    expect(wrapper.find('.variant-card__actions').exists()).toBe(false)
  })
})
