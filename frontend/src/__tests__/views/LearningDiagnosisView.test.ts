import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import type { LearningDiagnosis } from '@/api/statistics'
import LearningDiagnosisErrorPatterns from '@/components/statistics/LearningDiagnosisErrorPatterns.vue'
import LearningDiagnosisRecommendations from '@/components/statistics/LearningDiagnosisRecommendations.vue'
import LearningDiagnosisSummary from '@/components/statistics/LearningDiagnosisSummary.vue'
import QuestionErrorAnalysisDialog from '@/components/statistics/QuestionErrorAnalysisDialog.vue'
import LearningDiagnosisView from '@/views/statistics/LearningDiagnosisView.vue'

const {
  mockGetLearningDiagnosis,
  mockGetQuestionErrorAnalysis,
  mockGetSimilarQuestions,
  mockGetQuestionById,
  mockPush,
} = vi.hoisted(() => ({
  mockGetLearningDiagnosis: vi.fn(),
  mockGetQuestionErrorAnalysis: vi.fn(),
  mockGetSimilarQuestions: vi.fn(),
  mockGetQuestionById: vi.fn(),
  mockPush: vi.fn(),
}))

vi.mock('@/api/statistics', () => ({
  getLearningDiagnosis: (...args: unknown[]) => mockGetLearningDiagnosis(...args),
  getQuestionErrorAnalysis: (...args: unknown[]) => mockGetQuestionErrorAnalysis(...args),
  getSimilarQuestions: (...args: unknown[]) => mockGetSimilarQuestions(...args),
  getAiAdviceStream: vi.fn(),
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
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    mockGetLearningDiagnosis.mockResolvedValue({ data: diagnosis })
  })

  function mountView() {
    return shallowMount(LearningDiagnosisView, {
      global: { mocks: { $router: { back: vi.fn() } } },
    })
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

    expect(mockGetQuestionErrorAnalysis).toHaveBeenCalledWith(10)
    expect(wrapper.findComponent(QuestionErrorAnalysisDialog).props('data')).toEqual(analysis)
  })

  it('loads recommended questions and starts the existing practice session flow', async () => {
    mockGetQuestionById.mockResolvedValue({ data: { id: 10, content: '测试题' } })
    const wrapper = mountView()
    await flushPromises()

    wrapper.findComponent(LearningDiagnosisRecommendations).vm.$emit('start-recommend-practice')
    await flushPromises()

    expect(mockGetQuestionById).toHaveBeenCalledWith(10)
    expect(sessionStorage.getItem('practice_mode')).toBe('recommended')
    expect(JSON.parse(sessionStorage.getItem('practice_questions') || '[]')).toEqual([{ id: 10, content: '测试题' }])
    expect(mockPush).toHaveBeenCalledWith({ path: '/practice/session' })
  })
})
