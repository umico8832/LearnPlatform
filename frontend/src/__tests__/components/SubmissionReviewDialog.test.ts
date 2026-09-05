import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { QuestionSubmissionVO } from '@/api/submission'

const { generateReviewComment, message, reviewSubmission } = vi.hoisted(() => ({
  generateReviewComment: vi.fn(),
  message: { error: vi.fn(), success: vi.fn(), warning: vi.fn() },
  reviewSubmission: vi.fn(),
}))

vi.mock('@/api/submission', () => ({ generateReviewComment, reviewSubmission }))
vi.mock('element-plus', () => ({ ElMessage: message }))

import SubmissionReviewDialog from '@/admin/views/submission/SubmissionReviewDialog.vue'

const submission = { id: 12, content: '待审核题目' } as QuestionSubmissionVO

describe('SubmissionReviewDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    generateReviewComment.mockResolvedValue({ code: 0, data: '答案缺少推导说明' })
    reviewSubmission.mockResolvedValue({ code: 0 })
  })

  function mountDialog() {
    return mount(SubmissionReviewDialog, {
      global: {
        stubs: {
          'el-dialog': {
            props: ['modelValue'],
            template: '<section v-if="modelValue"><slot /><slot name="footer" /></section>',
          },
          'el-form': { template: '<form><slot /></form>' },
          'el-form-item': { template: '<div><slot /></div>' },
          'el-input': {
            props: ['modelValue'],
            emits: ['update:modelValue'],
            template: '<textarea :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
          },
          'el-button': {
            emits: ['click'],
            template: '<button type="button" @click="$emit(\'click\')"><slot /></button>',
          },
        },
      },
    })
  }

  it('requires a reason when rejecting a submission', async () => {
    const wrapper = mountDialog()
    ;(wrapper.vm as unknown as { open: (value: QuestionSubmissionVO, action: number) => void }).open(submission, 2)
    await wrapper.vm.$nextTick()

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('确认拒绝'))!
      .trigger('click')

    expect(message.warning).toHaveBeenCalledWith('拒绝时请填写审核意见')
    expect(reviewSubmission).not.toHaveBeenCalled()
  })

  it('applies an AI comment and emits a refresh fact after review', async () => {
    const wrapper = mountDialog()
    ;(wrapper.vm as unknown as { open: (value: QuestionSubmissionVO, action: number) => void }).open(submission, 2)
    await wrapper.vm.$nextTick()

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('AI 一键填充'))!
      .trigger('click')
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('确认拒绝'))!
      .trigger('click')
    await flushPromises()

    expect(generateReviewComment).toHaveBeenCalledWith(12)
    expect(reviewSubmission).toHaveBeenCalledWith(12, { status: 2, reviewComment: '答案缺少推导说明' })
    expect(wrapper.emitted('reviewed')).toHaveLength(1)
  })
})
