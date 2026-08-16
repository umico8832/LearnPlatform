import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const { mockGetReviewStats, mockGetDueReviewCards } = vi.hoisted(() => ({
  mockGetReviewStats: vi.fn(),
  mockGetDueReviewCards: vi.fn(),
}))

vi.mock('@/api/review', () => ({
  getReviewStats: (...args: unknown[]) => mockGetReviewStats(...args),
  getDueReviewCards: (...args: unknown[]) => mockGetDueReviewCards(...args),
  getAllReviewCards: vi.fn(),
  submitReview: vi.fn(),
  removeFromReviewPlan: vi.fn(),
  resetReviewProgress: vi.fn(),
  syncWrongQuestionsToReview: vi.fn(),
  getAiReviewSuggestionStream: vi.fn(),
}))

vi.mock('@/utils/auth', () => ({ getToken: vi.fn() }))
let routeQuery: Record<string, string> = { courseId: '408', questionId: '21' }

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: routeQuery }),
  useRouter: () => ({ replace: vi.fn() }),
}))

import ReviewView from '@/views/practice/ReviewView.vue'

describe('ReviewView course target', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    routeQuery = { courseId: '408', questionId: '21' }
    mockGetReviewStats.mockResolvedValue({
      data: {
        totalCards: 1,
        dueToday: 1,
        overdue: 0,
        reviewedToday: 0,
        newCards: 1,
        learningCards: 0,
        masteredCards: 0,
        difficultCards: 0,
        streakDays: 0,
        avgEaseFactor: 2.5,
      },
    })
    mockGetDueReviewCards.mockResolvedValue({
      data: [
        {
          id: 1,
          questionId: 21,
          questionContent: '课程目标复习题',
          questionType: 'SINGLE_CHOICE',
          courseId: 408,
          courseName: '408 数据结构',
          intervalDays: 1,
          repetitions: 0,
          overdue: false,
          overdueDays: 0,
          statusLabel: '新卡片',
        },
      ],
    })
  })

  it('从课程总览进入时自动加载并开始服务端选择的到期题', async () => {
    const wrapper = mount(ReviewView, {
      global: {
        stubs: { MarkdownRenderer: true },
        directives: { loading: () => undefined },
      },
    })
    await flushPromises()

    expect(mockGetDueReviewCards).toHaveBeenCalledWith(408, 30, 21, undefined)
    expect(wrapper.text()).toContain('课程目标复习题')
  })

  it('从知识点入口进入时按知识点筛选并展示可清除的筛选标记', async () => {
    routeQuery = { courseId: '408', knowledgePointId: '31', knowledgePointName: '栈' }
    const wrapper = mount(ReviewView, {
      global: {
        stubs: { MarkdownRenderer: true },
        directives: { loading: () => undefined },
      },
    })
    await flushPromises()

    expect(mockGetDueReviewCards).toHaveBeenCalledWith(408, 30, undefined, 31)
    expect(wrapper.text()).toContain('知识点：栈')
  })
})
