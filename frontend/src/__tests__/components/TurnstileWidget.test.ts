import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import TurnstileWidget from '@/components/auth/TurnstileWidget.vue'

describe('TurnstileWidget lifecycle', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_TURNSTILE_SITE_KEY', 'test-site-key')
  })
  afterEach(() => {
    vi.unstubAllEnvs()
    delete window.turnstile
  })

  it('removes the challenge on unmount and ignores callbacks from the abandoned widget', async () => {
    const render = vi.fn().mockReturnValue('widget-id')
    const remove = vi.fn()
    window.turnstile = { render, remove, reset: vi.fn() }
    const w = mount(TurnstileWidget, { props: { modelValue: '', theme: 'light' } })
    await flushPromises()
    const options = render.mock.calls[0][1]
    w.unmount()
    expect(remove).toHaveBeenCalledWith('widget-id')
    options.callback('stale-token')
    options['expired-callback']()
    options['error-callback']()
    expect(w.emitted('update:modelValue')).toBeUndefined()
    expect(w.emitted('error')).toBeUndefined()
    expect(w.emitted('expired')).toBeUndefined()
  })

  it('reports missing configuration without issuing a token', async () => {
    vi.stubEnv('VITE_TURNSTILE_SITE_KEY', '')
    const w = mount(TurnstileWidget, { props: { modelValue: '' } })
    await flushPromises()
    expect(w.get('[role="alert"]').text()).toContain('尚未配置')
    expect(w.emitted('update:modelValue')).toEqual([['']])
    expect(w.emitted('error')).toHaveLength(1)
    w.unmount()
  })
})
