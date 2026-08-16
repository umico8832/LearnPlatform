import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TutorArrayQueueDequeue from '@/components/TutorArrayQueueDequeue.vue'

const visualization = {
  kind: 'ARRAY_QUEUE_DEQUEUE' as const,
  version: 1 as const,
  capacity: 8,
  headIndex: 6,
  elements: ['A', 'B', 'C', 'D', 'E'],
}

const stubs = {
  'el-button': {
    emits: ['click'],
    template: '<button :disabled="$attrs.disabled" @click="$emit(\'click\')"><slot /></button>',
  },
}

describe('TutorArrayQueueDequeue', () => {
  it('replays reading the head, advancing j circularly, then decreasing n without mutating reviewed input', async () => {
    const wrapper = mount(TutorArrayQueueDequeue, { props: { visualization }, global: { stubs } })

    expect(wrapper.text()).toContain('j = 6')
    expect(wrapper.text()).toContain('n = 5')
    await wrapper.get('[data-testid="next-step"]').trigger('click')
    expect(wrapper.text()).toContain('取出队首元素 A')
    await wrapper.get('[data-testid="next-step"]').trigger('click')
    expect(wrapper.text()).toContain('(6+1) mod 8 = 7')
    expect(wrapper.text()).toContain('j = 7')
    expect(wrapper.text()).toContain('n = 5')
    expect(wrapper.get('[data-testid="array-values"]').text()).not.toContain('A')
    await wrapper.get('[data-testid="next-step"]').trigger('click')
    expect(wrapper.text()).toContain('n = 4')
    expect(wrapper.text()).toContain('B → C → D → E')
    expect(visualization.elements).toEqual(['A', 'B', 'C', 'D', 'E'])
  })
})
