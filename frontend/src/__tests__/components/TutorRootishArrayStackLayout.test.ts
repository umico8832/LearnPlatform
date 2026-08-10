import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TutorRootishArrayStackLayout from '@/components/TutorRootishArrayStackLayout.vue'

const visualization = { kind: 'ROOTISH_ARRAY_STACK_LAYOUT' as const, version: 1 as const, blocks: [['A'], ['B', 'C'], ['D', 'E', 'F']] }
const stubs = { 'el-button': { emits: ['click'], template: '<button :disabled="$attrs.disabled" @click="$emit(\'click\')"><slot /></button>' } }

describe('TutorRootishArrayStackLayout', () => {
  it('replays increasing blocks and retains the reviewed layout', async () => {
    const wrapper = mount(TutorRootishArrayStackLayout, { props: { visualization }, global: { stubs } })
    expect(wrapper.text()).toContain('块 0 的容量为 1')
    await wrapper.get('[data-testid="next-step"]').trigger('click')
    await wrapper.get('[data-testid="next-step"]').trigger('click')
    expect(wrapper.text()).toContain('总容量 1 + 2 + 3 = 6')
    expect(wrapper.text()).toContain('逻辑顺序：A → B → C → D → E → F')
    expect(visualization.blocks).toEqual([['A'], ['B', 'C'], ['D', 'E', 'F']])
  })
})
