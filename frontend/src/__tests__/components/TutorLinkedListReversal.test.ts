import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TutorLinkedListReversal from '@/components/TutorLinkedListReversal.vue'

const visualization = { kind: 'LINKED_LIST_REVERSAL' as const, version: 1 as const, elements: ['A', 'B', 'C'] }
const stubs = { 'el-button': { emits: ['click'], template: '<button :disabled="$attrs.disabled" @click="$emit(\'click\')"><slot /></button>' } }

describe('TutorLinkedListReversal', () => {
  it('shows the saved successor and grows the reversed prefix one node at a time', async () => {
    const wrapper = mount(TutorLinkedListReversal, { props: { visualization }, global: { stubs } })
    expect(wrapper.text()).toContain('prev = null，cur = A')
    await wrapper.get('[data-testid="next-step"]').trigger('click')
    expect(wrapper.text()).toContain('先保存 next = B')
    expect(wrapper.text()).toContain('已逆置：A → null')
    await wrapper.get('[data-testid="next-step"]').trigger('click')
    await wrapper.get('[data-testid="next-step"]').trigger('click')
    expect(wrapper.text()).toContain('新表头为 C')
    expect(wrapper.text()).toContain('C → B → A → null')
    expect(visualization.elements).toEqual(['A', 'B', 'C'])
  })
})
