import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TutorSequentialListStorage from '@/components/TutorSequentialListStorage.vue'

const visualization = {
  kind: 'SEQUENTIAL_LIST_STORAGE' as const,
  version: 1 as const,
  baseAddress: 1000,
  elementWidth: 4,
  elements: ['A', 'B', 'C', 'D'],
  accessIndex: 2,
}
const stubs = {
  'el-button': {
    emits: ['click'],
    template: '<button :disabled="$attrs.disabled" @click="$emit(\'click\')"><slot /></button>',
  },
}

describe('TutorSequentialListStorage', () => {
  it('derives the target address from reviewed base address and element width', async () => {
    const wrapper = mount(TutorSequentialListStorage, { props: { visualization }, global: { stubs } })
    expect(wrapper.text()).toContain('LOC(a1) = 1000')
    await wrapper.get('[data-testid="next-step"]').trigger('click')
    await wrapper.get('[data-testid="next-step"]').trigger('click')
    expect(wrapper.text()).toContain('LOC(a3) = 1000 + 2 × 4 = 1008')
    expect(wrapper.text()).toContain('a3 = C')
    expect(visualization.elements).toEqual(['A', 'B', 'C', 'D'])
  })
})
