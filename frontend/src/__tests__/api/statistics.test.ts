import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

import request from '@/utils/request'
import { getAdminStatisticsOverview, getAiAdviceStream } from '@/api/statistics'

const mockedRequest = vi.mocked(request)

describe('Statistics API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.unstubAllEnvs()
    vi.unstubAllGlobals()
    localStorage.clear()
  })

  describe('getAdminStatisticsOverview', () => {
    it('应使用 GET 请求获取管理端统计概览', async () => {
      const mockAdminOverview = {
        totalUsers: 100,
        enabledUsers: 95,
        totalQuestions: 500,
        weeklyNewQuestions: 20,
        totalExamPapers: 10,
        publishedExamPapers: 8,
        draftExamPapers: 2,
        todayActiveUsers: 15,
        totalPracticeRecords: 3000,
        questionTypeDistribution: {
          SINGLE_CHOICE: 200,
          MULTI_CHOICE: 150,
          TRUE_FALSE: 100,
          FILL_BLANK: 30,
          SHORT_ANSWER: 20,
        },
        dailyActivity: [{ date: '2026-06-08', practiceCount: 100, activeUsers: 10 }],
      }
      mockedRequest.get.mockResolvedValue({ code: 0, data: mockAdminOverview, message: 'success' })

      const result = await getAdminStatisticsOverview()

      expect(mockedRequest.get).toHaveBeenCalledWith('/admin/statistics/overview')
      expect(result).toEqual({ code: 0, data: mockAdminOverview, message: 'success' })
    })
  })

  describe('getAiAdviceStream', () => {
    it('应使用配置的 API Base URL 发起流式请求', async () => {
      vi.stubEnv('VITE_API_BASE_URL', '/custom-api')
      localStorage.setItem('token', 'jwt-token')
      const fetchMock = vi.fn().mockResolvedValue(new Response())
      vi.stubGlobal('fetch', fetchMock)

      const result = await getAiAdviceStream()

      expect(fetchMock).toHaveBeenCalledWith(
        '/custom-api/statistics/ai-advice/stream',
        expect.objectContaining({
          method: 'POST',
          headers: expect.objectContaining({ Authorization: 'Bearer jwt-token' }),
        }),
      )
      expect(result).toBeInstanceOf(Response)
    })
  })
})
