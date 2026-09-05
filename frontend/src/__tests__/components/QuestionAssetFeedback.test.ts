import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { getAssetFeedback, submitAssetFeedback } = vi.hoisted(() => ({
  getAssetFeedback: vi.fn(),
  submitAssetFeedback: vi.fn(),
}))

vi.mock('@/api/ai', () => ({ getAssetFeedback, submitAssetFeedback }))

import QuestionAssetFeedback from '@/components/question-learning/QuestionAssetFeedback.vue'

describe('QuestionAssetFeedback', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getAssetFeedback.mockResolvedValue({ code: 0, data: null })
    submitAssetFeedback.mockResolvedValue({ code: 0 })
  })

  function mountFeedback() {
    return mount(QuestionAssetFeedback, {
      props: { questionId: 42, assetType: 'FULL_EXPLANATION', available: true },
      global: {
        stubs: {
          'el-button': {
            emits: ['click'],
            template: '<button @click="$emit(\'click\')"><slot /></button>',
          },
          'el-tag': { template: '<span><slot /></span>' },
          'el-input': {
            props: ['modelValue'],
            emits: ['update:modelValue'],
            template: '<textarea :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
          },
        },
      },
    })
  }

  it('loads existing feedback when the asset becomes available', async () => {
    getAssetFeedback.mockResolvedValue({ code: 0, data: { helpful: true, comment: '' } })
    const wrapper = mountFeedback()
    await flushPromises()

    expect(getAssetFeedback).toHaveBeenCalledWith(42, 'FULL_EXPLANATION')
    expect(wrapper.text()).toContain('已反馈：有帮助')
  })

  it('owns negative feedback and its optional follow-up comment', async () => {
    const wrapper = mountFeedback()
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('没帮助'))!
      .trigger('click')
    await flushPromises()
    expect(submitAssetFeedback).toHaveBeenCalledWith(42, 'FULL_EXPLANATION', false)

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('补充说明'))!
      .trigger('click')
    await wrapper.find('textarea').setValue('例子不够清楚')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '提交')!
      .trigger('click')
    await flushPromises()

    expect(submitAssetFeedback).toHaveBeenLastCalledWith(42, 'FULL_EXPLANATION', false, '例子不够清楚')
  })
})
