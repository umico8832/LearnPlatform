import { defineComponent, h, nextTick, ref } from 'vue'
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

  it('uses the final server value immediately with reduced motion', () => {
    const request = vi.fn()
    vi.stubGlobal('requestAnimationFrame', request)
    vi.stubGlobal('cancelAnimationFrame', vi.fn())
    const wrapper = mount(
      defineComponent({
        setup: () => useAnimatedNumber(0, { reducedMotion: ref(true) }),
        render: () => h('output'),
      }),
    )
    const instance = wrapper.vm as unknown as { animateTo: (target: number) => void; value: number }
    instance.animateTo(7.5)
    expect(instance.value).toBe(7.5)
    expect(request).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('cancels movement and retains the target when reduced motion changes mid-animation', async () => {
    const reduced = ref(false)
    vi.stubGlobal(
      'requestAnimationFrame',
      vi.fn(() => 42),
    )
    const cancel = vi.fn()
    vi.stubGlobal('cancelAnimationFrame', cancel)
    const wrapper = mount(
      defineComponent({
        setup: () => useAnimatedNumber(0, { reducedMotion: reduced }),
        render: () => h('output'),
      }),
    )
    const instance = wrapper.vm as unknown as { animateTo: (target: number) => void; value: number }
    instance.animateTo(250)
    reduced.value = true
    await nextTick()
    expect(cancel).toHaveBeenCalledWith(42)
    expect(instance.value).toBe(250)
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
    expect(cancelAnimationFrame).toHaveBeenCalledWith(23)
    wrapper.unmount()
    expect(cancelAnimationFrame).toHaveBeenLastCalledWith(23)
  })
})
