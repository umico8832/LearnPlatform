import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const { mockGetSession, mockSubmitAnswer, mockCompleteSession, mockSuccess } = vi.hoisted(() => ({
  mockGetSession: vi.fn(),
  mockSubmitAnswer: vi.fn(),
  mockCompleteSession: vi.fn(),
  mockSuccess: vi.fn(),
}))

vi.mock('@/api/exam', () => ({
  getExamLearningSession: (...args: unknown[]) => mockGetSession(...args),
  submitExamLearningAnswer: (...args: unknown[]) => mockSubmitAnswer(...args),
  completeExamLearningSession: (...args: unknown[]) => mockCompleteSession(...args),
}))

vi.mock('vue-router', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue-router')>()),
  useRoute: () => ({ params: { sessionId: '30' } }),
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: mockSuccess, error: vi.fn() },
}))

import ExamLearningView from '@/views/exam/ExamLearningView.vue'

const stubs = {
  'el-card': { template: '<div><slot /></div>' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-button': {
    template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
    props: ['disabled', 'loading', 'type'],
    emits: ['click'],
  },
  'el-input': { template: '<textarea />' },
  'el-empty': { template: '<div><slot /></div>' },
  AiQuestionAssistant: true,
  QuestionLearningAsset: true,
}

const session = () => ({
  id: 30,
  examPaperId: 2,
  paperTitle: '结构化试卷',
  courseId: 20,
  paperType: 'OFFICIAL_EXAM',
  examName: '全国硕士研究生招生考试',
  examYear: 2025,
  sourceReference: '公开文件',
  sourceVerified: true,
  status: 0,
  currentQuestionId: 10,
  answeredQuestionCount: 0,
  correctQuestionCount: 0,
  startTime: '2026-08-11T00:00:00',
  completeTime: null,
  questions: [
    {
      questionId: 10,
      sortOrder: 1,
      score: 5,
      content: '正确选项是？',
      questionType: 'SINGLE_CHOICE',
      sectionTitle: '第一部分',
      majorQuestionNumber: '1',
      minorQuestionNumber: '1',
      subquestionNumber: null,
      displayNumber: '1(1)',
      options: [{ id: 100, optionLabel: 'A', content: '正确', sortOrder: 1 }],
      latestAnswer: null,
    },
  ],
})

describe('ExamLearningView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetSession.mockResolvedValue({ code: 0, data: session() })
    mockSubmitAnswer.mockResolvedValue({
      code: 0,
      data: {
        answerId: 81,
        questionId: 10,
        attemptNo: 1,
        userAnswer: 'A',
        correct: true,
        score: 5,
        fullScore: 5,
        correctAnswer: 'A',
        analysis: '解析',
      },
    })
    mockCompleteSession.mockResolvedValue({
      code: 0,
      data: { ...session(), status: 1, answeredQuestionCount: 1, correctQuestionCount: 1 },
    })
  })

  it('按原题号逐题判分并在全部作答后完成本轮学习', async () => {
    const wrapper = mount(ExamLearningView, {
      global: { stubs, directives: { loading: () => undefined } },
    })
    await flushPromises()

    expect(mockGetSession).toHaveBeenCalledWith(30)
    expect(wrapper.text()).toContain('2025 · 全国硕士研究生招生考试 · 来源：公开文件')
    expect(wrapper.text()).toContain('1(1)')
    const assistant = wrapper.findComponent({ name: 'AiQuestionAssistant' })
    expect(assistant.props('learningSessionId')).toBe(30)
    expect(assistant.props('disabled')).toBe(true)

    await wrapper.find('.option-item').trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('提交答案'))!
      .trigger('click')
    await flushPromises()

    expect(mockSubmitAnswer).toHaveBeenCalledWith(
      30,
      expect.objectContaining({
        questionId: 10,
        userAnswer: 'A',
      }),
    )
    expect(wrapper.text()).toContain('回答正确')
    expect(assistant.props('disabled')).toBe(false)

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('完成本轮学习'))!
      .trigger('click')
    await flushPromises()

    expect(mockCompleteSession).toHaveBeenCalledWith(30)
    expect(mockSuccess).toHaveBeenCalledWith('本轮试卷学习已完成，可继续查看逐题复盘')
  })

  it('主观题学习只展示自评参考，不显示伪造的对错和分数', async () => {
    const subjectiveSession = session()
    subjectiveSession.questions[0] = {
      ...subjectiveSession.questions[0],
      questionType: 'SHORT_ANSWER',
      content: '算法综合应用题',
      options: [],
      latestAnswer: {
        answerId: 82,
        questionId: 10,
        attemptNo: 1,
        userAnswer: '我的算法',
        correct: null,
        score: null,
        fullScore: 13,
        correctAnswer: null,
        analysis: '分步参考答案',
        gradingStatus: 'SELF_REVIEW',
      },
    } as never
    subjectiveSession.answeredQuestionCount = 1
    mockGetSession.mockResolvedValue({ code: 0, data: subjectiveSession })

    const wrapper = mount(ExamLearningView, {
      global: { stubs, directives: { loading: () => undefined } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('已保存，按参考答案自评')
    expect(wrapper.text()).toContain('分步参考答案')
    expect(wrapper.text()).not.toContain('回答错误')
    expect(wrapper.text()).not.toContain('正确答案：')
  })
})
