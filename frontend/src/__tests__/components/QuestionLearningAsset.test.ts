import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

const { getQuestionAssets, getAssetFeedback, recordAssetView, completeVariantTraining, generateAsset } = vi.hoisted(
  () => ({
    getQuestionAssets: vi.fn(),
    getAssetFeedback: vi.fn(),
    recordAssetView: vi.fn(),
    completeVariantTraining: vi.fn(),
    generateAsset: vi.fn(),
  }),
)

vi.mock('@/api/ai', () => ({
  getQuestionAssets,
  getAssetFeedback,
  recordAssetView,
  completeVariantTraining,
  generateAsset,
  streamAsset: vi.fn(),
  submitAssetFeedback: vi.fn(),
}))

vi.mock('@/components/MarkdownRenderer.vue', () => ({
  default: { template: '<div class="markdown-stub" />' },
}))

vi.mock('@/components/QuestionVisualInteractive.vue', () => ({
  default: { template: '<div class="visual-stub" />' },
}))

import QuestionLearningAsset from '@/components/QuestionLearningAsset.vue'
enableAutoUnmount(afterEach)

describe('QuestionLearningAsset view tracking', () => {
  let intersectionCallback: IntersectionObserverCallback

  beforeEach(() => {
    vi.clearAllMocks()
    getQuestionAssets.mockResolvedValue({
      code: 0,
      data: [
        {
          id: 1,
          questionId: 42,
          assetType: 'FULL_EXPLANATION',
          assetTypeLabel: '标准解析',
          content: 'cached explanation',
          model: 'test-model',
          createTime: '2026-07-16T12:00:00',
        },
      ],
    })
    getAssetFeedback.mockResolvedValue({ code: 0, data: null })
    recordAssetView.mockResolvedValue({ code: 0, data: null })
    completeVariantTraining.mockResolvedValue({
      code: 0,
      message: 'success',
      data: {
        questionId: 42,
        assetId: 2,
        status: 'COMPLETED',
        completed: true,
        startedTime: '2026-07-16T12:00:00',
        completedTime: '2026-07-16T12:10:00',
      },
    })

    class MockIntersectionObserver {
      constructor(callback: IntersectionObserverCallback) {
        intersectionCallback = callback
      }
      observe() {}
      disconnect() {}
      unobserve() {}
      takeRecords() {
        return []
      }
      root = null
      rootMargin = '0px'
      thresholds = [0.1]
    }
    vi.stubGlobal('IntersectionObserver', MockIntersectionObserver)
  })

  it('records a cached asset only after the component enters the viewport', async () => {
    const wrapper = mount(QuestionLearningAsset, {
      props: { questionId: 42 },
      global: {
        stubs: {
          'el-select': {
            name: 'ElSelect',
            template: '<select><slot /></select>',
            props: ['modelValue'],
            emits: ['update:modelValue', 'change'],
          },
          'el-option': { template: '<option>{{ label }}</option>', props: ['label', 'value'] },
          'el-tab-pane': { template: '<section><slot name="label" /><slot /></section>' },
          'el-tag': { template: '<span><slot /></span>' },
          'el-button': { template: '<button><slot /></button>' },
          'el-input': { template: '<textarea />' },
          'el-alert': { template: '<div />' },
          'el-icon': { template: '<i><slot /></i>' },
        },
      },
    })
    await flushPromises()

    expect(recordAssetView).not.toHaveBeenCalled()

    intersectionCallback(
      [{ isIntersecting: true, target: wrapper.get('.asset-content').element }] as IntersectionObserverEntry[],
      {} as IntersectionObserver,
    )
    await nextTick()

    expect(recordAssetView).toHaveBeenCalledTimes(1)
    expect(recordAssetView).toHaveBeenCalledWith(42, 'FULL_EXPLANATION', { errorDisplay: 'inline' })

    intersectionCallback(
      [{ isIntersecting: true, target: wrapper.get('.asset-content').element }] as IntersectionObserverEntry[],
      {} as IntersectionObserver,
    )
    await nextTick()
    expect(recordAssetView).toHaveBeenCalledTimes(1)
  })

  it('records variant completion only after explicit user confirmation', async () => {
    getQuestionAssets.mockResolvedValue({
      code: 0,
      data: [
        {
          id: 2,
          questionId: 42,
          assetType: 'VARIANT',
          assetTypeLabel: '变式题',
          content: 'variant exercises',
          model: 'test-model',
          createTime: '2026-07-16T12:00:00',
        },
      ],
    })
    recordAssetView.mockResolvedValue({
      code: 0,
      message: 'success',
      data: {
        questionId: 42,
        assetId: 2,
        status: 'STARTED',
        completed: false,
        startedTime: '2026-07-16T12:00:00',
        completedTime: null,
      },
    })

    const wrapper = mount(QuestionLearningAsset, {
      props: { questionId: 42 },
      global: {
        stubs: {
          'el-select': {
            name: 'ElSelect',
            template: '<select><slot /></select>',
            props: ['modelValue'],
            emits: ['update:modelValue', 'change'],
          },
          'el-option': { template: '<option>{{ label }}</option>', props: ['label', 'value'] },
          'el-tab-pane': { template: '<section><slot name="label" /><slot /></section>' },
          'el-tag': { template: '<span><slot /></span>' },
          'el-button': { template: '<button :disabled="$attrs.disabled" @click="$emit(\'click\')"><slot /></button>' },
          'el-input': { template: '<textarea />' },
          'el-alert': { template: '<div />' },
          'el-icon': { template: '<i><slot /></i>' },
        },
      },
    })
    await flushPromises()

    const selector = wrapper.findComponent({ name: 'ElSelect' })
    selector.vm.$emit('update:modelValue', 'VARIANT')
    selector.vm.$emit('change', 'VARIANT')
    await nextTick()
    const completeButton = wrapper.find('.variant-training-panel button')
    expect(completeButton.exists()).toBe(true)
    expect(completeVariantTraining).not.toHaveBeenCalled()

    await completeButton.trigger('click')
    await flushPromises()

    expect(recordAssetView).toHaveBeenCalledWith(42, 'VARIANT', { errorDisplay: 'inline' })
    expect(completeVariantTraining).toHaveBeenCalledWith(42, { errorDisplay: 'inline' })
    expect(wrapper.text()).toContain('已标记完成')
  })
})
