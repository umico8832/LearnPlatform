import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useAnimatedNumber } from '@/composables/useAnimatedNumber'

const AnimatedNumberHarness = defineComponent({
  setup() {
    return useAnimatedNumber(4)
  },
  render() {
    return h('output', String(this.value))
  },
})

describe('useAnimatedNumber', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('uses the first received server value immediately instead of presenting a fabricated zero', () => {
    const request = vi.fn()
    vi.stubGlobal('requestAnimationFrame', request)
    vi.stubGlobal('cancelAnimationFrame', vi.fn())
    const wrapper = mount(
      defineComponent({
        setup: () => useAnimatedNumber(0),
        render: () => h('output'),
      }),
    )
    const instance = wrapper.vm as unknown as { animateTo: (target: number) => void; value: number }

    instance.animateTo(72)

    expect(instance.value).toBe(72)
    expect(request).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('retains an exact fractional server value when the animation finishes', () => {
    let frame!: FrameRequestCallback
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => {
      frame = callback
      return 1
    })
    vi.stubGlobal('cancelAnimationFrame', vi.fn())
    const wrapper = mount(AnimatedNumberHarness)
    const instance = wrapper.vm as unknown as { animateTo: (target: number) => void; value: number }
    instance.animateTo(4)
    instance.animateTo(4.5)
    frame(performance.now() + 1000)
    expect(instance.value).toBe(4.5)
    wrapper.unmount()
  })

  it('cancels an in-flight value before a rapid re-trigger and at unmount', () => {
    const requestAnimationFrame = vi.fn(() => 23)
    const cancelAnimationFrame = vi.fn()
    vi.stubGlobal('requestAnimationFrame', requestAnimationFrame)
    vi.stubGlobal('cancelAnimationFrame', cancelAnimationFrame)
    const wrapper = mount(AnimatedNumberHarness)
    const instance = wrapper.vm as unknown as { animateTo: (target: number) => void }

    instance.animateTo(20)
    instance.animateTo(50)
    instance.animateTo(70)
    expect(cancelAnimationFrame).toHaveBeenCalledWith(23)
    wrapper.unmount()
    expect(cancelAnimationFrame).toHaveBeenLastCalledWith(23)
  })
})
