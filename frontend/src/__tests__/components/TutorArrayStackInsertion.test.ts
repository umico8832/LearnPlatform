import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TutorArrayStackInsertion from '@/components/TutorArrayStackInsertion.vue'

const visualization = {
  kind: 'ARRAY_STACK_INSERTION' as const,
  version: 1 as const,
  capacity: 5,
  initialElements: ['A', 'B', 'C'],
  insertIndex: 1,
  insertValue: 'X',
}

const stubs = {
  'el-button': { emits: ['click'], template: '<button :disabled="$attrs.disabled" @click="$emit(\'click\')"><slot /></button>' },
}

describe('TutorArrayStackInsertion', () => {
  it('replays a bounded right-to-left insertion without mutating the reviewed input', async () => {
    const wrapper = mount(TutorArrayStackInsertion, { props: { visualization }, global: { stubs } })

    expect(wrapper.get('[data-testid="array-values"]').text()).toContain('A')
    expect(wrapper.get('[data-testid="array-values"]').text()).toContain('B')
    expect(wrapper.text()).toContain('准备插入 X 到 a[1]')

    await wrapper.get('[data-testid="next-step"]').trigger('click')
    expect(wrapper.text()).toContain('将 C 从 a[2] 移到 a[3]')
    expect(wrapper.get('[data-testid="array-values"]').text()).toContain('C')

    await wrapper.get('[data-testid="next-step"]').trigger('click')
    await wrapper.get('[data-testid="next-step"]').trigger('click')
    await wrapper.get('[data-testid="next-step"]').trigger('click')

    expect(wrapper.text()).toContain('插入完成')
    expect(wrapper.get('[data-testid="array-values"]').text()).toContain('X')
    expect(visualization.initialElements).toEqual(['A', 'B', 'C'])
  })

  it('disables backward navigation before the first state and supports keyboard stepping', async () => {
    const wrapper = mount(TutorArrayStackInsertion, { props: { visualization }, global: { stubs } })

    expect((wrapper.get('[data-testid="previous-step"]').element as HTMLButtonElement).disabled).toBe(true)
    await wrapper.get('[data-testid="courseware"]').trigger('keydown', { key: 'ArrowRight' })

    expect(wrapper.text()).toContain('将 C 从 a[2] 移到 a[3]')
  })
})
