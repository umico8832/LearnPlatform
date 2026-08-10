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
})
