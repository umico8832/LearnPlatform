import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const { mockSubmitExam, mockReplace, mockConfirm, mockWarning } = vi.hoisted(() => ({
  mockSubmitExam: vi.fn(),
  mockReplace: vi.fn(),
  mockConfirm: vi.fn(),
  mockWarning: vi.fn(),
}))

vi.mock('@/api/exam', () => ({
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
  ElMessage: { error: vi.fn(), warning: mockWarning },
  ElMessageBox: { confirm: mockConfirm },
}))

import ExamTakeView from '@/views/exam/ExamTakeView.vue'

const stubs = {
  'el-button': {
    template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
    props: ['disabled', 'type', 'size'],
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
    sessionStorage.setItem('exam_session_101', JSON.stringify({ questions, duration: 20 }))
    mockConfirm.mockResolvedValue(undefined)
    mockSubmitExam.mockResolvedValue({
      code: 0,
      data: { id: 101, score: 15, totalScore: 15, answers: [] },
    })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('submits answers for single choice, multiple choice, and true/false then opens the result page', async () => {
    const wrapper = mount(ExamTakeView, { global: { stubs } })
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
    expect(sessionStorage.getItem('exam_session_101')).toBeNull()
    expect(sessionStorage.getItem('exam_result_101')).toContain('"score":15')
    expect(mockReplace).toHaveBeenCalledWith({ name: 'ExamResult', params: { recordId: '101' } })

    wrapper.unmount()
  })

  it('redirects to the exam list when the exam session is missing', async () => {
    sessionStorage.clear()
    const wrapper = mount(ExamTakeView, { global: { stubs } })
    await flushPromises()

    expect(mockWarning).toHaveBeenCalledWith('请从考试列表开始考试')
    expect(mockReplace).toHaveBeenCalledWith('/exams')

    wrapper.unmount()
  })
})
