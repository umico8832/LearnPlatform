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
  getQuestionPage,
  getQuestionById,
  getAdminQuestionPage,
  getAdminQuestionById,
  createQuestion,
  updateQuestion,
  deleteQuestion,
  exportQuestions,
  downloadTemplate,
  importQuestions,
  getReviewSuggestion,
} from '@/api/question'

const mockedRequest = vi.mocked(request)

describe('Question API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getQuestionPage', () => {
    it('应使用 GET 请求获取题目分页（用户端）', async () => {
      const mockPageData = {
        records: [
          {
            id: 1,
            content: '题目内容',
            questionType: 'SINGLE_CHOICE',
            courseId: 1,
            courseName: '课程1',
            difficulty: 3,
            analysis: '解析',
            tags: null,
            score: 5,
            status: 1,
            createTime: '2024-01-01',
            updateTime: '2024-01-01',
            options: [],
            knowledgePointIds: [],
            knowledgePointNames: [],
          },
        ],
        total: 1,
        size: 20,
        current: 1,
        pages: 1,
      }
      mockedRequest.get.mockResolvedValue({ code: 0, data: mockPageData, message: 'success' })

      const result = await getQuestionPage({ pageNum: 1, pageSize: 20 })

      expect(mockedRequest.get).toHaveBeenCalledWith('/questions', {
        params: { pageNum: 1, pageSize: 20 },
      })
      expect(result).toEqual({ code: 0, data: mockPageData, message: 'success' })
    })

    it('应支持按题型、课程、难度筛选', async () => {
      mockedRequest.get.mockResolvedValue({ code: 0, data: { records: [], total: 0 }, message: 'success' })

      await getQuestionPage({
        pageNum: 1,
        pageSize: 10,
        questionType: 'MULTI_CHOICE',
        courseId: 2,
        difficulty: 4,
      })

      expect(mockedRequest.get).toHaveBeenCalledWith('/questions', {
        params: { pageNum: 1, pageSize: 10, questionType: 'MULTI_CHOICE', courseId: 2, difficulty: 4 },
      })
    })
  })

  describe('getQuestionById', () => {
    it('应使用 GET 请求获取题目详情', async () => {
      const mockQuestion = {
        id: 5,
        content: '测试题目',
        questionType: 'SINGLE_CHOICE',
        courseId: 1,
        courseName: '课程1',
        difficulty: 3,
        analysis: null,
        tags: null,
        score: 5,
        status: 1,
        createTime: '2024-01-01',
        updateTime: '2024-01-01',
        options: [
          { id: 1, content: '选项A', optionLabel: 'A', isCorrect: 1, sortOrder: 1 },
        ],
        knowledgePointIds: [1, 2],
        knowledgePointNames: ['知识点1', '知识点2'],
      }
      mockedRequest.get.mockResolvedValue({ code: 0, data: mockQuestion, message: 'success' })

      const result = await getQuestionById(5)

      expect(mockedRequest.get).toHaveBeenCalledWith('/questions/5')
      expect(result).toEqual({ code: 0, data: mockQuestion, message: 'success' })
    })
  })

  describe('getAdminQuestionPage', () => {
    it('应使用 GET 请求获取管理端题目分页', async () => {
      mockedRequest.get.mockResolvedValue({ code: 0, data: { records: [], total: 0 }, message: 'success' })

      await getAdminQuestionPage({ pageNum: 1, pageSize: 20 })

      expect(mockedRequest.get).toHaveBeenCalledWith('/admin/questions', {
        params: { pageNum: 1, pageSize: 20 },
      })
    })

    it('应支持按关键词、状态筛选', async () => {
      mockedRequest.get.mockResolvedValue({ code: 0, data: { records: [], total: 0 }, message: 'success' })

      await getAdminQuestionPage({
        keyword: 'Java',
        questionType: 'FILL_BLANK',
        courseId: 1,
        difficulty: 2,
        status: 1,
      })

      expect(mockedRequest.get).toHaveBeenCalledWith('/admin/questions', {
        params: { keyword: 'Java', questionType: 'FILL_BLANK', courseId: 1, difficulty: 2, status: 1 },
      })
    })
  })

  describe('getAdminQuestionById', () => {
    it('应使用 GET 请求获取管理端题目详情', async () => {
      mockedRequest.get.mockResolvedValue({ code: 0, data: { id: 10 }, message: 'success' })

      await getAdminQuestionById(10)

      expect(mockedRequest.get).toHaveBeenCalledWith('/admin/questions/10')
    })
  })

  describe('createQuestion', () => {
    it('应使用 POST 请求创建题目', async () => {
      const mockForm = {
        content: '新题目',
        questionType: 'SINGLE_CHOICE',
        courseId: 1,
        difficulty: 3,
        options: [
          { content: '选项A', optionLabel: 'A', isCorrect: 1, sortOrder: 1 },
          { content: '选项B', optionLabel: 'B', isCorrect: 0, sortOrder: 2 },
        ],
        knowledgePointIds: [1],
      }
      mockedRequest.post.mockResolvedValue({ code: 0, data: { id: 20 }, message: 'success' })

      const result = await createQuestion(mockForm)

      expect(mockedRequest.post).toHaveBeenCalledWith('/admin/questions', mockForm)
      expect(result).toEqual({ code: 0, data: { id: 20 }, message: 'success' })
    })
  })

  describe('updateQuestion', () => {
    it('应使用 PUT 请求更新题目', async () => {
      const mockForm = {
        content: '更新后的题目',
        questionType: 'SINGLE_CHOICE',
        courseId: 1,
      }
      mockedRequest.put.mockResolvedValue({ code: 0, data: { id: 5 }, message: 'success' })

      await updateQuestion(5, mockForm)

      expect(mockedRequest.put).toHaveBeenCalledWith('/admin/questions/5', mockForm)
    })
  })

  describe('deleteQuestion', () => {
    it('应使用 DELETE 请求删除题目', async () => {
      mockedRequest.delete.mockResolvedValue({ code: 0, data: null, message: 'success' })

      await deleteQuestion(8)

      expect(mockedRequest.delete).toHaveBeenCalledWith('/admin/questions/8')
    })
  })

  describe('getReviewSuggestion', () => {
    it('应使用 GET 请求获取 AI 复审建议', async () => {
      mockedRequest.get.mockResolvedValue({
        code: 0,
        data: { recommendation: 'APPROVE', confidenceScore: 90, summary: '题目可继续使用' },
        message: 'success',
      })

      await getReviewSuggestion(12)

      expect(mockedRequest.get).toHaveBeenCalledWith('/admin/questions/12/review-suggestion')
    })
  })

  describe('exportQuestions', () => {
    it('应使用 GET 请求导出题目（blob 响应）', async () => {
      const mockBlob = new Blob(['data'], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
      mockedRequest.get.mockResolvedValue(mockBlob)

      await exportQuestions({ questionType: 'SINGLE_CHOICE', courseId: 1 })

      expect(mockedRequest.get).toHaveBeenCalledWith('/admin/questions/export', {
        params: { questionType: 'SINGLE_CHOICE', courseId: 1 },
        responseType: 'blob',
      })
    })

    it('无参数时应导出所有题目', async () => {
      mockedRequest.get.mockResolvedValue(new Blob())

      await exportQuestions()

      expect(mockedRequest.get).toHaveBeenCalledWith('/admin/questions/export', {
        params: undefined,
        responseType: 'blob',
      })
    })
  })

  describe('downloadTemplate', () => {
    it('应使用 GET 请求下载导入模板', async () => {
      mockedRequest.get.mockResolvedValue(new Blob())

      await downloadTemplate()

      expect(mockedRequest.get).toHaveBeenCalledWith('/admin/questions/template', {
        responseType: 'blob',
      })
    })
  })

  describe('importQuestions', () => {
    it('应使用 POST 请求导入题目（FormData）', async () => {
      const mockResult = {
        totalRows: 10,
        successCount: 8,
        failCount: 2,
        errors: ['第3行: 题型不合法', '第7行: 缺少课程ID'],
      }
      mockedRequest.post.mockResolvedValue({ code: 0, data: mockResult, message: 'success' })

      const file = new File(['content'], 'questions.xlsx', {
        type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      })
      const result = await importQuestions(file)

      expect(mockedRequest.post).toHaveBeenCalledWith(
        '/admin/questions/import',
        expect.any(FormData),
        { headers: { 'Content-Type': 'multipart/form-data' } },
      )
      expect(result).toEqual({ code: 0, data: mockResult, message: 'success' })
    })
  })
})
