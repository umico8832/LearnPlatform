import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const { mockStreamQuestionAi, mockStreamExamLearningAi } = vi.hoisted(() => ({
  mockStreamQuestionAi: vi.fn(),
  mockStreamExamLearningAi: vi.fn(),
}))

vi.mock('@/api/ai', () => ({
  streamQuestionAi: (...args: unknown[]) => mockStreamQuestionAi(...args),
  streamExamLearningAi: (...args: unknown[]) => mockStreamExamLearningAi(...args),
}))

import AiQuestionAssistant from '@/components/AiQuestionAssistant.vue'

const stubs = {
  'el-button': {
    template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
    props: ['disabled', 'loading', 'type'],
    emits: ['click'],
  },
  'el-icon': { template: '<span><slot /></span>' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-alert': { template: '<div />' },
  MarkdownRenderer: { template: '<div />' },
  Reading: true,
  MagicStick: true,
}

describe('AiQuestionAssistant', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockStreamQuestionAi.mockResolvedValue(undefined)
    mockStreamExamLearningAi.mockResolvedValue(undefined)
  })

  it('uses the traceable exam-learning endpoint when a session is provided', async () => {
    const wrapper = mount(AiQuestionAssistant, {
      props: { questionId: 10, learningSessionId: 30 },
      global: { stubs },
    })

    await wrapper.findAll('button')[0].trigger('click')
    await flushPromises()

    expect(mockStreamExamLearningAi).toHaveBeenCalledWith(
      'explanation',
      30,
      10,
      expect.any(Object),
      expect.any(AbortSignal),
    )
    expect(mockStreamQuestionAi).not.toHaveBeenCalled()
  })

  it('blocks assistance before the paper question has a first answer', async () => {
    const wrapper = mount(AiQuestionAssistant, {
      props: {
        questionId: 10,
        learningSessionId: 30,
        disabled: true,
        disabledReason: '先提交本题答案',
      },
      global: { stubs },
    })

    expect(wrapper.text()).toContain('先提交本题答案')
    expect(wrapper.findAll('button').every((button) => button.attributes('disabled') !== undefined)).toBe(true)
    expect(mockStreamExamLearningAi).not.toHaveBeenCalled()
  })
})
