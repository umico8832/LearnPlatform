import { createPinia, setActivePinia } from 'pinia'
import { useUserStore } from '@/stores/user'
import { setToken } from '@/utils/auth'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import type { LearningDiagnosis } from '@/api/statistics'
import LearningDiagnosisErrorPatterns from '@/components/statistics/LearningDiagnosisErrorPatterns.vue'
import LearningDiagnosisRecommendations from '@/components/statistics/LearningDiagnosisRecommendations.vue'
import LearningDiagnosisSummary from '@/components/statistics/LearningDiagnosisSummary.vue'
import QuestionErrorAnalysisDialog from '@/components/statistics/QuestionErrorAnalysisDialog.vue'
import SimilarQuestionDialog from '@/components/statistics/SimilarQuestionDialog.vue'
import LearningDiagnosisView from '@/views/statistics/LearningDiagnosisView.vue'

const {
  mockGetLearningDiagnosis,
  mockGetQuestionErrorAnalysis,
  mockGetSimilarQuestions,
  mockGetQuestionById,
  mockGetAiAdviceStream,
  mockPush,
} = vi.hoisted(() => ({
  mockGetLearningDiagnosis: vi.fn(),
  mockGetQuestionErrorAnalysis: vi.fn(),
  mockGetSimilarQuestions: vi.fn(),
  mockGetQuestionById: vi.fn(),
  mockGetAiAdviceStream: vi.fn(),
  mockPush: vi.fn(),
}))

vi.mock('@/api/statistics', () => ({
  getLearningDiagnosis: (...args: unknown[]) => mockGetLearningDiagnosis(...args),
  getQuestionErrorAnalysis: (...args: unknown[]) => mockGetQuestionErrorAnalysis(...args),
  getSimilarQuestions: (...args: unknown[]) => mockGetSimilarQuestions(...args),
  getAiAdviceStream: (...args: unknown[]) => mockGetAiAdviceStream(...args),
}))

vi.mock('@/api/question', () => ({
  getQuestionById: (...args: unknown[]) => mockGetQuestionById(...args),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
}))

const diagnosis: LearningDiagnosis = {
  totalPractice: 12,
  overallCorrectRate: 75,
  activeDaysLast30: 5,
  streakDays: 2,
  weakPoints: [],
  courseMasteries: [
    {
      courseId: 1,
      courseName: '数据结构',
      correctRate: 75,
      totalAttempts: 12,
      wrongCount: 3,
      knowledgePointCount: 4,
      weakPointCount: 1,
    },
  ],
  errorPatterns: {
    topErrorCourses: [],
    masteryDistribution: { 未掌握: 1 },
    repeatedErrorCount: 0,
    recentNewWrongCount: 1,
    questionTypeDistribution: {},
    difficultyDistribution: {},
    knowledgePointErrors: [],
    repeatedErrors: [],
    weeklyErrorTrend: [],
  },
  learningHabit: {
    avgDailyPractice: 1.2,
    preferredQuestionType: '单选题',
    preferredCourse: '数据结构',
    weeklyTrend: [],
    frequencyLevel: 'INACTIVE',
    frequencyDescription: '建议增加学习频率。',
  },
  dailyRecommendations: [
    {
      questionId: 10,
      reason: 'SPACED_REVIEW',
      reasonDescription: '到期复习',
      questionContent: '测试题',
      questionType: 'SINGLE_CHOICE',
      courseName: '数据结构',
      difficulty: 1,
      knowledgePointName: '线性表',
      lastWrongAnswer: null,
    },
  ],
  dailyAdvice: '保持练习',
}

describe('LearningDiagnosisView', () => {
  const mounted: Array<{ unmount: () => void }> = []

  afterEach(() => {
    mounted.splice(0).forEach((wrapper) => wrapper.unmount())
  })

  beforeEach(() => {
    setActivePinia(createPinia())
    useUserStore().setLoginInfo('test-token', {
      id: 7,
      username: 'learner',
      nickname: 'Learner',
      avatar: null,
      role: 'USER',
    })
    vi.clearAllMocks()
    sessionStorage.clear()
    mockGetLearningDiagnosis.mockResolvedValue({ data: diagnosis })
  })

  function mountView() {
    const wrapper = shallowMount(LearningDiagnosisView, {
      global: {
        mocks: { $router: { back: vi.fn() } },
        stubs: {
          LpStatePanel: {
            props: ['state', 'title'],
            template:
              '<section><p>{{ title }}</p><button @click="$emit(\'retry\')">重试读取</button><slot /></section>',
          },
          'el-page-header': { template: '<header><slot name="content" /></header>' },
        },
      },
    })
    mounted.push(wrapper)
    return wrapper
  }

  it('loads diagnosis and delegates each display area to a domain component', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(mockGetLearningDiagnosis).toHaveBeenCalledOnce()
    expect(wrapper.findComponent(LearningDiagnosisSummary).props('data')).toEqual(diagnosis)
    expect(wrapper.findComponent(LearningDiagnosisErrorPatterns).props('patterns')).toEqual(diagnosis.errorPatterns)
    expect(wrapper.findComponent(LearningDiagnosisRecommendations).props('recommendations')).toEqual(
      diagnosis.dailyRecommendations,
    )
  })

  it('shows a recoverable error state and retries loading the diagnosis', async () => {
    mockGetLearningDiagnosis
      .mockRejectedValueOnce(new Error('network unavailable'))
      .mockResolvedValueOnce({ data: diagnosis })
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('暂时无法读取学习诊断')
    const retryButton = wrapper.findAll('button').find((button) => button.text().includes('重试读取'))
    expect(retryButton).toBeDefined()

    await retryButton!.trigger('click')
    await flushPromises()

    expect(mockGetLearningDiagnosis).toHaveBeenCalledTimes(2)
    expect(wrapper.findComponent(LearningDiagnosisSummary).props('data')).toEqual(diagnosis)
  })

  it('keeps question error analysis requests in the page orchestrator', async () => {
    const analysis = {
      questionId: 10,
      questionContent: '测试题',
      questionType: 'SINGLE_CHOICE',
      difficulty: 1,
      courseName: '数据结构',
      knowledgePointName: '线性表',
      totalAttempts: 2,
      correctCount: 1,
      wrongCount: 1,
      correctRate: 50,
      currentMasteryLevel: 1,
      masteryTrend: 'STAGNANT',
      trendDescription: '保持稳定',
      attempts: [],
      errorPattern: '概念混淆',
    }
    mockGetQuestionErrorAnalysis.mockResolvedValue({ data: analysis })
    const wrapper = mountView()
    await flushPromises()

    wrapper.findComponent(LearningDiagnosisErrorPatterns).vm.$emit('question-error-analysis', 10)
    await flushPromises()

    expect(mockGetQuestionErrorAnalysis).toHaveBeenCalledWith(10, { errorDisplay: 'inline' })
    expect(wrapper.findComponent(QuestionErrorAnalysisDialog).props('data')).toEqual(analysis)
  })

  it('keeps a failed error-analysis request in its dialog and retries the same question', async () => {
    mockGetQuestionErrorAnalysis.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({ data: null })
    const wrapper = mountView()
    await flushPromises()
    const patterns = wrapper.findComponent(LearningDiagnosisErrorPatterns)
    patterns.vm.$emit('question-error-analysis', 10)
    await flushPromises()
    const dialog = wrapper.findComponent(QuestionErrorAnalysisDialog)
    expect(dialog.props('error')).toContain('offline')
    dialog.vm.$emit('retry')
    await flushPromises()
    expect(mockGetQuestionErrorAnalysis).toHaveBeenNthCalledWith(2, 10, { errorDisplay: 'inline' })
  })

  it('loads recommended questions and starts the existing practice session flow', async () => {
    mockGetQuestionById.mockResolvedValue({ data: { id: 10, content: '测试题' } })
    const wrapper = mountView()
    await flushPromises()

    wrapper.findComponent(LearningDiagnosisRecommendations).vm.$emit('start-recommend-practice')
    await flushPromises()

    expect(mockGetQuestionById).toHaveBeenCalledWith(10, { errorDisplay: 'inline' })
    expect(sessionStorage.getItem('practice_mode')).toBe('recommended')
    expect(JSON.parse(sessionStorage.getItem('practice_questions') || '[]')).toEqual([{ id: 10, content: '测试题' }])
    expect(mockPush).toHaveBeenCalledWith({ path: '/practice/session' })
  })

  it('ignores a late diagnosis response after the authenticated session changes', async () => {
    let resolveFirst: (value: { data: LearningDiagnosis }) => void = () => undefined
    const first = new Promise<{ data: LearningDiagnosis }>((resolve) => {
      resolveFirst = resolve
    })
    const current = { ...diagnosis, totalPractice: 13, dailyAdvice: '当前账号的建议' }
    mockGetLearningDiagnosis.mockReset().mockReturnValueOnce(first).mockResolvedValueOnce({ data: current })

    const wrapper = mountView()
    setToken('rotated-test-token')
    await flushPromises()
    resolveFirst({ data: diagnosis })
    await flushPromises()

    expect(mockGetLearningDiagnosis).toHaveBeenCalledTimes(2)
    expect(wrapper.findComponent(LearningDiagnosisSummary).props('data')).toEqual(current)
  })

  it('keeps the newest similar-question result when requests for two questions overlap', async () => {
    let resolveFirst: (value: { data: { sourceQuestionId: number; similarQuestions: [] } }) => void = () => undefined
    const first = new Promise<{ data: { sourceQuestionId: number; similarQuestions: [] } }>((resolve) => {
      resolveFirst = resolve
    })
    const latest = { sourceQuestionId: 20, similarQuestions: [] }
    mockGetSimilarQuestions.mockReturnValueOnce(first).mockResolvedValueOnce({ data: latest })
    const wrapper = mountView()
    await flushPromises()

    const patterns = wrapper.findComponent(LearningDiagnosisErrorPatterns)
    patterns.vm.$emit('similar-question', 10, '第一题')
    patterns.vm.$emit('similar-question', 20, '第二题')
    await flushPromises()
    resolveFirst({ data: { sourceQuestionId: 10, similarQuestions: [] } })
    await flushPromises()

    expect(wrapper.findComponent(SimilarQuestionDialog).props('data')).toEqual(latest)
    expect(wrapper.findComponent(SimilarQuestionDialog).props('sourceContent')).toBe('第二题')
  })

  it('keeps the newest error-analysis result when requests for two questions overlap', async () => {
    let resolveFirst: (value: { data: { questionId: number } }) => void = () => undefined
    const first = new Promise<{ data: { questionId: number } }>((resolve) => {
      resolveFirst = resolve
    })
    const latest = { questionId: 20 }
    mockGetQuestionErrorAnalysis.mockReturnValueOnce(first).mockResolvedValueOnce({ data: latest })
    const wrapper = mountView()
    await flushPromises()

    const patterns = wrapper.findComponent(LearningDiagnosisErrorPatterns)
    patterns.vm.$emit('question-error-analysis', 10)
    patterns.vm.$emit('question-error-analysis', 20)
    await flushPromises()
    resolveFirst({ data: { questionId: 10 } })
    await flushPromises()

    expect(wrapper.findComponent(QuestionErrorAnalysisDialog).props('data')).toEqual(latest)
  })

  it('keeps a similar-practice failure in the dialog and disables its only action while starting', async () => {
    const similar = {
      sourceQuestionId: 10,
      similarQuestions: [{ questionId: 11, questionContent: '相似题' }],
    }
    mockGetSimilarQuestions.mockResolvedValue({ data: similar })
    mockGetQuestionById.mockRejectedValue(new Error('offline'))
    const wrapper = mountView()
    await flushPromises()

    wrapper.findComponent(LearningDiagnosisErrorPatterns).vm.$emit('similar-question', 10, '原题')
    await flushPromises()
    const dialog = wrapper.findComponent(SimilarQuestionDialog)
    dialog.vm.$emit('start-practice')
    await flushPromises()

    expect(dialog.props('starting')).toBe(false)
    expect(dialog.props('practiceError')).toContain('offline')
    expect(mockPush).not.toHaveBeenCalled()
  })

  it('clears old account AI and dialog state before loading the new authenticated session', async () => {
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(new TextEncoder().encode('event: content\ndata: {"content":"旧账号建议"}\n\n'))
        controller.close()
      },
    })
    mockGetAiAdviceStream.mockResolvedValue({ ok: true, body: stream })
    mockGetSimilarQuestions.mockResolvedValue({ data: { sourceQuestionId: 10, similarQuestions: [] } })
    mockGetQuestionErrorAnalysis.mockRejectedValue(new Error('旧账号错因失败'))
    const current = { ...diagnosis, totalPractice: 13, dailyAdvice: '新账号建议' }
    mockGetLearningDiagnosis.mockResolvedValue({ data: current })
    const wrapper = mountView()
    await flushPromises()

    wrapper.findComponent(LearningDiagnosisErrorPatterns).vm.$emit('similar-question', 10, '旧题')
    wrapper.findComponent(LearningDiagnosisErrorPatterns).vm.$emit('question-error-analysis', 10)
    wrapper.findComponent(LearningDiagnosisSummary).vm.$emit('generate-ai-advice')
    await flushPromises()
    expect(wrapper.findComponent(SimilarQuestionDialog).props('data')).toEqual({
      sourceQuestionId: 10,
      similarQuestions: [],
    })
    expect(wrapper.findComponent(LearningDiagnosisSummary).props('aiAdviceContent')).toContain('旧账号建议')
    expect(wrapper.findComponent(QuestionErrorAnalysisDialog).props('error')).toContain('旧账号错因失败')

    setToken('new-session-token')
    await flushPromises()

    expect(wrapper.findComponent(SimilarQuestionDialog).props('modelValue')).toBe(false)
    expect(wrapper.findComponent(SimilarQuestionDialog).props('data')).toBeNull()
    expect(wrapper.findComponent(QuestionErrorAnalysisDialog).props('modelValue')).toBe(false)
    expect(wrapper.findComponent(QuestionErrorAnalysisDialog).props('error')).toBe('')
    expect(wrapper.findComponent(LearningDiagnosisSummary).props('aiAdviceContent')).toBe('')
    expect(wrapper.findComponent(LearningDiagnosisSummary).props('aiAdviceError')).toBe('')
    expect(wrapper.findComponent(LearningDiagnosisSummary).props('data')).toEqual(current)
  })

  it('does not let a cancelled older AI request report an error during a newer request', async () => {
    let rejectFirst: (reason?: unknown) => void = () => undefined
    const first = new Promise<Response>((_resolve, reject) => {
      rejectFirst = reject
    })
    const second = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.close()
      },
    })
    mockGetAiAdviceStream.mockReturnValueOnce(first).mockResolvedValueOnce({ ok: true, body: second })
    const wrapper = mountView()
    await flushPromises()
    const summary = wrapper.findComponent(LearningDiagnosisSummary)

    summary.vm.$emit('generate-ai-advice')
    await flushPromises()
    summary.vm.$emit('stop-ai-advice')
    summary.vm.$emit('generate-ai-advice')
    await flushPromises()
    rejectFirst(new Error('older request failed'))
    await flushPromises()

    expect(wrapper.findComponent(LearningDiagnosisSummary).props('aiAdviceError')).toBe('')
  })

  it('ignores a late chunk from a stopped stream after a newer stream starts', async () => {
    let releaseOldChunk: () => void = () => undefined
    const oldStream = new ReadableStream<Uint8Array>({
      async pull(controller) {
        await new Promise<void>((resolve) => {
          releaseOldChunk = resolve
        })
        controller.enqueue(new TextEncoder().encode('event: content\ndata: {"content":"旧流内容"}\n\n'))
        controller.close()
      },
    })
    const newStream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(new TextEncoder().encode('event: content\ndata: {"content":"新流内容"}\n\n'))
        controller.close()
      },
    })
    mockGetAiAdviceStream
      .mockReturnValueOnce({ ok: true, body: oldStream })
      .mockReturnValueOnce({ ok: true, body: newStream })
    const wrapper = mountView()
    await flushPromises()
    const summary = wrapper.findComponent(LearningDiagnosisSummary)

    summary.vm.$emit('generate-ai-advice')
    await flushPromises()
    summary.vm.$emit('stop-ai-advice')
    summary.vm.$emit('generate-ai-advice')
    await flushPromises()
    releaseOldChunk()
    await flushPromises()

    expect(wrapper.findComponent(LearningDiagnosisSummary).props('aiAdviceContent')).toBe('新流内容')
  })

  it('keeps streamed content and reports an SSE business error split across chunks', async () => {
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(new TextEncoder().encode('event: content\ndata: {"content":"已收到"'))
        controller.enqueue(new TextEncoder().encode('}\n\nevent: error\ndata: {"message":"额度已用完"}\n\n'))
        controller.close()
      },
    })
    mockGetAiAdviceStream.mockResolvedValue({ ok: true, body: stream })
    const wrapper = mountView()
    await flushPromises()

    wrapper.findComponent(LearningDiagnosisSummary).vm.$emit('generate-ai-advice')
    await flushPromises()

    const summary = wrapper.findComponent(LearningDiagnosisSummary)
    expect(summary.props('aiAdviceContent')).toContain('已收到')
    expect(summary.props('aiAdviceError')).toContain('额度已用完')
    expect(summary.props('aiAdviceLoading')).toBe(false)
  })

  it('cancels the AI stream on unmount and does not let a late response update the view', async () => {
    mockGetAiAdviceStream.mockImplementation(() => new Promise<Response>(() => undefined))
    const wrapper = mountView()
    await flushPromises()

    wrapper.findComponent(LearningDiagnosisSummary).vm.$emit('generate-ai-advice')
    await flushPromises()
    const signal = mockGetAiAdviceStream.mock.calls[0][0] as AbortSignal
    expect(signal.aborted).toBe(false)

    wrapper.unmount()
    expect(signal.aborted).toBe(true)
  })
})
