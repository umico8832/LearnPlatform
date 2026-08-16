import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TutorArrayStackResize from '@/components/TutorArrayStackResize.vue'

const visualization = {
  kind: 'ARRAY_STACK_RESIZE' as const,
  version: 1 as const,
  previousCapacity: 3,
  initialElements: ['A', 'B', 'C'],
}

const stubs = {
  'el-button': {
    emits: ['click'],
    template: '<button :disabled="$attrs.disabled" @click="$emit(\'click\')"><slot /></button>',
  },
}

describe('TutorArrayStackResize', () => {
  it('replays allocation, ordered copy, and replacement without mutating the reviewed input', async () => {
    const wrapper = mount(TutorArrayStackResize, { props: { visualization }, global: { stubs } })

    expect(wrapper.text()).toContain('旧数组容量为 3')
    expect(wrapper.text()).toContain('A')

    await wrapper.get('[data-testid="next-step"]').trigger('click')
    expect(wrapper.text()).toContain('分配容量为 6 的新数组')

    await wrapper.get('[data-testid="next-step"]').trigger('click')
    expect(wrapper.text()).toContain('将 A 从旧数组 a[0] 复制到新数组 b[0]')

    await wrapper.get('[data-testid="next-step"]').trigger('click')
    await wrapper.get('[data-testid="next-step"]').trigger('click')
    await wrapper.get('[data-testid="next-step"]').trigger('click')
    expect(wrapper.text()).toContain('令 a 指向新数组')
    expect(visualization.initialElements).toEqual(['A', 'B', 'C'])
  })
})
