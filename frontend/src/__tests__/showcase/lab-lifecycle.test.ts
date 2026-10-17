import { afterEach, expect, it, vi } from 'vitest'
import { shallowMount, flushPromises } from '@vue/test-utils'
import LabWindow from '@/views/home/showcase/lab/LabWindow.vue'

const { load } = vi.hoisted(() => ({ load: vi.fn() }))
vi.mock('@/viz', () => ({ findScene: () => ({ load }) }))
afterEach(() => vi.unstubAllGlobals())

it('does not install an observer when a scene finishes loading after unmount', async () => {
  let finish!: (value: null) => void
  load.mockReturnValue(
    new Promise((resolve) => {
      finish = resolve
    }),
  )
  const observer = vi.fn()
  vi.stubGlobal('IntersectionObserver', observer)
  const wrapper = shallowMount(LabWindow)
  wrapper.unmount()
  finish(null)
  await flushPromises()
  expect(observer).not.toHaveBeenCalled()
})
