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
let routeQuery: Record<string, string> = { courseId: '408', questionId: '22' }

vi.mock('vue-router', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue-router')>()),
  useRoute: () => ({ query: routeQuery }),
  useRouter: () => ({ push: mockPush, replace: mockPush }),
}))

import WrongQuestionView from '@/views/practice/WrongQuestionView.vue'

describe('WrongQuestionView course target', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    routeQuery = { courseId: '408', questionId: '22' }
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

  it('从测评复盘进入时按知识点筛选并展示可清除的筛选标记', async () => {
    routeQuery = { courseId: '408', knowledgePointId: '31', knowledgePointName: '栈' }
    const wrapper = mount(WrongQuestionView, {
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
      knowledgePointId: 31,
    })
    expect(wrapper.text()).toContain('知识点：栈')
  })
})
