import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { setToken } from '@/utils/auth'

const { mockGetSession, mockSubmitAnswer, mockCompleteSession, mockSuccess, mockError, mockReplace } = vi.hoisted(
  () => ({
    mockGetSession: vi.fn(),
    mockSubmitAnswer: vi.fn(),
    mockCompleteSession: vi.fn(),
    mockSuccess: vi.fn(),
    mockError: vi.fn(),
    mockReplace: vi.fn(),
  }),
)

vi.mock('@/api/exam', () => ({
  getExamLearningSession: (...args: unknown[]) => mockGetSession(...args),
  submitExamLearningAnswer: (...args: unknown[]) => mockSubmitAnswer(...args),
  completeExamLearningSession: (...args: unknown[]) => mockCompleteSession(...args),
}))

vi.mock('vue-router', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue-router')>()),
  useRoute: () => ({ params: { sessionId: '30' } }),
  useRouter: () => ({ push: vi.fn(), replace: mockReplace }),
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: mockSuccess, error: mockError },
}))

import ExamLearningView from '@/views/exam/ExamLearningView.vue'
import { useGamificationStore } from '@/stores/gamification'
enableAutoUnmount(afterEach)

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
    localStorage.clear()
    setActivePinia(createPinia())
    setToken('exam-learning-session-a')
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

    expect(mockGetSession).toHaveBeenCalledWith(30, { errorDisplay: 'inline' })
    expect(wrapper.text()).toContain('2025 · 全国硕士研究生招生考试 · 来源：公开文件')
    expect(wrapper.text()).toContain('1(1)')
    const assistant = wrapper.findComponent({ name: 'AiQuestionAssistant' })
    expect(assistant.props('learningSessionId')).toBe(30)
    expect(assistant.props('disabled')).toBe(true)

    await wrapper.find('.option-item input').setValue('A')
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
      { errorDisplay: 'inline' },
    )
    expect(wrapper.text()).toContain('本题反馈')
    expect(wrapper.text()).toContain('回答正确')
    expect((wrapper.find('.option-item input[type="radio"]').element as HTMLInputElement).checked).toBe(true)
    expect(wrapper.find('.sheet-item').attributes('aria-current')).toBe('step')
    expect(wrapper.findComponent({ name: 'AiQuestionAssistant' }).props('disabled')).toBe(false)

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('完成本轮学习'))!
      .trigger('click')
    await flushPromises()

    expect(mockCompleteSession).toHaveBeenCalledWith(30, { errorDisplay: 'inline' })
    expect(wrapper.text()).toContain('本轮学习已完成')
    expect(mockSuccess).not.toHaveBeenCalled()
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

    expect(wrapper.text()).toContain('答案已保存，供你对照参考答案自评')
    expect(wrapper.text()).toContain('分步参考答案')
    expect(wrapper.text()).not.toContain('回答错误')
    expect(wrapper.text()).not.toContain('正确答案：')
  })

  it('does not accept a reward from a submission that resolves after unmount', async () => {
    let resolve!: (value: { code: number; data: Record<string, unknown> }) => void
    mockSubmitAnswer.mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    const acceptReward = vi.spyOn(useGamificationStore(), 'acceptReward')
    const wrapper = mount(ExamLearningView, {
      global: { stubs, directives: { loading: () => undefined } },
    })
    await flushPromises()
    await wrapper.find('.option-item input').setValue('A')
    const pending = wrapper
      .findAll('button')
      .find((button) => button.text().includes('提交答案'))!
      .trigger('click')
    wrapper.unmount()
    resolve({
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
        reward: {
          eventId: 81,
          awardedXp: 10,
          reason: 'CORRECT_ANSWER',
          eligible: true,
          levelBefore: 1,
          levelAfter: 1,
          leveledUp: false,
          streakDays: 1,
          newAchievements: [],
          summary: { version: 1 },
        },
      },
    })
    await pending
    expect(acceptReward).not.toHaveBeenCalled()
  })

  it('提交在途时锁定作答，快速重复触发只发送一次请求', async () => {
    let resolve!: (value: { code: number; data: Record<string, unknown> }) => void
    mockSubmitAnswer.mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    const wrapper = mount(ExamLearningView, {
      global: { stubs, directives: { loading: () => undefined } },
    })
    await flushPromises()
    await wrapper.find('.option-item input').setValue('A')
    const submitButton = wrapper.findAll('button').find((button) => button.text().includes('提交答案'))!
    const pending = submitButton.trigger('click')
    await submitButton.trigger('click')
    expect(mockSubmitAnswer).toHaveBeenCalledTimes(1)
    expect(wrapper.find('fieldset').attributes('disabled')).toBeDefined()
    resolve({
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
    await pending
  })

  it('在认证会话变化后清空旧试卷并返回试卷列表', async () => {
    const wrapper = mount(ExamLearningView, {
      global: { stubs, directives: { loading: () => undefined } },
    })
    await flushPromises()
    setToken('exam-learning-session-b')
    await flushPromises()
    expect(wrapper.text()).toContain('试卷学习会话不存在')
    expect(mockReplace).toHaveBeenCalledWith('/exams')
  })

  it('读取失败时展示原位错误而不伪造空会话', async () => {
    mockGetSession.mockRejectedValueOnce(new Error('network'))
    const wrapper = mount(ExamLearningView, {
      global: { stubs, directives: { loading: () => undefined } },
    })
    await flushPromises()
    expect(wrapper.text()).toContain('试卷学习会话暂时无法读取')
    expect(wrapper.text()).not.toContain('试卷学习会话不存在')
  })

  it('在题内显示提交失败并允许重试，不显示全局提示', async () => {
    mockSubmitAnswer.mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce({
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
    const wrapper = mount(ExamLearningView, { global: { stubs, directives: { loading: () => undefined } } })
    await flushPromises()
    await wrapper.find('.option-item input').setValue('A')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('提交答案'))!
      .trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('提交答案失败')
    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()
    expect(mockSubmitAnswer).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('回答正确')
    expect(mockError).not.toHaveBeenCalled()
  })

  it('重做已保存的题目时重新出现提交入口，并替换上一轮辅导与反馈', async () => {
    const wrapper = mount(ExamLearningView, { global: { stubs } })
    await flushPromises()
    await wrapper.find('.option-item input').setValue('A')
    const button = (name: string) => wrapper.findAll('button').find((item) => item.text() === name)!
    await button('提交答案').trigger('click')
    await flushPromises()
    const oldAssistant = wrapper.findComponent({ name: 'AiQuestionAssistant' }).vm
    await button('重新作答').trigger('click')
    expect(wrapper.find('fieldset').attributes('disabled')).toBeUndefined()
    expect(wrapper.find('.exam-learning-feedback').exists()).toBe(false)
    expect(wrapper.findComponent({ name: 'AiQuestionAssistant' }).props('disabled')).toBe(true)
    expect(wrapper.findComponent({ name: 'AiQuestionAssistant' }).vm).not.toBe(oldAssistant)
    expect(button('完成本轮学习').attributes('disabled')).toBeDefined()
    await button('提交答案').trigger('click')
    await flushPromises()
    expect(mockSubmitAnswer).toHaveBeenCalledTimes(2)
    expect(wrapper.find('fieldset').attributes('disabled')).toBeDefined()
    expect(wrapper.find('.exam-learning-feedback').text()).toContain('回答正确')
    expect(wrapper.findComponent({ name: 'AiQuestionAssistant' }).props('disabled')).toBe(false)
  })
})
