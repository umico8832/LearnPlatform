import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock aiService from request module
const mockAiPost = vi.fn()
const mockAiGet = vi.fn()
vi.mock('@/utils/request', () => ({
  aiService: {
    post: (...args: unknown[]) => mockAiPost(...args),
    get: (...args: unknown[]) => mockAiGet(...args),
  },
}))

// Mock auth module for getToken
vi.mock('@/utils/auth', () => ({
  getToken: vi.fn(() => 'mock-jwt-token'),
}))

// Mock import.meta.env
vi.stubEnv('VITE_API_BASE_URL', '/api')

import {
  getExplanation,
  getVariant,
  getSummary,
  getAiUsage,
  recordAssetView,
  completeVariantTraining,
  submitVariantAnswer,
  streamQuestionAi,
  streamExamLearningAi,
} from '@/api/ai'

describe('AI API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getExplanation', () => {
    it('应使用 POST 请求生成题目解析', async () => {
      const mockData = { code: 0, data: { content: '解析内容', source: 'ai' }, message: 'success' }
      mockAiPost.mockResolvedValue(mockData)

      const result = await getExplanation(5)

      expect(mockAiPost).toHaveBeenCalledWith('/ai/explanation', { questionId: 5 })
      // ai API 函数使用 .then(res => res.data) 解包响应
      expect(result).toEqual({ content: '解析内容', source: 'ai' })
    })
  })

  describe('getVariant', () => {
    it('应使用 POST 请求生成变式题', async () => {
      const mockData = { code: 0, data: { content: '变式题内容', source: 'ai' }, message: 'success' }
      mockAiPost.mockResolvedValue(mockData)

      const result = await getVariant(10)

      expect(mockAiPost).toHaveBeenCalledWith('/ai/variant', { questionId: 10 })
      expect(result).toEqual({ content: '变式题内容', source: 'ai' })
    })
  })

  describe('getSummary', () => {
    it('应使用 POST 请求生成知识点总结', async () => {
      const mockData = { code: 0, data: { content: '知识点总结', source: 'ai' }, message: 'success' }
      mockAiPost.mockResolvedValue(mockData)

      const result = await getSummary(7)

      expect(mockAiPost).toHaveBeenCalledWith('/ai/summary', { knowledgePointId: 7 })
      expect(result).toEqual({ content: '知识点总结', source: 'ai' })
    })
  })

  describe('getAiUsage', () => {
    it('应使用 GET 请求查询 AI 调用用量', async () => {
      const mockData = { code: 0, data: { todayCount: 12, dailyQuota: 50 }, message: 'success' }
      mockAiGet.mockResolvedValue(mockData)

      const result = await getAiUsage()

      expect(mockAiGet).toHaveBeenCalledWith('/ai/usage')
      expect(result).toEqual({ todayCount: 12, dailyQuota: 50 })
    })
  })

  describe('recordAssetView', () => {
    it('records a visible cached learning asset', async () => {
      mockAiPost.mockResolvedValue({ code: 0, data: null, message: 'success' })

      await recordAssetView(10, 'STEP_BY_STEP')

      expect(mockAiPost).toHaveBeenCalledWith('/ai/asset/view', {
        questionId: 10,
        assetType: 'STEP_BY_STEP',
      })
    })
  })

  describe('completeVariantTraining', () => {
    it('records explicit completion for the current variant asset', async () => {
      mockAiPost.mockResolvedValue({
        data: {
          code: 0,
          data: { questionId: 10, assetId: 3, status: 'COMPLETED', completed: true },
          message: 'success',
        },
      })

      const result = await completeVariantTraining(10)

      expect(mockAiPost).toHaveBeenCalledWith('/ai/variant-training/10/complete')
      expect(result.data.completed).toBe(true)
    })
  })

  describe('submitVariantAnswer', () => {
    it('submits the selected answer for server-side grading', async () => {
      mockAiPost.mockResolvedValue({
        data: {
          code: 0,
          data: { questionId: 10, answered: true, correct: true, userAnswer: 'B' },
          message: 'success',
        },
      })

      const result = await submitVariantAnswer(10, 'B')

      expect(mockAiPost).toHaveBeenCalledWith('/ai/variant-training/10/answer', { userAnswer: 'B' })
      expect(result.data.correct).toBe(true)
    })
  })

  describe('streamQuestionAi', () => {
    it('应通过 fetch 发送 POST SSE 请求并解析内容事件', async () => {
      const encoder = new TextEncoder()
      const stream = new ReadableStream({
        start(controller) {
          controller.enqueue(encoder.encode('event: content\ndata: {"content":"你好"}\n\n'))
          controller.enqueue(encoder.encode('event: done\ndata: {"source":"ai"}\n\n'))
          controller.close()
        },
      })

      vi.stubGlobal(
        'fetch',
        vi.fn().mockResolvedValue({
          ok: true,
          body: stream,
        }),
      )

      const onContent = vi.fn()
      const onDone = vi.fn()
      await streamQuestionAi('explanation', 5, { onContent, onDone })

      expect(fetch).toHaveBeenCalledWith(
        '/api/ai/explanation/stream',
        expect.objectContaining({
          method: 'POST',
          headers: expect.objectContaining({
            'Content-Type': 'application/json',
            Authorization: 'Bearer mock-jwt-token',
          }),
          body: JSON.stringify({ questionId: 5 }),
        }),
      )
      expect(onContent).toHaveBeenCalledWith('你好')
      expect(onDone).toHaveBeenCalledWith('ai')
    })

    it('应在响应失败时抛出错误', async () => {
      vi.stubGlobal(
        'fetch',
        vi.fn().mockResolvedValue({
          ok: false,
          status: 500,
          json: () => Promise.resolve({ message: '服务器错误' }),
        }),
      )

      const onContent = vi.fn()

      await expect(streamQuestionAi('variant', 1, { onContent })).rejects.toThrow('服务器错误')
    })

    it('应在 401 时抛出登录过期错误', async () => {
      vi.stubGlobal(
        'fetch',
        vi.fn().mockResolvedValue({
          ok: false,
          status: 401,
          json: () => Promise.reject(new Error()),
        }),
      )

      await expect(streamQuestionAi('explanation', 1, { onContent: vi.fn() })).rejects.toThrow('登录已过期')
    })

    it('应在 body 为空时抛出不支持错误', async () => {
      vi.stubGlobal(
        'fetch',
        vi.fn().mockResolvedValue({
          ok: true,
          body: null,
        }),
      )

      await expect(streamQuestionAi('explanation', 1, { onContent: vi.fn() })).rejects.toThrow('浏览器不支持流式响应')
    })
  })

  describe('streamExamLearningAi', () => {
    it('binds the SSE request to the learning session and question route', async () => {
      const encoder = new TextEncoder()
      const stream = new ReadableStream({
        start(controller) {
          controller.enqueue(encoder.encode('event: done\ndata: {"source":"ai"}\n\n'))
          controller.close()
        },
      })
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, body: stream }))

      await streamExamLearningAi('explanation', 30, 10, { onContent: vi.fn() })

      expect(fetch).toHaveBeenCalledWith(
        '/api/exam/learning-sessions/30/questions/10/ai/explanation/stream',
        expect.objectContaining({ method: 'POST', body: JSON.stringify({}) }),
      )
    })
  })
})
