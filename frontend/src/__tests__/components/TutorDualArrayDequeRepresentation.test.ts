import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TutorDualArrayDequeRepresentation from '@/components/TutorDualArrayDequeRepresentation.vue'

const visualization = {
  kind: 'DUAL_ARRAY_DEQUE_REPRESENTATION' as const,
  version: 1 as const,
  front: ['B', 'A'],
  back: ['C', 'D', 'E'],
  accessIndex: 1,
}

const stubs = {
  'el-button': {
    emits: ['click'],
    template: '<button :disabled="$attrs.disabled" @click="$emit(\'click\')"><slot /></button>',
  },
}

describe('TutorDualArrayDequeRepresentation', () => {
  it('replays the reversed front mapping and leaves reviewed stacks unchanged', async () => {
    const wrapper = mount(TutorDualArrayDequeRepresentation, { props: { visualization }, global: { stubs } })

    expect(wrapper.text()).toContain('front 以逆序保存逻辑前缀')
    await wrapper.get('[data-testid="next-step"]').trigger('click')
    await wrapper.get('[data-testid="next-step"]').trigger('click')
    expect(wrapper.text()).toContain('逻辑下标 1 映射到 front[0]')
    expect(wrapper.text()).toContain('逻辑 List 顺序：A → B → C → D → E')
    expect(visualization.front).toEqual(['B', 'A'])
    expect(visualization.back).toEqual(['C', 'D', 'E'])
  })
})
