import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import QuestionVisualInteractive from '@/views/practice/learning/QuestionVisualInteractive.vue'
import CodeAnimationViewer from '@/views/practice/learning/CodeAnimationViewer.vue'
import SqlExecutionViewer from '@/views/practice/learning/SqlExecutionViewer.vue'
import NetworkProtocolViewer from '@/views/practice/learning/NetworkProtocolViewer.vue'
import OsProcessViewer from '@/views/practice/learning/OsProcessViewer.vue'

describe('practice-local learning visuals', () => {
  it('keeps structured and fallback visual content readable without legacy controls', async () => {
    const wrapper = mount(QuestionVisualInteractive, {
      props: {
        content: JSON.stringify({
          title: '图解',
          summary: '摘要',
          elements: [{ type: 'text', label: '说明', content: '内容' }],
        }),
      },
    })
    expect(wrapper.text()).toContain('图解')
    await wrapper.setProps({ content: '无法解析的内容' })
    expect(wrapper.get('[role="status"]').text()).toContain('已切换为文本显示')
    expect(wrapper.html()).not.toContain('el-')
  })

  it('keeps code and SQL playback keyboard-operable', async () => {
    const code = mount(CodeAnimationViewer, {
      props: {
        element: {
          type: 'code_animation',
          label: '代码',
          code: 'x = 1',
          steps: [{ lineStart: 1, lineEnd: 1, description: '赋值', variables: [] }],
        },
      },
    })
    expect(code.get('button[aria-label="播放"]')).toBeDefined()
    const sql = mount(SqlExecutionViewer, {
      props: {
        element: {
          type: 'sql_execution',
          label: '查询',
          query: 'SELECT 1',
          steps: [{ clause: 'SELECT', description: '选择' }],
        },
      },
    })
    expect(sql.get('button[aria-label="跳到第 1 步"]').attributes('aria-current')).toBe('step')
    await sql.get('button[aria-label="播放"]').trigger('click')
    expect(sql.get('button[aria-label="暂停"]')).toBeDefined()
  })

  it('keeps protocol order and process-step expansion semantic', async () => {
    const protocol = mount(NetworkProtocolViewer, {
      props: {
        element: {
          type: 'network_protocol',
          label: '协议',
          entities: ['甲', '乙'],
          messages: [{ from: 0, to: 1, content: 'SYN', state: 'current' }],
        },
      },
    })
    expect(protocol.get('li').attributes('aria-current')).toBe('step')
    const process = mount(OsProcessViewer, {
      props: { element: { type: 'os_process', label: '调度', steps: [{ description: '运行', state: [] }] } },
    })
    expect(process.get('.opv-step-header').attributes('aria-expanded')).toBe('true')
    await process.get('.opv-step-header').trigger('click')
    expect(process.get('.opv-step-header').attributes('aria-expanded')).toBe('true')
  })
})
