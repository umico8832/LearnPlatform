import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AnswerCard from '@/views/question/AnswerCard.vue'

const props = {
  modelValue: null,
  kind: '变式 · 单选题',
  level: 3 as const,
  pending: false,
  verdict: null,
  stem: '示例题干',
  heading: '变式题',
  headingId: 'example',
  name: 'variant',
  label: '选项',
  options: ['A', 'B', 'C', 'D'].map((label) => ({ label, content: label })),
  source: { kind: 'AI_GENERATED' as const, title: null, url: null, verification: 'PENDING' as const },
}

describe('variant changed-condition copy', () => {
  it('renders code markup with the same Markdown treatment as the question', () => {
    const wrapper = mount(AnswerCard, { props: { ...props, changed: '交换 `GetTop` 与 `Pop` 的顺序。' } })
    expect(wrapper.findAll('.changed code').map((code) => code.text())).toEqual(['GetTop', 'Pop'])
    expect(wrapper.get('.changed').text()).not.toContain('`')
    wrapper.unmount()
  })

  it('keeps plain copy readable', () => {
    const wrapper = mount(AnswerCard, { props: { ...props, changed: '只改变目标值。' } })
    expect(wrapper.get('.changed').text()).toContain('只改变目标值。')
    wrapper.unmount()
  })

  it('does not expose event handlers from embedded markup', () => {
    const wrapper = mount(AnswerCard, { props: { ...props, changed: '<img src=x onerror=alert(1)>' } })
    expect(wrapper.find('.changed [onerror]').exists()).toBe(false)
    wrapper.unmount()
  })
})
