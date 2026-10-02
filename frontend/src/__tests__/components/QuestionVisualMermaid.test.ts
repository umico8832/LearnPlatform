import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { initialize, render } = vi.hoisted(() => ({
  initialize: vi.fn(),
  render: vi.fn(),
}))

vi.mock('mermaid', () => ({
  default: { initialize, render },
}))

import QuestionVisualMermaid from '@/components/question-visual/QuestionVisualMermaid.vue'

describe('QuestionVisualMermaid', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    render.mockResolvedValue({ svg: '<svg aria-label="流程图"><text>初始图</text></svg>' })
  })

  it('renders Mermaid SVG and falls back to the source when rendering fails', async () => {
    const wrapper = mount(QuestionVisualMermaid, {
      props: {
        element: { type: 'mermaid', label: '流程', code: 'flowchart TD\nA-->B' },
      },
    })
    await flushPromises()

    expect(initialize).toHaveBeenCalledWith({
      startOnLoad: false,
      theme: 'base',
      securityLevel: 'strict',
      htmlLabels: false,
      themeVariables: expect.objectContaining({
        primaryColor: expect.any(String),
        primaryTextColor: expect.any(String),
        primaryBorderColor: expect.any(String),
        lineColor: expect.any(String),
        textColor: expect.any(String),
      }),
      flowchart: { useMaxWidth: true, htmlLabels: false, curve: 'basis' },
    })
    expect(render).toHaveBeenCalledWith(expect.stringMatching(/^mermaid-\d+-\d+$/), 'flowchart TD\nA-->B')
    expect(wrapper.find('svg').text()).toContain('初始图')

    render.mockRejectedValueOnce(new Error('invalid syntax'))
    await wrapper.setProps({
      element: { type: 'mermaid', label: '流程', code: 'invalid diagram' },
    })
    await flushPromises()

    expect(wrapper.find('.vi-mermaid-error').text()).toBe('invalid diagram')
  })

  it('sanitizes executable content from rendered SVG', async () => {
    render.mockResolvedValue({
      svg: '<svg xmlns="http://www.w3.org/2000/svg"><text>safe</text><foreignObject><div>HTML label</div></foreignObject><script>window.__xss = true</script><a href="javascript:alert(1)"><text>unsafe</text></a></svg>',
    })
    const wrapper = mount(QuestionVisualMermaid, {
      props: { element: { type: 'mermaid', label: '流程', code: 'flowchart TD\nA-->B' } },
    })
    await flushPromises()

    expect(wrapper.html()).not.toContain('<script>')
    expect(wrapper.html()).not.toContain('javascript:')
    expect(wrapper.find('foreignObject').exists()).toBe(false)
    expect(wrapper.find('svg').text()).toContain('safe')
  })

  it('fits the SVG viewport to every rendered node', async () => {
    const previousGetBBox = SVGSVGElement.prototype.getBBox
    Object.defineProperty(SVGSVGElement.prototype, 'getBBox', {
      configurable: true,
      value: () => ({ x: 8, y: 8, width: 296, height: 318 }),
    })
    try {
      const wrapper = mount(QuestionVisualMermaid, {
        props: { element: { type: 'mermaid', label: '流程', code: 'flowchart TD\nA-->B' } },
      })
      await flushPromises()

      const svg = wrapper.find('svg')
      expect(svg.attributes('viewBox')).toBe('0 0 312 334')
      expect(svg.attributes('height')).toBe('334')
    } finally {
      Object.defineProperty(SVGSVGElement.prototype, 'getBBox', {
        configurable: true,
        value: previousGetBBox,
      })
    }
  })

  it('falls back to source when lazy Mermaid setup fails', async () => {
    await vi.resetModules()
    initialize.mockImplementationOnce(() => {
      throw new Error('module setup failed')
    })
    const { default: FreshQuestionVisualMermaid } =
      await import('@/components/question-visual/QuestionVisualMermaid.vue')
    const wrapper = mount(FreshQuestionVisualMermaid, {
      props: { element: { type: 'mermaid', label: '流程', code: 'flowchart TD\nA-->B' } },
    })
    await flushPromises()

    expect(wrapper.find('.vi-mermaid-error').text()).toBe('flowchart TD\nA-->B')
    expect(wrapper.find('.vi-mermaid-error').attributes('role')).toBe('status')
  })
})

it('does not write a late Mermaid render after unmount', async () => {
  let resolve!: (value: { svg: string }) => void
  render.mockImplementationOnce(
    () =>
      new Promise((done) => {
        resolve = done
      }),
  )
  const wrapper = mount(QuestionVisualMermaid, {
    props: { element: { type: 'mermaid', label: '流程', code: 'flowchart TD\nA-->B' } },
  })
  await flushPromises()
  wrapper.unmount()
  resolve({ svg: '<svg><text>迟到图形</text></svg>' })
  await flushPromises()
  expect(wrapper.find('svg').exists()).toBe(false)
})
