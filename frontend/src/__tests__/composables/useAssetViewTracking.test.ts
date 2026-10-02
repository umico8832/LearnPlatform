import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import type { AiAssetType } from '@/api/ai'

const mocks = vi.hoisted(() => ({ view: vi.fn(), version: 1, listeners: new Set<() => void>() }))
vi.mock('@/api/ai', () => ({ recordAssetView: mocks.view }))
vi.mock('@/utils/auth', () => ({
  getAuthSessionVersion: () => mocks.version,
  onAuthSessionChange: (fn: () => void) => {
    mocks.listeners.add(fn)
    return () => mocks.listeners.delete(fn)
  },
}))
import { useAssetViewTracking } from '@/components/question-learning/useAssetViewTracking'
enableAutoUnmount(afterEach)

let intersect: IntersectionObserverCallback
const disconnect = vi.fn()
beforeEach(() => {
  vi.clearAllMocks()
  mocks.version = 1
  mocks.listeners.clear()
  mocks.view.mockResolvedValue({ data: null })
  vi.stubGlobal(
    'IntersectionObserver',
    class {
      constructor(callback: IntersectionObserverCallback) {
        intersect = callback
      }
      observe() {}
      unobserve() {}
      disconnect = disconnect
    },
  )
})
afterEach(() => vi.unstubAllGlobals())

function setup() {
  const questionId = ref(1),
    activeType = ref<AiAssetType>('FULL_EXPLANATION'),
    show = ref(true)
  const onVariantTraining = vi.fn(),
    hasContent = vi.fn(() => true)
  let tracking!: ReturnType<typeof useAssetViewTracking>
  const wrapper = mount(
    defineComponent({
      setup() {
        tracking = useAssetViewTracking({ questionId, activeType, hasContent, onVariantTraining })
        return () => (show.value ? h('article', { ref: tracking.assetRoot }) : null)
      },
    }),
  )
  return { wrapper, questionId, activeType, show, tracking, hasContent, onVariantTraining }
}
function visible(target: Element) {
  intersect([{ target, isIntersecting: true }] as IntersectionObserverEntry[], {} as IntersectionObserver)
}

describe('asset view boundaries', () => {
  it('ignores detached targets and hidden asset types, then records the visible type once', async () => {
    const { wrapper, tracking, activeType } = setup()
    visible(document.createElement('article'))
    await flushPromises()
    expect(mocks.view).not.toHaveBeenCalled()
    visible(wrapper.element)
    await flushPromises()
    expect(mocks.view).toHaveBeenCalledTimes(1)
    await tracking.trackVisibleAsset('VARIANT')
    expect(mocks.view).toHaveBeenCalledTimes(1)
    activeType.value = 'VARIANT'
    await tracking.trackVisibleAsset('VARIANT')
    await tracking.trackVisibleAsset('VARIANT')
    expect(mocks.view).toHaveBeenCalledTimes(2)
    expect(mocks.view).toHaveBeenLastCalledWith(1, 'VARIANT', { errorDisplay: 'inline' })
  })

  it.each(['question', 'account', 'unmount'])('ignores a late variant record after %s changes', async (boundary) => {
    let finish!: (value: unknown) => void
    mocks.view.mockReturnValue(new Promise((resolve) => (finish = resolve)))
    const { wrapper, questionId, activeType, onVariantTraining } = setup()
    activeType.value = 'VARIANT'
    await nextTick()
    visible(wrapper.element)
    await nextTick()
    expect(mocks.view).toHaveBeenCalledOnce()
    if (boundary === 'question') {
      questionId.value = 2
      await nextTick()
    } else if (boundary === 'account') {
      mocks.version++
      mocks.listeners.forEach((fn) => fn())
    } else wrapper.unmount()
    finish({ data: { questionId: 1, status: 'COMPLETED', completed: true } })
    await flushPromises()
    expect(onVariantTraining).not.toHaveBeenCalled()
  })

  it('requires a newly visible element after content has been collapsed and remounted', async () => {
    const { wrapper, show, tracking, activeType } = setup()
    await nextTick()
    const oldTarget = wrapper.element
    visible(oldTarget)
    await flushPromises()
    show.value = false
    await nextTick()
    activeType.value = 'VARIANT'
    show.value = true
    await nextTick()
    visible(oldTarget)
    await tracking.trackVisibleAsset('VARIANT')
    expect(mocks.view).toHaveBeenCalledTimes(1)
    visible(wrapper.element)
    await flushPromises()
    expect(mocks.view).toHaveBeenCalledTimes(2)
  })
})
