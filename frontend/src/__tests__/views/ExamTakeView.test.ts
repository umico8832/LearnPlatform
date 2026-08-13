import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const {
  mockGetExamSession,
  mockGetPaperDetail,
  mockSubmitExam,
  mockReplace,
  mockConfirm,
  mockWarning,
  mockError,
} = vi.hoisted(() => ({
  mockGetExamSession: vi.fn(),
  mockGetPaperDetail: vi.fn(),
  mockSubmitExam: vi.fn(),
  mockReplace: vi.fn(),
  mockConfirm: vi.fn(),
  mockWarning: vi.fn(),
  mockError: vi.fn(),
}))

vi.mock('@/api/exam', () => ({
  getExamSession: (...args: unknown[]) => mockGetExamSession(...args),
  getPaperDetail: (...args: unknown[]) => mockGetPaperDetail(...args),
  submitExam: (...args: unknown[]) => mockSubmitExam(...args),
}))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: () => ({ params: { recordId: '101' } }),
    useRouter: () => ({ replace: mockReplace }),
  }
})

vi.mock('element-plus', () => ({
  ElMessage: { error: mockError, warning: mockWarning },
  ElMessageBox: { confirm: mockConfirm },
}))

import ExamTakeView from '@/views/exam/ExamTakeView.vue'

const stubs = {
  'el-button': {
    template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
    props: ['disabled', 'loading', 'type', 'size'],
    emits: ['click'],
  },
  'el-card': { template: '<div><slot /></div>' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-icon': { template: '<i><slot /></i>' },
  'el-checkbox': { template: '<input type="checkbox" />', props: ['modelValue'] },
  'el-input': {
    template: '<textarea :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    props: ['modelValue'],
    emits: ['update:modelValue'],
  },
}

const questions = [
  {
    questionId: 1,
    sortOrder: 1,
    score: 5,
    content: '继承关键字是？',
    questionType: 'SINGLE_CHOICE',
    sectionTitle: '第一部分 数据结构',
    displayNumber: '1(1)',
    options: [
      { id: 11, optionLabel: 'A', content: 'extends', sortOrder: 1 },
      { id: 12, optionLabel: 'B', content: 'implements', sortOrder: 2 },
    ],
  },
  {
    questionId: 2,
    sortOrder: 2,
    score: 5,
    content: '哪些是基本数据类型？',
    questionType: 'MULTIPLE_CHOICE',
    options: [
      { id: 21, optionLabel: 'A', content: 'int', sortOrder: 1 },
      { id: 22, optionLabel: 'B', content: 'String', sortOrder: 2 },
      { id: 23, optionLabel: 'C', content: 'boolean', sortOrder: 3 },
    ],
  },
  {
    questionId: 3,
    sortOrder: 3,
    score: 5,
    content: 'finally 通常执行。',
    questionType: 'TRUE_FALSE',
    options: [],
  },
]

describe('ExamTakeView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    sessionStorage.setItem('exam_session_101', JSON.stringify({ questions: [], duration: 999 }))
    mockConfirm.mockResolvedValue(undefined)
    mockGetExamSession.mockResolvedValue({
      code: 0,
      data: {
        id: 101,
        examPaperId: 7,
        status: 0,
        duration: 60,
        deadline: '2026-08-13T10:17:00',
        serverTime: '2026-08-13T10:00:00',
      },
    })
    mockGetPaperDetail.mockResolvedValue({
      code: 0,
      data: { id: 7, duration: 60, questions },
    })
    mockSubmitExam.mockResolvedValue({
      code: 0,
      data: { id: 101, score: 15, totalScore: 15, answers: [] },
    })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('restores the server session, loads its safe paper, and uses the authoritative remaining time', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-13T02:00:00Z'))

    const wrapper = mount(ExamTakeView, {
      global: { stubs, directives: { loading: () => undefined } },
    })
    await flushPromises()

    expect(mockGetExamSession).toHaveBeenCalledWith(101)
    expect(mockGetPaperDetail).toHaveBeenCalledWith(7)
    expect(wrapper.find('.countdown').text()).toContain('17:00')
    expect(wrapper.find('.q-section').text()).toBe('第一部分 数据结构')
    expect(wrapper.find('.q-number').text()).toBe('1(1)')
    expect(wrapper.findAll('.sheet-item')[0].text()).toBe('1(1)')

    await vi.advanceTimersByTimeAsync(2_000)
    expect(wrapper.find('.countdown').text()).toContain('16:58')

    wrapper.unmount()
  })

  it('recomputes from the absolute deadline after a throttled timer tick', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-13T02:00:00Z'))

    const wrapper = mount(ExamTakeView, {
      global: { stubs, directives: { loading: () => undefined } },
    })
    await flushPromises()
    expect(wrapper.find('.countdown').text()).toContain('17:00')

    vi.setSystemTime(new Date('2026-08-13T02:05:00Z'))
    await vi.advanceTimersByTimeAsync(1_000)
    expect(wrapper.find('.countdown').text()).toContain('11:59')

    wrapper.unmount()
  })

  it('does not add paper loading time back to the server-authoritative deadline', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-13T02:00:00Z'))
    mockGetPaperDetail.mockImplementation(() => new Promise(resolve => {
      setTimeout(() => resolve({ code: 0, data: { id: 7, questions } }), 5_000)
    }))

    const wrapper = mount(ExamTakeView, {
      global: { stubs, directives: { loading: () => undefined } },
    })
    await flushPromises()
    await vi.advanceTimersByTimeAsync(5_000)
    await flushPromises()

    expect(wrapper.find('.countdown').text()).toContain('16:55')

    wrapper.unmount()
  })

  it('does not add a delayed session response back to the server-authoritative deadline', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-13T02:00:00Z'))
    mockGetExamSession.mockImplementation(() => new Promise(resolve => {
      setTimeout(() => resolve({
        code: 0,
        data: {
          id: 101,
          examPaperId: 7,
          status: 0,
          deadline: '2026-08-13T10:17:00',
          serverTime: '2026-08-13T10:00:00',
        },
      }), 5_000)
    }))

    const wrapper = mount(ExamTakeView, {
      global: { stubs, directives: { loading: () => undefined } },
    })
    await vi.advanceTimersByTimeAsync(5_000)
    await flushPromises()

    expect(wrapper.find('.countdown').text()).toContain('16:55')

    wrapper.unmount()
  })

  it('submits answers for single choice, multiple choice, and true/false then opens the authoritative result page', async () => {
    const wrapper = mount(ExamTakeView, {
      global: { stubs, directives: { loading: () => undefined } },
    })
    await flushPromises()

    await wrapper.findAll('.option-item').find(item => item.text().includes('extends'))!.trigger('click')
    await wrapper.findAll('button').find(button => button.text().includes('下一题'))!.trigger('click')

    await wrapper.findAll('.option-item').find(item => item.text().includes('int'))!.trigger('click')
    await wrapper.findAll('.option-item').find(item => item.text().includes('boolean'))!.trigger('click')
    await wrapper.findAll('button').find(button => button.text().includes('下一题'))!.trigger('click')

    await wrapper.findAll('.option-item').find(item => item.text().includes('正确'))!.trigger('click')
    await wrapper.findAll('button').find(button => button.text().includes('提交试卷'))!.trigger('click')
    await flushPromises()

    expect(mockConfirm).toHaveBeenCalledWith('确定提交试卷？提交后不可修改', '提交确认', { type: 'warning' })
    expect(mockSubmitExam).toHaveBeenCalledWith({
      examRecordId: 101,
      answers: [
        { questionId: 1, userAnswer: 'A' },
        { questionId: 2, userAnswer: 'A,C' },
        { questionId: 3, userAnswer: 'TRUE' },
      ],
    })
    expect(sessionStorage.getItem('exam_session_101')).toContain('"duration":999')
    expect(sessionStorage.getItem('exam_result_101')).toBeNull()
    expect(mockReplace).toHaveBeenCalledWith({ name: 'ExamResult', params: { recordId: '101' } })

    wrapper.unmount()
  })

  it('redirects a completed server session to its result without loading paper questions', async () => {
    mockGetExamSession.mockResolvedValue({ code: 0, data: { id: 101, examPaperId: 7, status: 1 } })

    const wrapper = mount(ExamTakeView, {
      global: { stubs, directives: { loading: () => undefined } },
    })
    await flushPromises()

    expect(mockReplace).toHaveBeenCalledWith({ name: 'ExamResult', params: { recordId: '101' } })
    expect(mockGetPaperDetail).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('explains an expired server session and returns to exam records', async () => {
    mockGetExamSession.mockResolvedValue({ code: 0, data: { id: 101, examPaperId: 7, status: 2 } })

    const wrapper = mount(ExamTakeView, {
      global: { stubs, directives: { loading: () => undefined } },
    })
    await flushPromises()

    expect(mockWarning).toHaveBeenCalledWith('考试已超时，已返回考试列表')
    expect(mockReplace).toHaveBeenCalledWith({ name: 'ExamList', query: { tab: 'records' } })
    expect(mockGetPaperDetail).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('returns to exam records when the safe paper cannot be loaded', async () => {
    mockGetPaperDetail.mockRejectedValue(new Error('network'))

    const wrapper = mount(ExamTakeView, {
      global: { stubs, directives: { loading: () => undefined } },
    })
    await flushPromises()

    expect(mockError).toHaveBeenCalledWith('恢复考试失败')
    expect(mockReplace).toHaveBeenCalledWith({ name: 'ExamList', query: { tab: 'records' } })

    wrapper.unmount()
  })

  it('returns to exam records at the deadline without claiming a late automatic submission', async () => {
    vi.useFakeTimers()
    mockGetExamSession.mockResolvedValue({
      code: 0,
      data: {
        id: 101,
        examPaperId: 7,
        status: 0,
        deadline: '2026-08-13T10:00:01',
        serverTime: '2026-08-13T10:00:00',
      },
    })
    const wrapper = mount(ExamTakeView, {
      global: { stubs, directives: { loading: () => undefined } },
    })
    await flushPromises()
    await vi.advanceTimersByTimeAsync(1_000)
    await flushPromises()

    expect(mockSubmitExam).not.toHaveBeenCalled()
    expect(mockWarning).toHaveBeenCalledWith('考试时间已结束，已返回考试列表')
    expect(mockReplace).toHaveBeenCalledWith({ name: 'ExamList', query: { tab: 'records' } })

    wrapper.unmount()
  })
})
