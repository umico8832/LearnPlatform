import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import NetworkProtocolViewer from '@/components/NetworkProtocolViewer.vue'
import type { NetworkProtocolElement } from '@/api/ai'

const element: NetworkProtocolElement = {
  type: 'network_protocol',
  label: 'TCP 三次握手',
  entities: ['客户端', '服务器'],
  messages: [
    { from: 0, to: 1, content: 'SYN', state: 'current' },
    { from: 1, to: 0, content: 'SYN + ACK', state: 'highlight' },
    { from: 0, to: 1, content: 'ACK' },
  ],
}

describe('协议消息的非颜色语义', () => {
  it('以有序消息、方向文字和当前/重点状态保留协议顺序', () => {
    const wrapper = mount(NetworkProtocolViewer, { props: { element } })
    const messages = wrapper.findAll('ol > li')
    expect(messages).toHaveLength(3)
    expect(messages[0]!.attributes('aria-current')).toBe('step')
    expect(messages[0]!.text()).toContain('当前步骤')
    expect(messages[0]!.text()).toContain('客户端 → 服务器')
    expect(messages[1]!.text()).toContain('重点步骤')
    expect(messages[1]!.text()).toContain('服务器 → 客户端')
    expect(messages[2]!.text()).not.toContain('当前步骤')
    expect(wrapper.get('[role="region"]').attributes('tabindex')).toBe('0')
  })

  it('装饰箭头不重复播报，端点共用实体坐标而且正反方向一致', () => {
    const wrapper = mount(NetworkProtocolViewer, { props: { element } })
    const arrows = wrapper.findAll('svg')
    expect(arrows.every((arrow) => arrow.attributes('aria-hidden') === 'true')).toBe(true)
    const forward = arrows[0]!.get('line')
    const reverse = arrows[1]!.get('line')
    expect(forward.attributes('x1')).toBe('70')
    expect(forward.attributes('x2')).toBe('250')
    expect(reverse.attributes('x1')).toBe(forward.attributes('x2'))
    expect(reverse.attributes('x2')).toBe(forward.attributes('x1'))
  })
})
