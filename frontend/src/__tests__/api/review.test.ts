import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
  },
}))

import request from '@/utils/request'
import {
  addToReviewPlan,
  getAiReviewSuggestion,
  getAiReviewSuggestionStream,
  getAllReviewCards,
  getDueReviewCards,
  getReviewStats,
  removeFromReviewPlan,
  resetReviewProgress,
  submitReview,
  syncWrongQuestionsToReview,
} from '@/api/review'

const mockedRequest = vi.mocked(request)

describe('Review API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('应使用 GET 请求获取复习统计且不重复 /api 前缀', async () => {
    mockedRequest.get.mockResolvedValue({ dueToday: 0, totalCards: 0 })

    await getReviewStats()

    expect(mockedRequest.get).toHaveBeenCalledWith('/review/stats')
  })

  it('应使用 GET 请求获取待复习题目', async () => {
    mockedRequest.get.mockResolvedValue([])

    await getDueReviewCards(2, 30, 21)

    expect(mockedRequest.get).toHaveBeenCalledWith('/review/due', {
      params: { courseId: 2, questionId: 21, limit: 30 },
    })
  })

  it('应使用 GET 请求获取全部复习卡片', async () => {
    mockedRequest.get.mockResolvedValue([])

    await getAllReviewCards(3)

    expect(mockedRequest.get).toHaveBeenCalledWith('/review/cards', {
      params: { courseId: 3 },
    })
  })

  it('应使用 POST 请求管理复习计划', async () => {
    mockedRequest.post.mockResolvedValue({})

    await addToReviewPlan(11)
    await resetReviewProgress(11)
    await syncWrongQuestionsToReview()
    await getAiReviewSuggestion()

    expect(mockedRequest.post).toHaveBeenCalledWith('/review/add/11')
    expect(mockedRequest.post).toHaveBeenCalledWith('/review/reset/11')
    expect(mockedRequest.post).toHaveBeenCalledWith('/review/sync-wrong-questions')
    expect(mockedRequest.post).toHaveBeenCalledWith('/review/ai-suggestion')
  })

  it('应使用 POST 请求提交复习答案', async () => {
    mockedRequest.post.mockResolvedValue({})

    await submitReview({ questionId: 9, userAnswer: 'A' })

    expect(mockedRequest.post).toHaveBeenCalledWith('/review/submit', {
      questionId: 9,
      userAnswer: 'A',
    })
  })

  it('应使用 DELETE 请求移出复习计划', async () => {
    mockedRequest.delete.mockResolvedValue({})

    await removeFromReviewPlan(12)

    expect(mockedRequest.delete).toHaveBeenCalledWith('/review/remove/12')
  })

  it('流式 AI 复习建议应使用默认 /api 基础路径', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response())
    vi.stubGlobal('fetch', fetchMock)

    await getAiReviewSuggestionStream('token-1')

    expect(fetchMock).toHaveBeenCalledWith('/api/review/ai-suggestion/stream', {
      method: 'POST',
      headers: {
        Authorization: 'Bearer token-1',
        Accept: 'text/event-stream',
      },
    })
  })
})
