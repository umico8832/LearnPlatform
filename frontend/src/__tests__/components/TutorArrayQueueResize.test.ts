import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TutorArrayQueueResize from '@/components/TutorArrayQueueResize.vue'

const visualization = {
  kind: 'ARRAY_QUEUE_RESIZE' as const,
  version: 1 as const,
  previousCapacity: 8,
  headIndex: 6,
  elements: ['A', 'B', 'C', 'D', 'E'],
}

const stubs = {
  'el-button': { emits: ['click'], template: '<button :disabled="$attrs.disabled" @click="$emit(\'click\')"><slot /></button>' },
}

describe('TutorArrayQueueResize', () => {
  it('replays wrapped FIFO elements into a linear array and resets j without mutating reviewed input', async () => {
    const wrapper = mount(TutorArrayQueueResize, { props: { visualization }, global: { stubs } })

    expect(wrapper.text()).toContain('旧数组容量为 8')
    expect(wrapper.text()).toContain('j = 6')
    await wrapper.get('[data-testid="next-step"]').trigger('click')
    expect(wrapper.text()).toContain('分配容量为 max(1, 2n) = 10')
    await wrapper.get('[data-testid="next-step"]').trigger('click')
    expect(wrapper.get('[data-testid="new-array-values"]').text()).toContain('A')
    expect(wrapper.get('[data-testid="new-array-values"]').text()).toContain('E')
    await wrapper.get('[data-testid="next-step"]').trigger('click')
    expect(wrapper.text()).toContain('j = 0')
    expect(wrapper.text()).toContain('逻辑 FIFO 顺序：A → B → C → D → E')
    expect(visualization.elements).toEqual(['A', 'B', 'C', 'D', 'E'])
  })
})
