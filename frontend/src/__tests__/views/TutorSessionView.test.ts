import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const { mockStartTutorSession, mockSubmitTutorCheck, mockPush } = vi.hoisted(() => ({
  mockStartTutorSession: vi.fn(),
  mockSubmitTutorCheck: vi.fn(),
  mockPush: vi.fn(),
}))

vi.mock('@/api/course', () => ({
  startTutorSession: (...args: unknown[]) => mockStartTutorSession(...args),
  submitTutorCheck: (...args: unknown[]) => mockSubmitTutorCheck(...args),
  isArrayStackInsertionCourseware: () => false,
  isArrayStackResizeCourseware: () => false,
  isArrayQueueRepresentationCourseware: () => false,
  isArrayQueueEnqueueCourseware: () => false,
  isArrayQueueDequeueCourseware: () => false,
  isArrayQueueResizeCourseware: () => false,
  isArrayDequeRepresentationCourseware: () => false,
  isArrayDequeFrontShiftInsertCourseware: () => false,
  isDualArrayDequeRepresentationCourseware: () => false,
  isDualArrayDequeBalanceCourseware: () => false,
  isRootishArrayStackLayoutCourseware: () => false,
  isSequentialListStorageCourseware: () => false,
  isLinkedListReversalCourseware: () => false,
  isFactorialCallStackCourseware: () => false,
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '408' }, query: { knowledgePointId: '37' } }),
  useRouter: () => ({ back: vi.fn(), push: mockPush }),
}))

import TutorSessionView from '@/views/course/TutorSessionView.vue'

const stubs = {
  'el-button': {
    template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
    props: ['disabled', 'loading', 'type', 'icon', 'text'],
    emits: ['click'],
  },
  'el-result': { template: '<section><slot /><slot name="extra" /></section>' },
  'el-alert': { template: '<div>{{ title }} {{ description }}</div>', props: ['title', 'description', 'type'] },
  'el-radio-group': {
    template:
      '<div><button data-testid="choose-option" @click="$emit(\'update:modelValue\', \'RIGHT\')">选择</button><slot /></div>',
    props: ['modelValue', 'disabled'],
    emits: ['update:modelValue'],
  },
  'el-radio': { template: '<span><slot /></span>', props: ['value', 'border'] },
  TutorArrayStackInsertion: true,
  TutorArrayStackResize: true,
  TutorArrayQueueRepresentation: true,
  TutorArrayQueueEnqueue: true,
  TutorArrayQueueDequeue: true,
  TutorArrayQueueResize: true,
  TutorArrayDequeRepresentation: true,
  TutorArrayDequeFrontShiftInsert: true,
  TutorDualArrayDequeRepresentation: true,
  TutorDualArrayDequeBalance: true,
  TutorRootishArrayStackLayout: true,
  TutorSequentialListStorage: true,
  TutorLinkedListReversal: true,
  TutorFactorialCallStack: true,
}

function findButton(wrapper: ReturnType<typeof mount>, text: string) {
  const button = wrapper.findAll('button').find((item) => item.text().includes(text))
  expect(button, `button with text ${text}`).toBeTruthy()
  return button!
}

describe('TutorSessionView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockStartTutorSession.mockResolvedValue({
      data: {
        sessionKey: 'session-key',
        title: 'ArrayQueue 的循环数组表示',
        lesson: { summary: 'summary', steps: ['step'] },
        check: { id: 'check', prompt: 'prompt', options: [{ id: 'RIGHT', text: '正确选项' }] },
        learningContext: {
          paperAnswerCount: 3,
          paperIncorrectCount: 2,
          paperAiAssistanceCount: 1,
          unresolvedWrongCount: 2,
          dueReviewCount: 1,
          reviewAnswerCount: 4,
          latestEvidenceAt: '2026-08-15T09:30:00',
        },
      },
    })
    mockSubmitTutorCheck.mockResolvedValue({
      data: {
        correct: true,
        explanation: '回答正确。',
        guidanceType: 'NEXT_TARGET',
        guidanceTitle: 'ArrayQueue 的入队',
        guidanceDescription: '继续学习循环队尾写入。',
        guidanceKnowledgePointId: 38,
      },
    })
  })

  it('理解检查后允许直接进入已审查的下一教学目标', async () => {
    const wrapper = mount(TutorSessionView, { global: { stubs } })
    await flushPromises()

    await wrapper.get('[data-testid="choose-option"]').trigger('click')
    await findButton(wrapper, '提交检查').trigger('click')
    await flushPromises()
    await findButton(wrapper, '学习下一内容').trigger('click')

    expect(mockPush).toHaveBeenCalledWith({
      name: 'TutorSession',
      params: { id: 408 },
      query: { knowledgePointId: '38' },
    })
  })

  it('展示Tutor会话启动时消费的最近相关记录', async () => {
    const wrapper = mount(TutorSessionView, { global: { stubs } })
    await flushPromises()

    expect(wrapper.text()).toContain('最近相关记录')
    expect(wrapper.text()).toContain('3次真题学习作答')
    expect(wrapper.text()).toContain('其中 2 次答错')
    expect(wrapper.text()).toContain('1次试卷 AI 辅导')
    expect(wrapper.text()).toContain('2道未掌握错题')
    expect(wrapper.text()).toContain('1道到期复习')
    expect(wrapper.text()).toContain('4次复习作答')
    expect(wrapper.text()).toContain('2026-08-15 09:30')
  })

  it('没有相关证据时不制造学习进度卡片', async () => {
    mockStartTutorSession.mockResolvedValueOnce({
      data: {
        sessionKey: 'empty-context-session',
        title: 'ArrayQueue 的循环数组表示',
        lesson: { summary: 'summary', steps: ['step'] },
        check: { id: 'check', prompt: 'prompt', options: [] },
        learningContext: {
          paperAnswerCount: 0,
          paperIncorrectCount: 0,
          paperAiAssistanceCount: 0,
          unresolvedWrongCount: 0,
          dueReviewCount: 0,
          reviewAnswerCount: 0,
          latestEvidenceAt: null,
        },
      },
    })

    const wrapper = mount(TutorSessionView, { global: { stubs } })
    await flushPromises()

    expect(wrapper.text()).not.toContain('最近相关记录')
  })
})
