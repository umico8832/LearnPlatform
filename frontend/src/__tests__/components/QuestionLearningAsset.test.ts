import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

const { getQuestionAssets, getAssetFeedback, recordAssetView } = vi.hoisted(() => ({
  getQuestionAssets: vi.fn(),
  getAssetFeedback: vi.fn(),
  recordAssetView: vi.fn(),
}))

vi.mock('@/api/ai', () => ({
  getQuestionAssets,
  getAssetFeedback,
  recordAssetView,
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

describe('QuestionLearningAsset view tracking', () => {
  let intersectionCallback: IntersectionObserverCallback

  beforeEach(() => {
    vi.clearAllMocks()
    getQuestionAssets.mockResolvedValue({
      code: 0,
      data: [{
        id: 1,
        questionId: 42,
        assetType: 'FULL_EXPLANATION',
        assetTypeLabel: '标准解析',
        content: 'cached explanation',
        model: 'test-model',
        createTime: '2026-07-16T12:00:00',
      }],
    })
    getAssetFeedback.mockResolvedValue({ code: 0, data: null })
    recordAssetView.mockResolvedValue({ code: 0, data: null })

    class MockIntersectionObserver {
      constructor(callback: IntersectionObserverCallback) {
        intersectionCallback = callback
      }
      observe() {}
      disconnect() {}
      unobserve() {}
      takeRecords() { return [] }
      root = null
      rootMargin = '0px'
      thresholds = [0.1]
    }
    vi.stubGlobal('IntersectionObserver', MockIntersectionObserver)
  })

  it('records a cached asset only after the component enters the viewport', async () => {
    mount(QuestionLearningAsset, {
      props: { questionId: 42 },
      global: {
        stubs: {
          'el-tabs': { template: '<div><slot /></div>' },
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

    intersectionCallback([{ isIntersecting: true }] as IntersectionObserverEntry[], {} as IntersectionObserver)
    await nextTick()

    expect(recordAssetView).toHaveBeenCalledTimes(1)
    expect(recordAssetView).toHaveBeenCalledWith(42, 'FULL_EXPLANATION')

    intersectionCallback([{ isIntersecting: true }] as IntersectionObserverEntry[], {} as IntersectionObserver)
    await nextTick()
    expect(recordAssetView).toHaveBeenCalledTimes(1)
  })
})
