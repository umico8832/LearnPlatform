import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock the request module
vi.mock('@/utils/request', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

import request from '@/utils/request'
import { getLearningPlan, updateDailyGoal } from '@/api/learningPlan'

const mockedRequest = vi.mocked(request)

describe('LearningPlan API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getLearningPlan', () => {
    it('应使用 GET 请求获取学习计划', async () => {
      const mockPlan = {
        dailyGoal: 20,
        todayCount: 8,
        progress: 40,
        streakDays: 5,
        lastPracticeDate: '2024-01-15',
      }
      mockedRequest.get.mockResolvedValue({ code: 0, data: mockPlan, message: 'success' })

      const result = await getLearningPlan()

      expect(mockedRequest.get).toHaveBeenCalledWith('/learning-plan')
      expect(result).toEqual({ code: 0, data: mockPlan, message: 'success' })
    })

    it('应能处理 lastPracticeDate 为 null 的情况', async () => {
      const mockPlan = {
        dailyGoal: 20,
        todayCount: 0,
        progress: 0,
        streakDays: 0,
        lastPracticeDate: null,
      }
      mockedRequest.get.mockResolvedValue({ code: 0, data: mockPlan, message: 'success' })

      const result = await getLearningPlan()

      expect(mockedRequest.get).toHaveBeenCalledWith('/learning-plan')
      expect(result.data.lastPracticeDate).toBeNull()
    })
  })

  describe('updateDailyGoal', () => {
    it('应使用 PUT 请求更新每日刷题目标', async () => {
      const mockPlan = {
        dailyGoal: 30,
        todayCount: 8,
        progress: 27,
        streakDays: 5,
        lastPracticeDate: '2024-01-15',
      }
      mockedRequest.put.mockResolvedValue({ code: 0, data: mockPlan, message: 'success' })

      const result = await updateDailyGoal(30)

      expect(mockedRequest.put).toHaveBeenCalledWith('/learning-plan', { dailyGoal: 30 })
      expect(result).toEqual({ code: 0, data: mockPlan, message: 'success' })
    })

    it('应支持设置较小的目标值', async () => {
      mockedRequest.put.mockResolvedValue({ code: 0, data: {}, message: 'success' })

      await updateDailyGoal(5)

      expect(mockedRequest.put).toHaveBeenCalledWith('/learning-plan', { dailyGoal: 5 })
    })
  })
})
