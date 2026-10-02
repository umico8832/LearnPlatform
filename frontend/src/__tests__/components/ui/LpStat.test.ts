import { mount } from '@vue/test-utils'
import { expect, it, vi } from 'vitest'
import LpStat from '@/components/ui/LpStat.vue'

it('renders its first finite value without counting up from zero', () => {
  const request = vi.fn()
  vi.stubGlobal('requestAnimationFrame', request)
  const wrapper = mount(LpStat, { props: { label: '已作答', value: 16 } })

  expect(wrapper.get('.lp-stat-value').text()).toBe('16')
  expect(request).not.toHaveBeenCalled()
})

it('renders an unavailable value as provided instead of inventing zero', () => {
  const wrapper = mount(LpStat, { props: { label: '正确率', value: '—' } })

  expect(wrapper.get('.lp-stat-value').text()).toBe('—')
})
