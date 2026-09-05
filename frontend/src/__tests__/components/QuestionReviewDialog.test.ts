import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { getReviewRecords, getReviewSuggestion, message, performReReview } = vi.hoisted(() => ({
  getReviewRecords: vi.fn(),
  getReviewSuggestion: vi.fn(),
  message: { success: vi.fn(), warning: vi.fn() },
  performReReview: vi.fn(),
}))

vi.mock('@/api/question', () => ({ getReviewRecords, getReviewSuggestion, performReReview }))
vi.mock('element-plus', () => ({ ElMessage: message }))

import QuestionReviewDialog from '@/admin/views/question/QuestionReviewDialog.vue'

const passthrough = { template: '<div><slot name="title" /><slot /></div>' }

describe('QuestionReviewDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getReviewRecords.mockResolvedValue({ data: [] })
    getReviewSuggestion.mockResolvedValue({
      data: {
        recommendation: 'REVISE',
        confidenceScore: 88,
        summary: '补充边界条件',
        suggestedContent: '修订后的题干',
        suggestedDifficulty: 4,
        riskPoints: [],
        suggestions: [],
      },
    })
    performReReview.mockResolvedValue(undefined)
  })

  it('loads review context and applies an AI suggestion before submission', async () => {
    const wrapper = mount(QuestionReviewDialog, {
      global: {
        stubs: {
          'el-dialog': {
            props: ['modelValue'],
            template: '<section v-if="modelValue"><slot /><slot name="footer" /></section>',
          },
          'el-button': { emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
          'el-descriptions': passthrough,
          'el-descriptions-item': passthrough,
          'el-tag': passthrough,
          'el-form': passthrough,
          'el-form-item': passthrough,
          'el-radio-group': passthrough,
          'el-radio-button': passthrough,
          'el-input': true,
          'el-rate': true,
          'el-alert': passthrough,
          'el-timeline': passthrough,
          'el-timeline-item': passthrough,
          'el-card': passthrough,
        },
      },
    })

    await (wrapper.vm as unknown as { open: (question: object) => Promise<void> }).open({
      id: 12,
      content: '原题干',
      questionType: 'SINGLE_CHOICE',
      difficulty: 3,
      sourceType: 'MANUAL',
      reviewRounds: 0,
    })
    await flushPromises()
    expect(getReviewRecords).toHaveBeenCalledWith(12)

    const clickButton = async (label: string) => {
      const button = wrapper.findAll('button').find((item) => item.text().includes(label))
      expect(button).toBeDefined()
      await button!.trigger('click')
      await flushPromises()
    }

    await clickButton('AI 复审建议')
    expect(getReviewSuggestion).toHaveBeenCalledWith(12)
    await clickButton('应用到表单')
    await clickButton('提交复审')

    expect(performReReview).toHaveBeenCalledWith(12, {
      action: 'REVISE',
      newContent: '修订后的题干',
      newDifficulty: 4,
      comment: '补充边界条件',
    })
    expect(wrapper.emitted('reviewed')).toHaveLength(1)
  })
})
