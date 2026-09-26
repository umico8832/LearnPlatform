import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { mockGet, mockStart, mockResume, mockLatest } = vi.hoisted(() => ({
  mockGet: vi.fn(),
  mockLatest: vi.fn(),
  mockStart: vi.fn(),
  mockResume: vi.fn(),
}))

vi.mock('@/api/tutor', () => ({
  getLatestTutorAgentRun: (...args: unknown[]) => mockLatest(...args),
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
    mockLatest.mockResolvedValue({ data: null })
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

  it('offers an assistant-requested understanding check without supplying an answer', async () => {
    mockLatest.mockResolvedValueOnce({
      data: {
        runKey: 'run',
        status: 'WAITING_USER',
        messages: [
          { sequence: 1, role: 'USER', content: '我准备好了', createTime: null, actions: [{ type: 'CHECK' }] },
          {
            sequence: 2,
            role: 'ASSISTANT',
            content: '我们先检查一下理解。',
            createTime: null,
            actions: [{ type: 'CHECK' }],
          },
        ],
      },
    })
    const wrapper = mountAgent()
    await flushPromises()

    expect(wrapper.findAll('[data-testid="agent-request-check"]')).toHaveLength(1)
    await wrapper.get('[data-testid="agent-request-check"]').trigger('click')
    expect(wrapper.emitted('request-check')).toEqual([[]])
  })

  it('only asks Tutor to read the saved check result after the learner explicitly requests follow-up', async () => {
    mockLatest.mockResolvedValueOnce({ data: { runKey: 'run', status: 'WAITING_USER', messages: [] } })
    const wrapper = mount(TutorAgentConversation, {
      props: {
        courseId: 10,
        sessionKey: 'session',
        checkResult: {
          correct: false,
          explanation: '这项由服务端判分。',
          guidanceType: null,
          guidanceTitle: null,
          guidanceDescription: null,
          guidanceKnowledgePointId: null,
        },
      },
      global: { stubs },
    })
    await flushPromises()

    await wrapper.get('[data-testid="agent-follow-up-check"]').trigger('click')
    await flushPromises()

    expect(mockResume).toHaveBeenCalledWith(10, 'session', 'run', '请根据我本节理解检查的实际作答，继续指导我。')
    expect(mockResume).not.toHaveBeenCalledWith(10, 'session', 'run', expect.stringContaining('这项由服务端判分'))
  })

  it('starts a first Agent run when the learner explicitly requests follow-up after checking', async () => {
    mockLatest.mockResolvedValueOnce({ data: null })
    const wrapper = mount(TutorAgentConversation, {
      props: { courseId: 10, sessionKey: 'session', checkResult: { correct: true } },
      global: { stubs },
    })
    await flushPromises()
    await wrapper.get('[data-testid="agent-follow-up-check"]').trigger('click')
    await flushPromises()
    expect(mockStart).toHaveBeenCalledWith(10, 'session', '请根据我本节理解检查的实际作答，继续指导我。')
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

  it('blocks sending until the saved conversation is restored', async () => {
    sessionStorage.setItem('lp:tutor-agent-run:10:session', 'run')
    let resolve!: (value: unknown) => void
    mockGet.mockReturnValueOnce(
      new Promise((done) => {
        resolve = done
      }),
    )
    const wrapper = mountAgent()
    await wrapper.get('[data-testid="agent-input"]').setValue('继续')
    expect(wrapper.get('[data-testid="agent-submit"]').attributes('disabled')).toBeDefined()
    await wrapper.get('[data-testid="agent-input"]').trigger('keydown', { key: 'Enter', ctrlKey: true })
    expect(mockStart).not.toHaveBeenCalled()
    resolve({ data: { runKey: 'run', status: 'WAITING_USER', messages: [] } })
    await flushPromises()
    expect(wrapper.get('[data-testid="agent-submit"]').attributes('disabled')).toBeUndefined()
  })

  it('keeps the saved run after a network failure and allows restoring it again', async () => {
    sessionStorage.setItem('lp:tutor-agent-run:10:session', 'run')
    mockGet.mockRejectedValueOnce(new Error('网络暂不可用'))
    const wrapper = mountAgent()
    await flushPromises()
    expect(sessionStorage.getItem('lp:tutor-agent-run:10:session')).toBe('run')
    await wrapper.get('[data-testid="agent-input"]').setValue('继续')
    expect(wrapper.get('[data-testid="agent-submit"]').attributes('disabled')).toBeDefined()
    mockGet.mockResolvedValueOnce({ data: { runKey: 'run', status: 'WAITING_USER', messages: [] } })
    await wrapper.get('[data-testid="agent-refresh"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="agent-submit"]').attributes('disabled')).toBeUndefined()
  })

  it('shows running state and refreshes it before another question can be sent', async () => {
    sessionStorage.setItem('lp:tutor-agent-run:10:session', 'run')
    mockGet.mockResolvedValueOnce({ data: { runKey: 'run', status: 'RUNNING', messages: [] } })
    const wrapper = mountAgent()
    await flushPromises()
    await wrapper.get('[data-testid="agent-input"]').setValue('继续')
    expect(wrapper.get('[data-testid="agent-submit"]').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('正在处理')
    mockGet.mockResolvedValueOnce({ data: { runKey: 'run', status: 'FAILED', messages: [] } })
    await wrapper.get('[data-testid="agent-refresh"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('可重试')
    await wrapper.get('[data-testid="agent-submit"]').trigger('click')
    await flushPromises()
    expect(mockResume).toHaveBeenCalledWith(10, 'session', 'run', '继续')
  })

  it('ignores restored messages after switching to another Tutor session', async () => {
    sessionStorage.setItem('lp:tutor-agent-run:10:session', 'old-run')
    let resolve!: (value: unknown) => void
    mockGet.mockReturnValueOnce(
      new Promise((done) => {
        resolve = done
      }),
    )
    const wrapper = mountAgent()
    await wrapper.setProps({ sessionKey: 'next-session' })
    resolve({
      data: {
        runKey: 'old-run',
        status: 'WAITING_USER',
        messages: [{ sequence: 2, role: 'ASSISTANT', content: '旧会话的回答', createTime: null }],
      },
    })
    await flushPromises()
    expect(wrapper.text()).not.toContain('旧会话的回答')
    await wrapper.get('[data-testid="agent-input"]').setValue('新的问题')
    await wrapper.get('[data-testid="agent-submit"]').trigger('click')
    await flushPromises()
    expect(mockStart).toHaveBeenCalledWith(10, 'next-session', '新的问题')
  })

  it('ignores a late send response after the session changes', async () => {
    let resolve!: (value: unknown) => void
    mockStart.mockReturnValueOnce(
      new Promise((done) => {
        resolve = done
      }),
    )
    const wrapper = mountAgent()
    await wrapper.get('[data-testid="agent-input"]').setValue('旧问题')
    await wrapper.get('[data-testid="agent-submit"]').trigger('click')
    await wrapper.setProps({ sessionKey: 'next-session' })
    await wrapper.get('[data-testid="agent-input"]').setValue('新问题草稿')
    resolve({
      data: {
        runKey: 'old-run',
        status: 'WAITING_USER',
        messages: [{ sequence: 2, role: 'ASSISTANT', content: '迟到的旧回答', createTime: null }],
      },
    })
    await flushPromises()
    expect(wrapper.text()).not.toContain('迟到的旧回答')
    expect((wrapper.get('[data-testid="agent-input"]').element as HTMLTextAreaElement).value).toBe('新问题草稿')
    expect(sessionStorage.getItem('lp:tutor-agent-run:10:next-session')).toBeNull()
  })

  it('discovers the latest server run when the first response identifier was lost', async () => {
    mockLatest.mockResolvedValueOnce({
      data: {
        runKey: 'recovered',
        status: 'WAITING_USER',
        messages: [{ sequence: 2, role: 'ASSISTANT', content: '服务端已保存的回答', createTime: null }],
      },
    })
    const wrapper = mountAgent()
    await flushPromises()
    expect(mockLatest).toHaveBeenCalledWith(10, 'session')
    expect(wrapper.text()).toContain('服务端已保存的回答')
    expect(sessionStorage.getItem('lp:tutor-agent-run:10:session')).toBe('recovered')
    await wrapper.get('[data-testid="agent-input"]').setValue('继续')
    await wrapper.get('[data-testid="agent-submit"]').trigger('click')
    await flushPromises()
    expect(mockResume).toHaveBeenCalledWith(10, 'session', 'recovered', '继续')
    expect(mockStart).not.toHaveBeenCalled()
  })

  it('recovers a created run after the first send loses its response', async () => {
    const wrapper = mountAgent()
    await flushPromises()
    mockStart.mockRejectedValueOnce(new Error('连接中断'))
    mockLatest.mockResolvedValueOnce({ data: { runKey: 'created', status: 'FAILED', messages: [] } })
    await wrapper.get('[data-testid="agent-input"]').setValue('原问题')
    await wrapper.get('[data-testid="agent-submit"]').trigger('click')
    await flushPromises()
    expect(sessionStorage.getItem('lp:tutor-agent-run:10:session')).toBe('created')
    await wrapper.get('[data-testid="agent-submit"]').trigger('click')
    await flushPromises()
    expect(mockStart).toHaveBeenCalledTimes(1)
    expect(mockResume).toHaveBeenCalledWith(10, 'session', 'created', '原问题')
  })

  it('does not create a new run while server discovery is unavailable', async () => {
    mockLatest.mockRejectedValueOnce(new Error('网络不可用'))
    const wrapper = mountAgent()
    await flushPromises()
    await wrapper.get('[data-testid="agent-input"]').setValue('新问题')
    expect(wrapper.get('[data-testid="agent-submit"]').attributes('disabled')).toBeDefined()
    mockLatest.mockResolvedValueOnce({ data: null })
    await wrapper.get('[data-testid="agent-refresh"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="agent-submit"]').attributes('disabled')).toBeUndefined()
  })
})
