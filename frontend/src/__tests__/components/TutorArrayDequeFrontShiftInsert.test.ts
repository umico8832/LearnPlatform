import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TutorArrayDequeFrontShiftInsert from '@/components/TutorArrayDequeFrontShiftInsert.vue'

const visualization = {
  kind: 'ARRAY_DEQUE_FRONT_SHIFT_INSERT' as const,
  version: 1 as const,
  capacity: 8,
  headIndex: 2,
  elements: ['A', 'B', 'C', 'D', 'E'],
  insertIndex: 1,
  insertValue: 'X',
}

const stubs = {
  'el-button': { emits: ['click'], template: '<button :disabled="$attrs.disabled" @click="$emit(\'click\')"><slot /></button>' },
}

describe('TutorArrayDequeFrontShiftInsert', () => {
  it('replays the front-side branch by moving only the prefix and wrapping j', async () => {
    const wrapper = mount(TutorArrayDequeFrontShiftInsert, { props: { visualization }, global: { stubs } })

    expect(wrapper.text()).toContain('i = 1 < n / 2 = 2.5')
    expect(wrapper.text()).toContain('只搬移前缀中的 1 个元素')

    await wrapper.get('[data-testid="next-step"]').trigger('click')
    expect(wrapper.text()).toContain('j 从 2 回绕到 1')

    await wrapper.get('[data-testid="next-step"]').trigger('click')
    expect(wrapper.text()).toContain('将 A 从 a[2] 移到 a[1]')

    await wrapper.get('[data-testid="next-step"]').trigger('click')
    expect(wrapper.text()).toContain('逻辑 List 顺序：A → X → B → C → D → E')
    expect(visualization.elements).toEqual(['A', 'B', 'C', 'D', 'E'])
  })
})
