import { defineComponent, h, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useReducedMotion } from '@/composables/useReducedMotion'

describe('useReducedMotion', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('reads the OS preference during setup and removes its listener when unmounted', async () => {
    const change = vi.fn()
    const media = {
      matches: true,
      addEventListener: vi.fn((type: string, listener: () => void) => {
        if (type === 'change') change.mockImplementation(listener)
      }),
      removeEventListener: vi.fn(),
    }
    vi.stubGlobal(
      'matchMedia',
      vi.fn(() => media),
    )
    const wrapper = mount(
      defineComponent({
        setup() {
          return useReducedMotion()
        },
        render() {
          return h('output', String(this.reducedMotion))
        },
      }),
    )

    expect(wrapper.text()).toBe('true')
    media.matches = false
    change({ matches: false })
    await nextTick()
    expect(wrapper.text()).toBe('false')
    wrapper.unmount()
    expect(media.removeEventListener).toHaveBeenCalled()
  })
})
