import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TutorFactorialCallStack from '@/components/TutorFactorialCallStack.vue'

const visualization = { kind: 'FACTORIAL_CALL_STACK' as const, version: 1 as const, startValue: 4 }
const stubs = { 'el-button': { emits: ['click'], template: '<button :disabled="$attrs.disabled" @click="$emit(\'click\')"><slot /></button>' } }

describe('TutorFactorialCallStack', () => {
  it('grows to the base case before unwinding the reviewed factorial frames', async () => {
    const wrapper = mount(TutorFactorialCallStack, { props: { visualization }, global: { stubs } })
    expect(wrapper.text()).toContain('factorial(4)')
    await wrapper.get('[data-testid="next-step"]').trigger('click')
    await wrapper.get('[data-testid="next-step"]').trigger('click')
    await wrapper.get('[data-testid="next-step"]').trigger('click')
    expect(wrapper.text()).toContain('基例 factorial(1) = 1')
    expect(wrapper.findAll('[data-testid="call-frame"]')).toHaveLength(4)
    await wrapper.get('[data-testid="next-step"]').trigger('click')
    expect(wrapper.text()).toContain('factorial(2) = 2')
    expect(visualization.startValue).toBe(4)
  })
})
