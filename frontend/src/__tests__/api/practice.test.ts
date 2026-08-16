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
  getPracticeQuestions,
  submitAnswer,
  getPracticeRecords,
  getPracticeStats,
  getWrongQuestionPractice,
  getFavoritePractice,
  getAdaptiveQuestions,
  getAdaptiveSummary,
} from '@/api/practice'

const mockedRequest = vi.mocked(request)

describe('Practice API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getPracticeQuestions', () => {
    it('应使用 GET 请求获取练习题目', async () => {
      const mockData = [
        { id: 1, content: '题目1', questionType: 'SINGLE_CHOICE', difficulty: 3 },
        { id: 2, content: '题目2', questionType: 'MULTI_CHOICE', difficulty: 4 },
      ]
      mockedRequest.get.mockResolvedValue({ code: 0, data: mockData, message: 'success' })

      const result = await getPracticeQuestions({ count: 10 })

      expect(mockedRequest.get).toHaveBeenCalledWith('/practice/questions', { params: { count: 10 } })
      expect(result).toEqual({ code: 0, data: mockData, message: 'success' })
    })

    it('无参数时应调用正确路径', async () => {
      mockedRequest.get.mockResolvedValue({ code: 0, data: [], message: 'success' })

      await getPracticeQuestions()

      expect(mockedRequest.get).toHaveBeenCalledWith('/practice/questions', { params: undefined })
    })

    it('应支持按课程、知识点、题型、难度筛选', async () => {
      mockedRequest.get.mockResolvedValue({ code: 0, data: [], message: 'success' })

      await getPracticeQuestions({
        courseId: 1,
        knowledgePointId: 5,
        questionType: 'SINGLE_CHOICE',
        difficulty: 3,
        count: 5,
      })

      expect(mockedRequest.get).toHaveBeenCalledWith('/practice/questions', {
        params: { courseId: 1, knowledgePointId: 5, questionType: 'SINGLE_CHOICE', difficulty: 3, count: 5 },
      })
    })
  })

  describe('submitAnswer', () => {
    it('应使用 POST 请求提交答案', async () => {
      const mockResult = {
        recordId: 100,
        questionId: 1,
        userAnswer: 'A',
        correct: true,
        correctAnswer: 'A',
        analysis: '解析内容',
        score: 5,
      }
      mockedRequest.post.mockResolvedValue({ code: 0, data: mockResult, message: 'success' })

      const result = await submitAnswer({ questionId: 1, userAnswer: 'A', answerTime: 30 })

      expect(mockedRequest.post).toHaveBeenCalledWith('/practice/submit', {
        questionId: 1,
        userAnswer: 'A',
        answerTime: 30,
      })
      expect(result).toEqual({ code: 0, data: mockResult, message: 'success' })
    })

    it('应支持不传 answerTime', async () => {
      mockedRequest.post.mockResolvedValue({ code: 0, data: {}, message: 'success' })

      await submitAnswer({ questionId: 1, userAnswer: 'B' })

      expect(mockedRequest.post).toHaveBeenCalledWith('/practice/submit', { questionId: 1, userAnswer: 'B' })
    })
  })

  describe('getPracticeRecords', () => {
    it('应使用 GET 请求获取练习记录', async () => {
      const mockPageData = { records: [], total: 0, pageNum: 1, pageSize: 20 }
      mockedRequest.get.mockResolvedValue({ code: 0, data: mockPageData, message: 'success' })

      await getPracticeRecords({ pageNum: 1, pageSize: 20 })

      expect(mockedRequest.get).toHaveBeenCalledWith('/practice/records', {
        params: { pageNum: 1, pageSize: 20 },
      })
    })

    it('应支持按题型、课程和正确状态筛选', async () => {
      mockedRequest.get.mockResolvedValue({ code: 0, data: { records: [], total: 0 }, message: 'success' })

      await getPracticeRecords({
        pageNum: 1,
        pageSize: 10,
        questionType: 'FILL_BLANK',
        courseId: 2,
        isCorrect: 0,
      })

      expect(mockedRequest.get).toHaveBeenCalledWith('/practice/records', {
        params: { pageNum: 1, pageSize: 10, questionType: 'FILL_BLANK', courseId: 2, isCorrect: 0 },
      })
    })
  })

  describe('getPracticeStats', () => {
    it('应使用 GET 请求获取练习统计', async () => {
      const mockStats = { totalAnswered: 100, correctCount: 80, wrongCount: 20, correctRate: 0.8 }
      mockedRequest.get.mockResolvedValue({ code: 0, data: mockStats, message: 'success' })

      const result = await getPracticeStats()

      expect(mockedRequest.get).toHaveBeenCalledWith('/practice/stats')
      expect(result).toEqual({ code: 0, data: mockStats, message: 'success' })
    })
  })

  describe('getWrongQuestionPractice', () => {
    it('应使用 GET 请求获取错题重练题目', async () => {
      mockedRequest.get.mockResolvedValue({ code: 0, data: [], message: 'success' })

      await getWrongQuestionPractice({ masteryLevel: 1, count: 5 })

      expect(mockedRequest.get).toHaveBeenCalledWith('/practice/wrong-questions', {
        params: { masteryLevel: 1, count: 5 },
      })
    })

    it('无参数时应调用正确路径', async () => {
      mockedRequest.get.mockResolvedValue({ code: 0, data: [], message: 'success' })

      await getWrongQuestionPractice()

      expect(mockedRequest.get).toHaveBeenCalledWith('/practice/wrong-questions', { params: undefined })
    })
  })

  describe('getFavoritePractice', () => {
    it('应使用 GET 请求获取收藏题练习', async () => {
      mockedRequest.get.mockResolvedValue({ code: 0, data: [], message: 'success' })

      await getFavoritePractice({ count: 10 })

      expect(mockedRequest.get).toHaveBeenCalledWith('/practice/favorites', { params: { count: 10 } })
    })

    it('应支持指定单题练习', async () => {
      mockedRequest.get.mockResolvedValue({ code: 0, data: [], message: 'success' })

      await getFavoritePractice({ questionId: 42 })

      expect(mockedRequest.get).toHaveBeenCalledWith('/practice/favorites', { params: { questionId: 42 } })
    })
  })

  describe('getAdaptiveQuestions', () => {
    it('应使用 GET 请求获取自适应题目', async () => {
      mockedRequest.get.mockResolvedValue({ code: 0, data: [], message: 'success' })

      await getAdaptiveQuestions({ courseId: 1, count: 10 })

      expect(mockedRequest.get).toHaveBeenCalledWith('/practice/adaptive', {
        params: { courseId: 1, count: 10 },
      })
    })
  })

  describe('getAdaptiveSummary', () => {
    it('应使用 GET 请求获取自适应推荐摘要', async () => {
      const mockSummary = {
        totalAnswered: 50,
        overallCorrectRate: 0.7,
        recommendedDifficulty: 3,
        difficultyDetails: [
          { difficulty: 1, label: '简单', total: 10, correct: 9, correctRate: 0.9, weight: 0.1 },
          { difficulty: 3, label: '中等', total: 20, correct: 14, correctRate: 0.7, weight: 0.5 },
        ],
      }
      mockedRequest.get.mockResolvedValue({ code: 0, data: mockSummary, message: 'success' })

      const result = await getAdaptiveSummary()

      expect(mockedRequest.get).toHaveBeenCalledWith('/practice/adaptive/summary')
      expect(result).toEqual({ code: 0, data: mockSummary, message: 'success' })
    })
  })
})
