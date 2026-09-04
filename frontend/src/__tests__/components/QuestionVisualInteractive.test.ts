import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import QuestionVisualInteractive from '@/components/QuestionVisualInteractive.vue'
import { parseQuestionVisualContent } from '@/components/question-visual/useQuestionVisualContent'

const visualData = {
  title: '二分查找',
  summary: '在有序数组中收缩搜索区间。',
  elements: [
    { type: 'text' as const, label: '思路', content: '从中点开始判断。' },
    {
      type: 'matrix' as const,
      label: '搜索状态',
      headers: ['索引', '值'],
      rows: [
        ['0', '2'],
        ['1', { value: '5', state: 'current' as const }],
      ],
    },
    {
      type: 'bar_chart' as const,
      label: '比较次数',
      items: [
        { label: '最优', value: 1 },
        { label: '最差', value: 4 },
      ],
    },
    {
      type: 'number_line' as const,
      label: '范围',
      min: 0,
      max: 8,
      current: 4,
      markers: [{ label: '中点', position: 4 }],
    },
    {
      type: 'tree' as const,
      label: '递归树',
      root: { name: '根', state: 'current' as const, children: [{ name: '叶', children: [] }] },
    },
  ],
}

const global = {
  stubs: {
    'el-icon': { template: '<i><slot /></i>' },
    'el-alert': { props: ['title'], template: '<div class="alert-stub">{{ title }}<slot /></div>' },
    'el-empty': { template: '<div class="empty-stub" />' },
  },
}

describe('parseQuestionVisualContent', () => {
  it('accepts direct JSON and JSON embedded in a Markdown code fence', () => {
    const direct = parseQuestionVisualContent(JSON.stringify(visualData))
    const fenced = parseQuestionVisualContent(`说明\n\`\`\`json\n${JSON.stringify(visualData)}\n\`\`\``)

    expect(direct).toEqual({ data: visualData, fallbackMode: false, rawContent: '' })
    expect(fenced).toEqual({ data: visualData, fallbackMode: false, rawContent: '' })
  })

  it('keeps invalid non-empty content available for the fallback renderer', () => {
    expect(parseQuestionVisualContent('not valid JSON')).toEqual({
      data: null,
      fallbackMode: true,
      rawContent: 'not valid JSON',
    })
    expect(parseQuestionVisualContent('')).toEqual({ data: null, fallbackMode: false, rawContent: '' })
  })
})

describe('QuestionVisualInteractive', () => {
  it('keeps the existing visual element rendering contract', async () => {
    const wrapper = mount(QuestionVisualInteractive, {
      props: { content: JSON.stringify(visualData) },
      global,
    })

    expect(wrapper.text()).toContain('二分查找')
    expect(wrapper.text()).toContain('从中点开始判断。')
    expect(wrapper.find('.vi-matrix td.vi-cell--current').text()).toBe('5')
    expect(wrapper.find('.vi-bar-fill').attributes('style')).toContain('width: 25%')
    expect(wrapper.find('.vi-nl-current').attributes('style')).toContain('left: 50%')
    expect(wrapper.text()).toContain('根')
    expect(wrapper.text()).toContain('叶')

    await wrapper.setProps({ content: 'plain text fallback' })
    expect(wrapper.text()).toContain('可视化数据解析失败，已切换为文本显示')
    expect(wrapper.text()).toContain('plain text fallback')
  })

  it('prioritizes loading and keeps the empty state for missing content', async () => {
    const wrapper = mount(QuestionVisualInteractive, {
      props: { content: JSON.stringify(visualData), loading: true, loadingText: '正在生成' },
      global,
    })

    expect(wrapper.text()).toContain('正在生成')
    expect(wrapper.find('.vi-content').exists()).toBe(false)

    await wrapper.setProps({ loading: false, content: '' })
    expect(wrapper.find('.empty-stub').exists()).toBe(true)
  })
})
