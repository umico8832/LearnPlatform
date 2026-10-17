import { afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, provide, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { heroActive, useHeroFrames } from '@/views/home/showcase/hero/activity'
import { Halt, rest } from '@/views/home/showcase/hero/ghost'

afterEach(() => {
  vi.unstubAllGlobals()
  vi.useRealTimers()
})

describe('homepage background work', () => {
  it('stops continuous frames outside the hero and disposes the resumed loop', () => {
    let callback: FrameRequestCallback | undefined
    let pending = 0
    vi.stubGlobal('requestAnimationFrame', (cb: FrameRequestCallback) => {
      callback = cb
      pending++
      return 1
    })
    vi.stubGlobal('cancelAnimationFrame', () => {
      pending = 0
    })
    const active = ref(true)
    const tick = vi.fn()
    const Scene = defineComponent({
      setup() {
        useHeroFrames(tick)
        return () => null
      },
    })
    const Host = defineComponent({
      setup() {
        provide(heroActive, active)
      },
      components: { Scene },
      template: '<Scene />',
    })
    const wrapper = mount(Host)
    expect(pending).toBe(1)
    pending--
    callback!(16)
    expect(tick).toHaveBeenCalledTimes(1)
    active.value = false
    expect(pending).toBe(0)
    active.value = true
    expect(pending).toBe(1)
    wrapper.unmount()
    expect(pending).toBe(0)
  })

  it('releases abort listeners on completed waits and cancels pending waits', async () => {
    vi.useFakeTimers()
    const controller = new AbortController()
    const add = vi.spyOn(controller.signal, 'addEventListener')
    const remove = vi.spyOn(controller.signal, 'removeEventListener')
    for (let i = 0; i < 20; i++) {
      const wait = rest(10, controller.signal)
      await vi.advanceTimersByTimeAsync(10)
      await wait
    }
    expect(add).toHaveBeenCalledTimes(20)
    expect(remove).toHaveBeenCalledTimes(20)
    const stopped = expect(rest(1000, controller.signal)).rejects.toBeInstanceOf(Halt)
    controller.abort()
    await stopped
    expect(vi.getTimerCount()).toBe(0)
  })
})
