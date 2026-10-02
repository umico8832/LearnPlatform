import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'

const { mockGetExamSession, mockGetPaperDetail, mockSubmitExam, mockReplace, mockConfirm, mockWarning, mockError } =
  vi.hoisted(() => ({
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
import { removeToken } from '@/utils/auth'

enableAutoUnmount(afterEach)

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}
const mountExam = () => mount(ExamTakeView, { global: { stubs, directives: { loading: () => undefined } } })
const button = (wrapper: ReturnType<typeof mountExam>, text: string) =>
  wrapper.findAll('button').find((item) => item.text() === text)!

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

    expect(mockGetExamSession).toHaveBeenCalledWith(101, { errorDisplay: 'inline' })
    expect(mockGetPaperDetail).toHaveBeenCalledWith(7, { errorDisplay: 'inline' })
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
    mockGetPaperDetail.mockImplementation(
      () =>
        new Promise((resolve) => {
          setTimeout(() => resolve({ code: 0, data: { id: 7, questions } }), 5_000)
        }),
    )

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
    mockGetExamSession.mockImplementation(
      () =>
        new Promise((resolve) => {
          setTimeout(
            () =>
              resolve({
                code: 0,
                data: {
                  id: 101,
                  examPaperId: 7,
                  status: 0,
                  deadline: '2026-08-13T10:17:00',
                  serverTime: '2026-08-13T10:00:00',
                },
              }),
            5_000,
          )
        }),
    )

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

    await wrapper
      .findAll('.option-item')
      .find((item) => item.text().includes('extends'))!
      .find('input')
      .setValue(true)
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('下一题'))!
      .trigger('click')

    await wrapper
      .findAll('.option-item')
      .find((item) => item.text().includes('int'))!
      .find('input')
      .setValue(true)
    await wrapper
      .findAll('.option-item')
      .find((item) => item.text().includes('boolean'))!
      .find('input')
      .setValue(true)
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('下一题'))!
      .trigger('click')

    await wrapper
      .findAll('.option-item')
      .find((item) => item.text().includes('正确'))!
      .find('input')
      .setValue(true)
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('提交试卷'))!
      .trigger('click')
    await flushPromises()

    expect(mockConfirm).toHaveBeenCalledWith(
      '已完成全部作答。确定提交试卷？提交后不可修改。',
      '提交确认',
      expect.objectContaining({ confirmButtonText: '提交试卷', cancelButtonText: '继续作答' }),
    )
    expect(mockSubmitExam).toHaveBeenCalledWith(
      {
        examRecordId: 101,
        answers: [
          { questionId: 1, userAnswer: 'A' },
          { questionId: 2, userAnswer: 'A,C' },
          { questionId: 3, userAnswer: 'TRUE' },
        ],
      },
      { errorDisplay: 'inline' },
    )
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

  it('keeps a recoverable paper error inline without a false countdown', async () => {
    mockGetPaperDetail.mockRejectedValue(new Error('network'))

    const wrapper = mount(ExamTakeView, {
      global: { stubs, directives: { loading: () => undefined } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('暂时无法恢复考试')
    expect(wrapper.find('.countdown').exists()).toBe(false)
    expect(mockReplace).not.toHaveBeenCalled()
    expect(mockError).not.toHaveBeenCalled()
    mockGetPaperDetail.mockResolvedValue({ code: 0, data: { id: 7, questions } })
    await button(wrapper, '重试恢复').trigger('click')
    await flushPromises()
    expect(wrapper.find('.countdown').exists()).toBe(true)

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
  it('locks answering during one confirmation and keeps choices when it is cancelled', async () => {
    const confirmation = deferred<void>()
    mockConfirm.mockReturnValueOnce(confirmation.promise)
    const wrapper = mountExam()
    await flushPromises()
    await wrapper.find('input[value="A"]').setValue(true)
    await button(wrapper, '提交试卷').trigger('click')
    await button(wrapper, '提交试卷').trigger('click')
    expect(mockConfirm).toHaveBeenCalledTimes(1)
    expect(mockConfirm.mock.calls[0][0]).toContain('还有 2 题未作答')
    expect(wrapper.find('fieldset').attributes('disabled')).toBeDefined()
    confirmation.reject('cancel')
    await flushPromises()
    expect(mockSubmitExam).not.toHaveBeenCalled()
    expect((wrapper.find('input[value="A"]').element as HTMLInputElement).checked).toBe(true)
    expect(wrapper.find('fieldset').attributes('disabled')).toBeUndefined()
  })

  it('preserves choices after failed submission and sends one locked retry', async () => {
    mockSubmitExam.mockRejectedValueOnce(new Error('Network Error'))
    const wrapper = mountExam()
    await flushPromises()
    await wrapper.find('input[value="A"]').setValue(true)
    await button(wrapper, '提交试卷').trigger('click')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').text()).toContain('答案仍保留在本页')
    expect((wrapper.find('input[value="A"]').element as HTMLInputElement).checked).toBe(true)
    const retry = deferred<unknown>()
    mockSubmitExam.mockReturnValueOnce(retry.promise)
    await button(wrapper, '重试交卷').trigger('click')
    await button(wrapper, '提交试卷').trigger('click')
    expect(mockSubmitExam).toHaveBeenCalledTimes(2)
    expect(wrapper.find('fieldset').attributes('disabled')).toBeDefined()
    retry.resolve({ code: 0, data: { id: 101 } })
    await flushPromises()
    expect(mockReplace).toHaveBeenCalledWith({ name: 'ExamResult', params: { recordId: '101' } })
  })

  it('does not submit a confirmation resolved after the authoritative deadline', async () => {
    vi.useFakeTimers()
    const confirmation = deferred<void>()
    mockConfirm.mockReturnValueOnce(confirmation.promise)
    mockGetExamSession.mockResolvedValue({
      code: 0,
      data: { id: 101, examPaperId: 7, status: 0, deadline: '2026-08-13T10:00:01', serverTime: '2026-08-13T10:00:00' },
    })
    const wrapper = mountExam()
    await flushPromises()
    await button(wrapper, '提交试卷').trigger('click')
    await vi.advanceTimersByTimeAsync(1_000)
    confirmation.resolve()
    await flushPromises()
    expect(mockSubmitExam).not.toHaveBeenCalled()
    expect(mockWarning).toHaveBeenCalledTimes(1)
    expect(mockReplace).toHaveBeenCalledWith({ name: 'ExamList', query: { tab: 'records' } })
  })

  it('ignores a restored session that arrives after unmount', async () => {
    const pending = deferred<unknown>()
    mockGetExamSession.mockReturnValueOnce(pending.promise)
    const wrapper = mountExam()
    wrapper.unmount()
    pending.resolve({ code: 0, data: { id: 101, examPaperId: 7, status: 1 } })
    await flushPromises()
    expect(mockReplace).not.toHaveBeenCalled()
    expect(mockGetPaperDetail).not.toHaveBeenCalled()
  })
  it('clears answers on account change and ignores a late submission', async () => {
    const pending = deferred<unknown>()
    mockSubmitExam.mockReturnValueOnce(pending.promise)
    const wrapper = mountExam()
    await flushPromises()
    await wrapper.find('input[value="A"]').setValue(true)
    await button(wrapper, '提交试卷').trigger('click')
    await flushPromises()
    removeToken()
    await flushPromises()
    expect(wrapper.find('.question-card').exists()).toBe(false)
    pending.resolve({ code: 0, data: { id: 101 } })
    await flushPromises()
    expect(mockReplace).toHaveBeenCalledTimes(1)
    expect(mockReplace).toHaveBeenCalledWith({ name: 'ExamList', query: { tab: 'records' } })
    expect(mockWarning).not.toHaveBeenCalled()
  })

  it('ignores a paper response that arrives after unmount', async () => {
    const pending = deferred<unknown>()
    mockGetPaperDetail.mockReturnValueOnce(pending.promise)
    const wrapper = mountExam()
    await flushPromises()
    wrapper.unmount()
    pending.resolve({ code: 0, data: { id: 7, questions } })
    await flushPromises()
    expect(mockReplace).not.toHaveBeenCalled()
    expect(mockWarning).not.toHaveBeenCalled()
  })
})
