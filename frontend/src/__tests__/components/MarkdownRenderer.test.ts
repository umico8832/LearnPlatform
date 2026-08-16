import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'

describe('MarkdownRenderer', () => {
  it('renders empty content as empty div', () => {
    const wrapper = mount(MarkdownRenderer, { props: { content: '' } })
    expect(wrapper.find('.markdown-body').html()).toContain('class="markdown-body"')
    expect(wrapper.find('.markdown-body').text()).toBe('')
  })

  it('renders plain text', () => {
    const wrapper = mount(MarkdownRenderer, { props: { content: 'Hello world' } })
    expect(wrapper.text()).toContain('Hello world')
  })

  it('renders markdown bold text', () => {
    const wrapper = mount(MarkdownRenderer, { props: { content: '**bold text**' } })
    expect(wrapper.find('.markdown-body').find('strong').exists()).toBe(true)
    expect(wrapper.text()).toContain('bold text')
  })

  it('renders markdown headings', () => {
    const wrapper = mount(MarkdownRenderer, { props: { content: '## Heading 2' } })
    expect(wrapper.text()).toContain('Heading 2')
    // Verify content is not rendered as plain raw markdown
    expect(wrapper.html()).not.toContain('## Heading 2')
  })

  it('renders markdown lists', () => {
    const wrapper = mount(MarkdownRenderer, { props: { content: '- item 1\n- item 2\n- item 3' } })
    expect(wrapper.text()).toContain('item 1')
    expect(wrapper.text()).toContain('item 2')
    expect(wrapper.text()).toContain('item 3')
  })

  it('renders code blocks', () => {
    const wrapper = mount(MarkdownRenderer, { props: { content: '`inline code`' } })
    expect(wrapper.find('.markdown-body').find('code').exists()).toBe(true)
  })

  it('renders links', () => {
    const wrapper = mount(MarkdownRenderer, { props: { content: '[link](https://example.com)' } })
    expect(wrapper.find('.markdown-body').find('a').exists()).toBe(true)
    expect(wrapper.find('.markdown-body').find('a').attributes('href')).toBe('https://example.com')
  })

  it('sanitizes XSS content', () => {
    const xssContent = '<script>alert("xss")</script>Hello'
    const wrapper = mount(MarkdownRenderer, { props: { content: xssContent } })
    expect(wrapper.html()).not.toContain('<script>')
    expect(wrapper.text()).toContain('Hello')
  })

  it('sanitizes onclick event handler', () => {
    const xssContent = '<img src=x onerror=alert(1)>'
    const wrapper = mount(MarkdownRenderer, { props: { content: xssContent } })
    expect(wrapper.html()).not.toContain('onerror')
  })

  it('renders blockquotes', () => {
    const wrapper = mount(MarkdownRenderer, { props: { content: '> quote text' } })
    expect(wrapper.text()).toContain('quote text')
    // Verify content is not rendered as plain raw markdown
    expect(wrapper.html()).not.toContain('> quote text')
  })
})
