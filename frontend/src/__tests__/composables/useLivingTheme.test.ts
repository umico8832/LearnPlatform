import { afterEach, describe, expect, it } from 'vitest'
import { defineComponent, h, KeepAlive, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { useLivingTheme } from '@/composables/useLivingTheme'

const themed = defineComponent({
  setup() {
    useLivingTheme()
    return () => h('div', 'themed')
  },
})
const plain = defineComponent({ setup: () => () => h('div', 'plain') })
const wrappers: ReturnType<typeof mount>[] = []
afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
  expect(document.documentElement.classList.contains('lt')).toBe(false)
})

describe('useLivingTheme', () => {
  it('releases the html class on deactivation and reacquires it on activation', async () => {
    const current = ref(true)
    const host = defineComponent({ setup: () => () => h(KeepAlive, null, () => h(current.value ? themed : plain)) })
    const wrapper = mount(host)
    wrappers.push(wrapper)
    expect(document.documentElement.classList.contains('lt')).toBe(true)
    current.value = false
    await wrapper.vm.$nextTick()
    expect(document.documentElement.classList.contains('lt')).toBe(false)
    current.value = true
    await wrapper.vm.$nextTick()
    expect(document.documentElement.classList.contains('lt')).toBe(true)
  })

  it('does not double count mounted plus activated or leave a class when two pages alternate', async () => {
    const first = mount(themed)
    const second = mount(themed)
    wrappers.push(first, second)
    first.unmount()
    expect(document.documentElement.classList.contains('lt')).toBe(true)
    second.unmount()
    expect(document.documentElement.classList.contains('lt')).toBe(false)
  })
})
