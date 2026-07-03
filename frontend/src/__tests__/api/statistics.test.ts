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
import {
  getStatisticsOverview,
  getDailyTrend,
  getCourseStats,
  getAdminStatisticsOverview,
  getLearningReport,
  getAiAdviceStream,
} from '@/api/statistics'

const mockedRequest = vi.mocked(request)

describe('Statistics API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.unstubAllEnvs()
    vi.unstubAllGlobals()
    localStorage.clear()
  })

  describe('getStatisticsOverview', () => {
    it('应使用 GET 请求获取学习统计概览', async () => {
      const mockOverview = {
        totalPractice: 150,
        correctCount: 120,
        wrongCount: 30,
        correctRate: 0.8,
        todayPractice: 10,
        streakDays: 5,
        wrongQuestionCount: 25,
        masteredCount: 15,
      }
      mockedRequest.get.mockResolvedValue({ code: 0, data: mockOverview, message: 'success' })

      const result = await getStatisticsOverview()

      expect(mockedRequest.get).toHaveBeenCalledWith('/statistics/overview')
      expect(result).toEqual({ code: 0, data: mockOverview, message: 'success' })
    })
  })

  describe('getDailyTrend', () => {
    it('应使用 GET 请求获取每日刷题趋势', async () => {
      const mockTrend = [
        { date: '2026-06-08', total: 20, correct: 15, wrong: 5 },
        { date: '2026-06-09', total: 15, correct: 12, wrong: 3 },
      ]
      mockedRequest.get.mockResolvedValue({ code: 0, data: mockTrend, message: 'success' })

      const result = await getDailyTrend()

      expect(mockedRequest.get).toHaveBeenCalledWith('/statistics/daily-trend')
      expect(result).toEqual({ code: 0, data: mockTrend, message: 'success' })
    })

    it('无数据时返回空数组', async () => {
      mockedRequest.get.mockResolvedValue({ code: 0, data: [], message: 'success' })

      const result = await getDailyTrend()

      expect(result).toEqual({ code: 0, data: [], message: 'success' })
    })
  })

  describe('getCourseStats', () => {
    it('应使用 GET 请求获取课程维度统计', async () => {
      const mockCourseStats = [
        { courseId: 1, courseName: 'Java基础', total: 50, correct: 40, correctRate: 0.8 },
        { courseId: 2, courseName: '数据库', total: 30, correct: 24, correctRate: 0.8 },
      ]
      mockedRequest.get.mockResolvedValue({ code: 0, data: mockCourseStats, message: 'success' })

      const result = await getCourseStats()

      expect(mockedRequest.get).toHaveBeenCalledWith('/statistics/course-stats')
      expect(result).toEqual({ code: 0, data: mockCourseStats, message: 'success' })
    })
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
        questionTypeDistribution: { SINGLE_CHOICE: 200, MULTI_CHOICE: 150, TRUE_FALSE: 100, FILL_BLANK: 30, SHORT_ANSWER: 20 },
        dailyActivity: [
          { date: '2026-06-08', practiceCount: 100, activeUsers: 10 },
        ],
      }
      mockedRequest.get.mockResolvedValue({ code: 0, data: mockAdminOverview, message: 'success' })

      const result = await getAdminStatisticsOverview()

      expect(mockedRequest.get).toHaveBeenCalledWith('/admin/statistics/overview')
      expect(result).toEqual({ code: 0, data: mockAdminOverview, message: 'success' })
    })
  })

  describe('getLearningReport', () => {
    it('应使用 GET 请求获取个人学习报告', async () => {
      const mockReport = {
        monthTotalPractice: 200,
        monthCorrectCount: 160,
        monthCorrectRate: 0.8,
        monthNewWrongCount: 10,
        monthMasteredCount: 8,
        monthExamCount: 3,
        monthExamAvgScore: 85,
        lastMonthTotalPractice: 150,
        lastMonthCorrectRate: 0.75,
        practiceGrowthRate: 0.33,
        correctRateChange: 5,
        learningEffectScore: 82,
        learningEffectLevel: 'IMPROVING',
        learningEffectLabel: '稳步提升',
        learningEffectSummary: '学习效果正在提升',
        wrongQuestionConversionRate: 44.4,
        reviewMasteryRate: 33.3,
        activeStudyDays: 12,
        dailyTrend: [],
        courseStats: [],
        questionTypeDistribution: { SINGLE_CHOICE: 100, MULTI_CHOICE: 60 },
      }
      mockedRequest.get.mockResolvedValue({ code: 0, data: mockReport, message: 'success' })

      const result = await getLearningReport()

      expect(mockedRequest.get).toHaveBeenCalledWith('/statistics/learning-report')
      expect(result).toEqual({ code: 0, data: mockReport, message: 'success' })
    })
  })

  describe('getAiAdviceStream', () => {
    it('应使用配置的 API Base URL 发起流式请求', async () => {
      vi.stubEnv('VITE_API_BASE_URL', '/custom-api')
      localStorage.setItem('token', 'jwt-token')
      const fetchMock = vi.fn().mockResolvedValue(new Response())
      vi.stubGlobal('fetch', fetchMock)

      await getAiAdviceStream()

      expect(fetchMock).toHaveBeenCalledWith('/custom-api/statistics/ai-advice/stream', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer jwt-token',
        },
      })
    })
  })
})
