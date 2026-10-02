import { mount } from '@vue/test-utils'
import { expect, it } from 'vitest'
import LpProgress from '@/components/ui/LpProgress.vue'

it('does not present NaN as zero progress', () => {
  const wrapper = mount(LpProgress, { props: { percent: Number.NaN, label: '答题进度', showLabel: true } })
  const progress = wrapper.get('[role="progressbar"]')

  expect(progress.attributes('aria-valuenow')).toBeUndefined()
  expect(progress.attributes('aria-label')).toBe('答题进度')
  expect(progress.attributes('aria-valuetext')).toBe('答题进度暂不可用')
  expect(wrapper.get('.lp-progress-fill').attributes('data-available')).toBe('false')
})

it('clamps a finite value to the progressbar range', () => {
  const wrapper = mount(LpProgress, { props: { percent: 140 } })

  expect(wrapper.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('100')
  expect(wrapper.get('.lp-progress-fill').attributes('style')).toContain('width: 100%')
})
