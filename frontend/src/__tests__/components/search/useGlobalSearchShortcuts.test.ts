import { defineComponent, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useGlobalSearchShortcuts } from '@/components/search/useGlobalSearchShortcuts'

function mountShortcuts(open = vi.fn(), close = vi.fn()) {
  const visible = ref(false)
  const wrapper = mount(
    defineComponent({
      setup: () => {
        useGlobalSearchShortcuts(visible, open, close)
        return { visible }
      },
      template: '<div />',
    }),
  )
  return { wrapper, open, close }
}

describe('useGlobalSearchShortcuts', () => {
  afterEach(() => document.body.replaceChildren())

  it('accepts an uppercase Ctrl/Meta K but never steals editable or composing input', () => {
    const { wrapper, open } = mountShortcuts()
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'K', ctrlKey: true, bubbles: true }))
    expect(open).toHaveBeenCalledTimes(1)

    const input = document.createElement('input')
    document.body.append(input)
    input.focus()
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'k', ctrlKey: true, bubbles: true }))
    window.dispatchEvent(new KeyboardEvent('keydown', { key: '/', bubbles: true, isComposing: true }))
    expect(open).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })
})
