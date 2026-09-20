import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { mockGet, mockStart, mockResume } = vi.hoisted(() => ({
  mockGet: vi.fn(),
  mockStart: vi.fn(),
  mockResume: vi.fn(),
}))

vi.mock('@/api/tutor', () => ({
  getTutorAgentRun: (...args: unknown[]) => mockGet(...args),
  startTutorAgentRun: (...args: unknown[]) => mockStart(...args),
  resumeTutorAgentRun: (...args: unknown[]) => mockResume(...args),
}))

import TutorAgentConversation from '@/components/course/TutorAgentConversation.vue'

const stubs = {
  'el-button': {
    template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
    props: ['disabled', 'loading', 'type'],
    emits: ['click'],
  },
  'el-input': {
    template:
      '<textarea data-testid="agent-input" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    props: ['modelValue', 'type', 'rows', 'maxlength', 'showWordLimit', 'disabled', 'placeholder'],
    emits: ['update:modelValue'],
  },
  'el-alert': { template: '<div>{{ title }}</div>', props: ['title', 'type', 'closable', 'showIcon'] },
}

function mountAgent() {
  return mount(TutorAgentConversation, {
    props: { courseId: 10, sessionKey: 'session' },
    global: { stubs },
  })
}

describe('TutorAgentConversation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    mockStart.mockResolvedValue({
      data: {
        runKey: 'run',
        status: 'WAITING_USER',
        messages: [
          { sequence: 1, role: 'USER', content: '为什么从右向左搬？', createTime: null },
          { sequence: 2, role: 'ASSISTANT', content: '这样不会覆盖尚未读取的元素。', createTime: null },
        ],
      },
    })
    mockResume.mockResolvedValue({
      data: {
        runKey: 'run',
        status: 'WAITING_USER',
        messages: [
          { sequence: 1, role: 'USER', content: '为什么从右向左搬？', createTime: null },
          { sequence: 2, role: 'ASSISTANT', content: '这样不会覆盖尚未读取的元素。', createTime: null },
          { sequence: 3, role: 'USER', content: '换个例子', createTime: null },
          { sequence: 4, role: 'ASSISTANT', content: '可以把它看成给书架腾位置。', createTime: null },
        ],
      },
    })
  })

  it('starts a run, renders safe text, and resumes the same run', async () => {
    const wrapper = mountAgent()
    expect(wrapper.text()).toContain('只依据本节已审查内容')

    await wrapper.get('[data-testid="agent-input"]').setValue('为什么从右向左搬？')
    await wrapper.get('[data-testid="agent-submit"]').trigger('click')
    await flushPromises()

    expect(mockStart).toHaveBeenCalledWith(10, 'session', '为什么从右向左搬？')
    expect(wrapper.text()).toContain('这样不会覆盖尚未读取的元素。')

    await wrapper.get('[data-testid="agent-input"]').setValue('换个例子')
    await wrapper.get('[data-testid="agent-submit"]').trigger('click')
    await flushPromises()

    expect(mockResume).toHaveBeenCalledWith(10, 'session', 'run', '换个例子')
    expect(wrapper.text()).toContain('可以把它看成给书架腾位置。')
  })

  it('keeps the question available when the request fails', async () => {
    mockStart.mockRejectedValueOnce(new Error('当前 AI 模型不支持 Tutor Agent 工具调用'))
    const wrapper = mountAgent()

    await wrapper.get('[data-testid="agent-input"]').setValue('解释一下')
    await wrapper.get('[data-testid="agent-submit"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('当前 AI 模型不支持 Tutor Agent 工具调用')
    expect((wrapper.get('[data-testid="agent-input"]').element as HTMLTextAreaElement).value).toBe('解释一下')
  })

  it('restores the paused run from the same Tutor session', async () => {
    sessionStorage.setItem('lp:tutor-agent-run:10:session', 'run')
    mockGet.mockResolvedValueOnce({
      data: {
        runKey: 'run',
        status: 'WAITING_USER',
        messages: [{ sequence: 2, role: 'ASSISTANT', content: '上次回答', createTime: null }],
      },
    })

    const wrapper = mountAgent()
    await flushPromises()

    expect(mockGet).toHaveBeenCalledWith(10, 'session', 'run')
    expect(wrapper.text()).toContain('上次回答')
  })
})
