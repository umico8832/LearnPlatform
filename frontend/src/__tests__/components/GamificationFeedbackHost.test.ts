import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import GamificationFeedbackHost from '@/components/gamification/GamificationFeedbackHost.vue'
import { createCelebrationScheduler } from '@/utils/celebrationScheduler'

vi.mock('@/utils/celebrationConfetti', () => ({ launchCelebrationConfetti: vi.fn() }))

describe('GamificationFeedbackHost', () => {
  afterEach(() => vi.useRealTimers())

  it('allows Escape to dismiss a non-modal reward notice', async () => {
    const scheduler = createCelebrationScheduler()
    const wrapper = mount(GamificationFeedbackHost, { props: { scheduler } })
    scheduler.enqueue({
      id: 'achievement',
      kind: 'achievement',
      context: 'learning',
      achievements: [{ id: 'first', title: '首次作答' }],
    })
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('首次作答')
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).not.toContain('首次作答')
    wrapper.unmount()
  })

  it('cleans up its auto-dismiss timer when unmounted', async () => {
    vi.useFakeTimers()
    const scheduler = createCelebrationScheduler()
    const wrapper = mount(GamificationFeedbackHost, { props: { scheduler } })
    scheduler.enqueue({
      id: 'achievement',
      kind: 'achievement',
      context: 'learning',
      achievements: [{ id: 'first', title: '首次作答' }],
    })
    await wrapper.vm.$nextTick()
    wrapper.unmount()
    vi.runAllTimers()
    expect(scheduler.current()).toBeDefined()
  })
})
