import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TutorArrayQueueEnqueue from '@/components/TutorArrayQueueEnqueue.vue'

const visualization = {
  kind: 'ARRAY_QUEUE_ENQUEUE' as const,
  version: 1 as const,
  capacity: 8,
  headIndex: 6,
  elements: ['A', 'B', 'C', 'D', 'E'],
  enqueueValue: 'F',
}

const stubs = {
  'el-button': {
    emits: ['click'],
    template: '<button :disabled="$attrs.disabled" @click="$emit(\'click\')"><slot /></button>',
  },
}

describe('TutorArrayQueueEnqueue', () => {
  it('replays writing at the circular tail then increasing n without mutating reviewed input', async () => {
    const wrapper = mount(TutorArrayQueueEnqueue, { props: { visualization }, global: { stubs } })

    expect(wrapper.text()).toContain('j = 6')
    expect(wrapper.text()).toContain('n = 5')
    await wrapper.get('[data-testid="next-step"]').trigger('click')
    expect(wrapper.text()).toContain('(6+5) mod 8 = 3')
    await wrapper.get('[data-testid="next-step"]').trigger('click')
    expect(wrapper.get('[data-testid="array-values"]').text()).toContain('F')
    await wrapper.get('[data-testid="next-step"]').trigger('click')
    expect(wrapper.text()).toContain('j = 6')
    expect(wrapper.text()).toContain('n = 6')
    expect(wrapper.text()).toContain('A → B → C → D → E → F')
    expect(visualization.elements).toEqual(['A', 'B', 'C', 'D', 'E'])
  })
})
