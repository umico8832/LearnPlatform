import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import QuestionProse from '@/views/question/QuestionProse.vue'

describe('QuestionProse', () => {
  it('renders empty content as empty div', () => {
    const wrapper = mount(QuestionProse, { props: { content: '' } })
    expect(wrapper.find('.prose').html()).toContain('class="prose"')
    expect(wrapper.find('.prose').text()).toBe('')
  })

  it('renders plain text', () => {
    const wrapper = mount(QuestionProse, { props: { content: 'Hello world' } })
    expect(wrapper.text()).toContain('Hello world')
  })

  it('renders markdown bold text', () => {
    const wrapper = mount(QuestionProse, { props: { content: '**bold text**' } })
    expect(wrapper.find('.prose').find('strong').exists()).toBe(true)
    expect(wrapper.text()).toContain('bold text')
  })

  it('renders markdown headings', () => {
    const wrapper = mount(QuestionProse, { props: { content: '## Heading 2' } })
    expect(wrapper.text()).toContain('Heading 2')
    // Verify content is not rendered as plain raw markdown
    expect(wrapper.html()).not.toContain('## Heading 2')
  })

  it('renders markdown lists', () => {
    const wrapper = mount(QuestionProse, { props: { content: '- item 1\n- item 2\n- item 3' } })
    expect(wrapper.text()).toContain('item 1')
    expect(wrapper.text()).toContain('item 2')
    expect(wrapper.text()).toContain('item 3')
  })

  it('renders code blocks', () => {
    const wrapper = mount(QuestionProse, { props: { content: '`inline code`' } })
    expect(wrapper.find('.prose').find('code').exists()).toBe(true)
  })

  it('renders links', () => {
    const wrapper = mount(QuestionProse, { props: { content: '[link](https://example.com)' } })
    expect(wrapper.find('.prose').find('a').exists()).toBe(true)
    expect(wrapper.find('.prose').find('a').attributes('href')).toBe('https://example.com')
  })

  it('sanitizes XSS content', () => {
    const xssContent = '<script>alert("xss")</script>Hello'
    const wrapper = mount(QuestionProse, { props: { content: xssContent } })
    expect(wrapper.html()).not.toContain('<script>')
    expect(wrapper.text()).toContain('Hello')
  })

  it('sanitizes onclick event handler', () => {
    const xssContent = '<img src=x onerror=alert(1)>'
    const wrapper = mount(QuestionProse, { props: { content: xssContent } })
    expect(wrapper.html()).not.toContain('onerror')
  })

  it('renders blockquotes', () => {
    const wrapper = mount(QuestionProse, { props: { content: '> quote text' } })
    expect(wrapper.text()).toContain('quote text')
    // Verify content is not rendered as plain raw markdown
    expect(wrapper.html()).not.toContain('> quote text')
  })
})
