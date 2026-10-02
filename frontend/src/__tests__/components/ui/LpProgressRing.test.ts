import { mount } from '@vue/test-utils'
import { expect, it } from 'vitest'
import LpProgressRing from '@/components/ui/LpProgressRing.vue'

it('exposes the numeric progress with an explicit accessible label', () => {
  const wrapper = mount(LpProgressRing, { props: { percent: 65, label: '课程完成度' } })
  const progress = wrapper.get('[role="progressbar"]')

  expect(progress.attributes()).toMatchObject({
    'aria-label': '课程完成度',
    'aria-valuenow': '65',
    'aria-valuetext': '课程完成度 65%',
  })
})

it('does not claim zero when the value is unavailable', () => {
  const wrapper = mount(LpProgressRing, { props: { percent: Number.NaN, label: '正确率' } })
  const progress = wrapper.get('[role="progressbar"]')

  expect(progress.attributes('aria-valuenow')).toBeUndefined()
  expect(progress.attributes('aria-valuetext')).toBe('正确率暂不可用')
})
