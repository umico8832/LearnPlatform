import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TutorArrayDequeRepresentation from '@/components/TutorArrayDequeRepresentation.vue'

const visualization = {
  kind: 'ARRAY_DEQUE_REPRESENTATION' as const,
  version: 1 as const,
  capacity: 8,
  headIndex: 6,
  elements: ['A', 'B', 'C', 'D', 'E'],
  accessIndex: 3,
}

const stubs = {
  'el-button': { emits: ['click'], template: '<button :disabled="$attrs.disabled" @click="$emit(\'click\')"><slot /></button>' },
}

describe('TutorArrayDequeRepresentation', () => {
  it('replays a wrapped logical access and keeps reviewed input immutable', async () => {
    const wrapper = mount(TutorArrayDequeRepresentation, { props: { visualization }, global: { stubs } })

    expect(wrapper.text()).toContain('get(3)')
    expect(wrapper.text()).toContain('j = 6，n = 5')

    await wrapper.get('[data-testid="next-step"]').trigger('click')
    await wrapper.get('[data-testid="next-step"]').trigger('click')
    expect(wrapper.text()).toContain('get(3) 访问逻辑元素 D')
    expect(wrapper.text()).toContain('(6+3) mod 8 = 1')
    expect(visualization.elements).toEqual(['A', 'B', 'C', 'D', 'E'])
  })
})
