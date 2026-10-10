import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { collapseInto, expandInto } from '@/viz/expandTransition'
import VizPlayer from '@/viz/VizPlayer.vue'
import { Player } from '@/viz/engine/player'

const motion = vi.hoisted(() => ({ reduced: true }))
vi.mock('@/motion', async (original) => ({
  ...(await original<typeof import('@/motion')>()),
  prefersReducedMotion: () => motion.reduced,
}))

const spec = { scene: 'stack', op: 'check', seq: [3, 2, 1], pauseAt: [] }
const router = createRouter({
  history: createMemoryHistory(),
  routes: [{ path: '/viz/:scene', name: 'Visualization', component: { template: '<div />' } }],
})

afterEach(() => {
  motion.reduced = true
})

describe('visualization expansion', () => {
  it('navigates directly when no origin card exists or motion is reduced', async () => {
    const first = vi.fn(async () => undefined)
    expandInto(null, first)
    await flushPromises()
    expect(first).toHaveBeenCalledOnce()

    const card = document.createElement('div')
    document.body.append(card)
    const second = vi.fn(async () => undefined)
    expandInto(card, second)
    await flushPromises()
    expect(second).toHaveBeenCalledOnce()
    const back = vi.fn(async () => undefined)
    collapseInto(back)
    await flushPromises()
    expect(back).toHaveBeenCalledOnce()
    card.remove()
  })

  it('links an embedded player to its own scene and hides the link for expandTo=false', async () => {
    await router.push('/viz/stack')
    await router.isReady()
    const normal = mount(VizPlayer, { props: { spec }, global: { plugins: [router] } })
    await vi.waitFor(() => expect(normal.get('a.viz-expand').attributes('href')).toMatch(/^\/viz\/stack\?/))
    const pause = vi.spyOn(Player.prototype, 'pause')
    await normal.get('a.viz-expand').trigger('click')
    expect(pause).toHaveBeenCalledOnce()
    pause.mockRestore()
    normal.unmount()

    const hidden = mount(VizPlayer, { props: { spec, expandTo: false }, global: { plugins: [router] } })
    await flushPromises()
    expect(hidden.find('a.viz-expand').exists()).toBe(false)
    hidden.unmount()
  })
})
