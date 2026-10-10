import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ChoiceList from '@/views/question/ChoiceList.vue'

vi.mock('@/motion', () => ({ springAnimate: vi.fn(), springs: { pointer: {} } }))

const options = [
  { label: 'A', content: '第一项' },
  { label: 'B', content: '第二项' },
  { label: 'C', content: '第三项' },
]
const wrappers: ReturnType<typeof mount>[] = []
function render(extra: Record<string, unknown> = {}) {
  const wrapper = mount(ChoiceList, {
    props: { options, name: 'answer', label: '选项', locked: false, active: true, modelValue: null, ...extra },
  })
  wrappers.push(wrapper)
  return wrapper
}
function key(key: string, target: HTMLElement = document.body) {
  target.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }))
}
afterEach(() => wrappers.splice(0).forEach((wrapper) => wrapper.unmount()))

describe('ChoiceList', () => {
  it('selects by letter and submits with Enter while active', async () => {
    const wrapper = render()
    key('b')
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['B'])
    await wrapper.setProps({ modelValue: 'B' })
    key('Enter')
    expect(wrapper.emitted('submit')).toHaveLength(1)
  })

  it('ignores keys in a visualization or text input and when inactive', async () => {
    const wrapper = render({ active: false })
    key('A')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    await wrapper.setProps({ active: true })
    const viz = document.createElement('div')
    viz.className = 'viz'
    const input = document.createElement('input')
    document.body.append(viz, input)
    try {
      key('A', viz)
      key('B', input)
      key('Enter', input)
      expect(wrapper.emitted('update:modelValue')).toBeUndefined()
      expect(wrapper.emitted('submit')).toBeUndefined()
    } finally {
      viz.remove()
      input.remove()
    }
  })

  it('marks wrong, right and dim states with screen-reader text after locking', () => {
    const wrapper = render({ locked: true, active: false, modelValue: 'B', chosen: 'B', correct: 'A' })
    const rows = wrapper.findAll('.choice')
    expect(rows.map((row) => row.attributes('data-state'))).toEqual(['right', 'wrong', 'dim'])
    expect(rows[0].find('.sr-only').text()).toContain('正确答案')
    expect(rows[1].find('.sr-only').text()).toContain('你的选择，错误')
    expect(rows[2].find('.sr-only').text()).toBe('')
    expect(wrapper.findAll('input:disabled')).toHaveLength(3)
  })

  it('reads a correct chosen option as both own choice and correct', () => {
    const wrapper = render({ locked: true, active: false, modelValue: 'A', chosen: 'A', correct: 'A' })
    expect(wrapper.find('.choice .sr-only').text()).toContain('你的选择，正确')
  })
})
