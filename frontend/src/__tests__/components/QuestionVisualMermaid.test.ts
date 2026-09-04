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
      theme: 'default',
      securityLevel: 'loose',
      flowchart: { useMaxWidth: true, htmlLabels: true, curve: 'basis' },
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
})
