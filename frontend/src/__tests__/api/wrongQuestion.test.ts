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
import {
  getWrongQuestions,
  getWrongQuestionStats,
  updateMasteryLevel,
  removeWrongQuestion,
} from '@/api/wrongQuestion'

const mockedRequest = vi.mocked(request)

describe('WrongQuestion API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getWrongQuestions', () => {
    it('应使用 GET 请求获取错题本列表', async () => {
      const mockPageData = {
        records: [
          {
            id: 1,
            questionId: 10,
            questionContent: '题目内容',
            questionType: 'SINGLE_CHOICE',
            courseId: 1,
            courseName: '课程1',
            difficulty: 3,
            wrongCount: 2,
            masteryLevel: 0,
            lastWrongAnswer: 'B',
            createTime: '2024-01-01',
            updateTime: '2024-01-02',
          },
        ],
        total: 1,
        pageNum: 1,
        pageSize: 20,
      }
      mockedRequest.get.mockResolvedValue({ code: 0, data: mockPageData, message: 'success' })

      const result = await getWrongQuestions({ pageNum: 1, pageSize: 20 })

      expect(mockedRequest.get).toHaveBeenCalledWith('/wrong-questions', {
        params: { pageNum: 1, pageSize: 20 },
      })
      expect(result).toEqual({ code: 0, data: mockPageData, message: 'success' })
    })

    it('应支持按课程和掌握程度筛选', async () => {
      mockedRequest.get.mockResolvedValue({ code: 0, data: { records: [], total: 0 }, message: 'success' })

      await getWrongQuestions({ pageNum: 1, pageSize: 10, courseId: 2, masteryLevel: 0 })

      expect(mockedRequest.get).toHaveBeenCalledWith('/wrong-questions', {
        params: { pageNum: 1, pageSize: 10, courseId: 2, masteryLevel: 0 },
      })
    })

    it('无参数时应调用正确路径', async () => {
      mockedRequest.get.mockResolvedValue({ code: 0, data: { records: [], total: 0 }, message: 'success' })

      await getWrongQuestions({})

      expect(mockedRequest.get).toHaveBeenCalledWith('/wrong-questions', { params: {} })
    })
  })

  describe('getWrongQuestionStats', () => {
    it('应使用 GET 请求获取错题统计', async () => {
      const mockStats = {
        total: 50,
        unmastered: 20,
        partial: 15,
        mastered: 15,
        courseWrongCount: { 'Java基础': 10, '数据结构': 8 },
      }
      mockedRequest.get.mockResolvedValue({ code: 0, data: mockStats, message: 'success' })

      const result = await getWrongQuestionStats()

      expect(mockedRequest.get).toHaveBeenCalledWith('/wrong-questions/stats')
      expect(result).toEqual({ code: 0, data: mockStats, message: 'success' })
    })
  })

  describe('updateMasteryLevel', () => {
    it('应使用 PUT 请求更新掌握程度', async () => {
      mockedRequest.put.mockResolvedValue({ code: 0, data: null, message: 'success' })

      await updateMasteryLevel(5, 2)

      expect(mockedRequest.put).toHaveBeenCalledWith('/wrong-questions/5/mastery', null, {
        params: { masteryLevel: 2 },
      })
    })

    it('应支持设置为未掌握', async () => {
      mockedRequest.put.mockResolvedValue({ code: 0, data: null, message: 'success' })

      await updateMasteryLevel(3, 0)

      expect(mockedRequest.put).toHaveBeenCalledWith('/wrong-questions/3/mastery', null, {
        params: { masteryLevel: 0 },
      })
    })
  })

  describe('removeWrongQuestion', () => {
    it('应使用 DELETE 请求移出错题本', async () => {
      mockedRequest.delete.mockResolvedValue({ code: 0, data: null, message: 'success' })

      await removeWrongQuestion(7)

      expect(mockedRequest.delete).toHaveBeenCalledWith('/wrong-questions/7')
    })
  })
})