import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TutorDualArrayDequeBalance from '@/components/TutorDualArrayDequeBalance.vue'

const visualization = {
  kind: 'DUAL_ARRAY_DEQUE_BALANCE' as const,
  version: 1 as const,
  front: ['B'],
  back: ['C', 'D', 'E', 'F', 'G'],
}

const stubs = {
  'el-button': {
    emits: ['click'],
    template: '<button :disabled="$attrs.disabled" @click="$emit(\'click\')"><slot /></button>',
  },
}

describe('TutorDualArrayDequeBalance', () => {
  it('rebuilds the two stacks around the logical midpoint without mutating reviewed input', async () => {
    const wrapper = mount(TutorDualArrayDequeBalance, { props: { visualization }, global: { stubs } })

    expect(wrapper.text()).toContain('back 的大小超过 front 的三倍')
    await wrapper.get('[data-testid="next-step"]').trigger('click')
    await wrapper.get('[data-testid="next-step"]').trigger('click')
    expect(wrapper.text()).toContain('front（栈底 → 栈顶）D → C → B')
    expect(wrapper.text()).toContain('back（栈底 → 栈顶）E → F → G')
    expect(wrapper.text()).toContain('逻辑 List 顺序始终保持：B → C → D → E → F → G')
    expect(visualization.front).toEqual(['B'])
    expect(visualization.back).toEqual(['C', 'D', 'E', 'F', 'G'])
  })
})
