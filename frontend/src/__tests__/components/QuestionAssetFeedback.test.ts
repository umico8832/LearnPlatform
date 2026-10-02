import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { getAssetFeedback, submitAssetFeedback } = vi.hoisted(() => ({
  getAssetFeedback: vi.fn(),
  submitAssetFeedback: vi.fn(),
}))

vi.mock('@/api/ai', () => ({ getAssetFeedback, submitAssetFeedback }))

import QuestionAssetFeedback from '@/components/question-learning/QuestionAssetFeedback.vue'
enableAutoUnmount(afterEach)

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

    expect(getAssetFeedback).toHaveBeenCalledWith(42, 'FULL_EXPLANATION', { errorDisplay: 'inline' })
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
    expect(submitAssetFeedback).toHaveBeenCalledWith(42, 'FULL_EXPLANATION', false, undefined, {
      errorDisplay: 'inline',
    })

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

    expect(submitAssetFeedback).toHaveBeenLastCalledWith(42, 'FULL_EXPLANATION', false, '例子不够清楚', {
      errorDisplay: 'inline',
    })
  })

  it('waits for saved feedback before offering a new write', async () => {
    let finish!: (value: unknown) => void
    getAssetFeedback.mockReturnValue(new Promise((resolve) => (finish = resolve)))
    const wrapper = mountFeedback()
    expect(wrapper.text()).toContain('正在读取反馈')
    expect(wrapper.findAll('button')).toHaveLength(0)
    finish({ code: 0, data: { helpful: true, comment: '' } })
    await flushPromises()
    expect(wrapper.text()).toContain('已反馈：有帮助')
    expect(submitAssetFeedback).not.toHaveBeenCalled()
  })

  it('retries the failed helpful choice even before any feedback exists', async () => {
    submitAssetFeedback.mockRejectedValueOnce(new Error('offline'))
    const wrapper = mountFeedback()
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '没帮助')!
      .trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('提交反馈失败')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '重试')!
      .trigger('click')
    await flushPromises()
    expect(submitAssetFeedback).toHaveBeenCalledTimes(2)
    expect(submitAssetFeedback).toHaveBeenLastCalledWith(42, 'FULL_EXPLANATION', false, undefined, {
      errorDisplay: 'inline',
    })
    expect(wrapper.text()).toContain('已反馈：没帮助')
    expect(wrapper.text()).not.toContain('提交反馈失败')
  })

  it('retains a failed comment and clears its error after retry', async () => {
    getAssetFeedback.mockResolvedValue({ code: 0, data: { helpful: false, comment: '' } })
    submitAssetFeedback.mockRejectedValueOnce(new Error('offline'))
    const wrapper = mountFeedback()
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '补充说明')!
      .trigger('click')
    await wrapper.find('textarea').setValue('缺少例子')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '提交')!
      .trigger('click')
    await flushPromises()
    expect(wrapper.find('textarea').element.value).toBe('缺少例子')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '重试')!
      .trigger('click')
    await flushPromises()
    expect(submitAssetFeedback).toHaveBeenLastCalledWith(42, 'FULL_EXPLANATION', false, '缺少例子', {
      errorDisplay: 'inline',
    })
    expect(wrapper.text()).not.toContain('提交反馈失败')
  })
})
