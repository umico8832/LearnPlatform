import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TutorArrayQueueRepresentation from '@/components/TutorArrayQueueRepresentation.vue'

const visualization = {
  kind: 'ARRAY_QUEUE_REPRESENTATION' as const,
  version: 1 as const,
  capacity: 8,
  headIndex: 6,
  elements: ['A', 'B', 'C', 'D', 'E'],
}

const stubs = {
  'el-button': { emits: ['click'], template: '<button :disabled="$attrs.disabled" @click="$emit(\'click\')"><slot /></button>' },
}

describe('TutorArrayQueueRepresentation', () => {
  it('replays circular index mapping through wraparound without mutating reviewed input', async () => {
    const wrapper = mount(TutorArrayQueueRepresentation, { props: { visualization }, global: { stubs } })

    expect(wrapper.text()).toContain('j = 6，n = 5')
    expect(wrapper.text()).toContain('队首不必位于下标 0')

    await wrapper.get('[data-testid="next-step"]').trigger('click')
    expect(wrapper.text()).toContain('A 位于 (6+0) mod 8 = 6')

    await wrapper.get('[data-testid="next-step"]').trigger('click')
    await wrapper.get('[data-testid="next-step"]').trigger('click')
    await wrapper.get('[data-testid="next-step"]').trigger('click')
    expect(wrapper.text()).toContain('D 位于 (6+3) mod 8 = 1')

    await wrapper.get('[data-testid="next-step"]').trigger('click')
    await wrapper.get('[data-testid="next-step"]').trigger('click')
    expect(wrapper.text()).toContain('A → B → C → D → E')
    expect(visualization.elements).toEqual(['A', 'B', 'C', 'D', 'E'])
  })
})
