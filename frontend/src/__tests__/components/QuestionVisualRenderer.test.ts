import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import QuestionVisualRenderer from '@/components/question-visual/QuestionVisualRenderer.vue'

const mermaid = vi.hoisted(() => ({
  loaded: vi.fn(),
  initialize: vi.fn(),
  render: vi.fn().mockResolvedValue({ svg: '<svg><text>流程节点</text></svg>' }),
}))

vi.mock('mermaid', () => {
  mermaid.loaded()
  return { default: { initialize: mermaid.initialize, render: mermaid.render } }
})

describe('QuestionVisualRenderer Mermaid boundary', () => {
  it('does not load Mermaid for a visual asset without a Mermaid element', async () => {
    const wrapper = mount(QuestionVisualRenderer, {
      props: {
        data: {
          title: '数组',
          summary: '结构化展示。',
          elements: [{ type: 'text', label: '说明', content: '不需要图形语法。' }],
        },
      },
      global: { stubs: { MarkdownRenderer: { props: ['content'], template: '<p>{{ content }}</p>' } } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('不需要图形语法。')
    expect(mermaid.loaded).not.toHaveBeenCalled()
  })

  it('loads Mermaid only when a Mermaid element is present', async () => {
    const wrapper = mount(QuestionVisualRenderer, {
      props: {
        data: {
          title: '流程',
          summary: '按需加载。',
          elements: [{ type: 'mermaid', label: '流程图', code: 'flowchart TD\nA-->B' }],
        },
      },
    })
    expect(wrapper.get('[role="status"]').text()).toContain('正在加载图形渲染器')
    await flushPromises()
    await flushPromises()
    await vi.waitFor(() => expect(wrapper.find('svg').exists()).toBe(true))
    expect(wrapper.find('svg').text()).toContain('流程节点')
  })
})

describe('QuestionVisualRenderer lazy module recovery', () => {
  it('keeps a failed renderer import inline and retries it', async () => {
    let attempts = 0
    vi.resetModules()
    const loadMock = () => {
      attempts += 1
      if (attempts === 1) throw new Error('temporary chunk failure')
      return {
        default: { props: ['element'], template: '<div data-testid="mermaid-recovered">{{ element.label }}</div>' },
      }
    }
    vi.doMock('@/components/question-visual/QuestionVisualMermaid.vue', loadMock)
    vi.doMock('@/components/question-visual/QuestionVisualMermaid.vue?retry=1', loadMock)

    const Renderer = (await import('@/components/question-visual/QuestionVisualRenderer.vue')).default
    const wrapper = mount(Renderer, {
      props: {
        data: {
          title: '流程',
          summary: '重试加载。',
          elements: [{ type: 'mermaid', label: '流程图', code: 'flowchart TD\nA-->B' }],
        },
      },
    })
    await flushPromises()
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('图形渲染器暂时无法加载')
    await wrapper.get('button').trigger('click')
    await flushPromises()
    await flushPromises()

    expect(wrapper.get('[data-testid="mermaid-recovered"]').text()).toContain('流程图')
    vi.doUnmock('@/components/question-visual/QuestionVisualMermaid.vue')
    vi.doUnmock('@/components/question-visual/QuestionVisualMermaid.vue?retry=1')
  })
})

describe('QuestionVisualRenderer exhausted lazy recovery', () => {
  it('keeps the source after two failed imports and does not offer a cached third retry', async () => {
    let attempts = 0
    vi.resetModules()
    const failLoad = () => {
      attempts += 1
      throw new Error('chunk unavailable')
    }
    vi.doMock('@/components/question-visual/QuestionVisualMermaid.vue', failLoad)
    vi.doMock('@/components/question-visual/QuestionVisualMermaid.vue?retry=1', failLoad)

    const Renderer = (await import('@/components/question-visual/QuestionVisualRenderer.vue')).default
    const wrapper = mount(Renderer, {
      props: {
        data: {
          title: '流程',
          summary: '恢复边界。',
          elements: [{ type: 'mermaid', label: '流程图', code: 'flowchart TD\nA-->B' }],
        },
      },
    })
    await flushPromises()
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('图形渲染器暂时无法加载，已保留源代码。')
    expect(wrapper.get('[role="alert"]').text()).toContain('流程图')
    expect(wrapper.get('.vi-mermaid-source').text()).toBe('flowchart TD\nA-->B')
    await wrapper.get('button').trigger('click')
    await flushPromises()
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('刷新页面后可再试。')
    expect(wrapper.find('button').exists()).toBe(false)
    expect(attempts).toBe(2)
    vi.doUnmock('@/components/question-visual/QuestionVisualMermaid.vue')
    vi.doUnmock('@/components/question-visual/QuestionVisualMermaid.vue?retry=1')
  })
})
