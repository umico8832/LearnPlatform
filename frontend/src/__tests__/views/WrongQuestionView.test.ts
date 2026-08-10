import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const { mockGetWrongQuestions, mockGetWrongQuestionStats, mockPush } = vi.hoisted(() => ({
  mockGetWrongQuestions: vi.fn(),
  mockGetWrongQuestionStats: vi.fn(),
  mockPush: vi.fn(),
}))

vi.mock('@/api/wrongQuestion', () => ({
  getWrongQuestions: (...args: unknown[]) => mockGetWrongQuestions(...args),
  getWrongQuestionStats: (...args: unknown[]) => mockGetWrongQuestionStats(...args),
  updateMasteryLevel: vi.fn(),
  removeWrongQuestion: vi.fn(),
}))
vi.mock('@/api/practice', () => ({ getWrongQuestionPractice: vi.fn() }))
vi.mock('@/api/statistics', () => ({ getSimilarQuestions: vi.fn() }))
vi.mock('vue-router', async (importOriginal) => ({
  ...await importOriginal<typeof import('vue-router')>(),
  useRoute: () => ({ query: { courseId: '408', questionId: '22' } }),
  useRouter: () => ({ push: mockPush }),
}))

import WrongQuestionView from '@/views/practice/WrongQuestionView.vue'

describe('WrongQuestionView course target', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetWrongQuestionStats.mockResolvedValue({
      code: 0,
      data: { total: 1, unmastered: 1, partial: 0, mastered: 0, courseWrongCount: {} },
    })
    mockGetWrongQuestions.mockResolvedValue({
      code: 0,
      data: { records: [], total: 0 },
    })
  })

  it('从课程总览进入时在服务端分页前限定课程和目标题目', async () => {
    mount(WrongQuestionView, {
      global: {
        stubs: { AiQuestionAssistant: true, QuestionLearningAsset: true },
        directives: { loading: () => undefined },
      },
    })
    await flushPromises()

    expect(mockGetWrongQuestions).toHaveBeenCalledWith({
      pageNum: 1,
      pageSize: 10,
      courseId: 408,
      questionId: 22,
    })
  })
})
