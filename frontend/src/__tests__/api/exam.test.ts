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
  getExamPaperList,
  getExamPaperDetail,
  createExamPaper,
  updateExamPaper,
  deleteExamPaper,
  publishExamPaper,
  getPublishedPapers,
  getPaperDetail,
  startExam,
  startExamLearningSession,
  getExamLearningSession,
  submitExamLearningAnswer,
  completeExamLearningSession,
  submitExam,
  getExamResult,
  getMyExamRecords,
} from '@/api/exam'

const mockedRequest = vi.mocked(request)

describe('Exam API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('管理端 API', () => {
    describe('getExamPaperList', () => {
      it('应使用 GET 请求获取试卷列表', async () => {
        const mockPageData = { records: [], total: 0, pageNum: 1, pageSize: 20 }
        mockedRequest.get.mockResolvedValue({ code: 0, data: mockPageData, message: 'success' })

        await getExamPaperList({ pageNum: 1, pageSize: 20 })

        expect(mockedRequest.get).toHaveBeenCalledWith('/admin/exam-papers', {
          params: { pageNum: 1, pageSize: 20 },
        })
      })

      it('应支持按课程和状态筛选', async () => {
        mockedRequest.get.mockResolvedValue({ code: 0, data: { records: [], total: 0 }, message: 'success' })

        await getExamPaperList({ pageNum: 1, pageSize: 10, courseId: 2, status: 1 })

        expect(mockedRequest.get).toHaveBeenCalledWith('/admin/exam-papers', {
          params: { pageNum: 1, pageSize: 10, courseId: 2, status: 1 },
        })
      })
    })

    describe('getExamPaperDetail', () => {
      it('应使用 GET 请求获取试卷详情', async () => {
        const mockPaper = { id: 1, title: '期中考试', totalScore: 100, duration: 60 }
        mockedRequest.get.mockResolvedValue({ code: 0, data: mockPaper, message: 'success' })

        const result = await getExamPaperDetail(1)

        expect(mockedRequest.get).toHaveBeenCalledWith('/admin/exam-papers/1')
        expect(result).toEqual({ code: 0, data: mockPaper, message: 'success' })
      })
    })

    describe('createExamPaper', () => {
      it('应使用 POST 请求创建试卷', async () => {
        const createData = { title: '单元测试', description: '测试描述', duration: 90 }
        mockedRequest.post.mockResolvedValue({ code: 0, data: { id: 5, ...createData }, message: 'success' })

        await createExamPaper(createData)

        expect(mockedRequest.post).toHaveBeenCalledWith('/admin/exam-papers', createData)
      })
    })

    describe('updateExamPaper', () => {
      it('应使用 PUT 请求更新试卷', async () => {
        const updateData = { title: '更新后的试卷' }
        mockedRequest.put.mockResolvedValue({ code: 0, data: {}, message: 'success' })

        await updateExamPaper(1, updateData)

        expect(mockedRequest.put).toHaveBeenCalledWith('/admin/exam-papers/1', updateData)
      })
    })

    describe('deleteExamPaper', () => {
      it('应使用 DELETE 请求删除试卷', async () => {
        mockedRequest.delete.mockResolvedValue({ code: 0, data: null, message: 'success' })

        await deleteExamPaper(1)

        expect(mockedRequest.delete).toHaveBeenCalledWith('/admin/exam-papers/1')
      })
    })

    describe('publishExamPaper', () => {
      it('应使用 POST 请求发布试卷', async () => {
        mockedRequest.post.mockResolvedValue({ code: 0, data: null, message: 'success' })

        await publishExamPaper(1)

        expect(mockedRequest.post).toHaveBeenCalledWith('/admin/exam-papers/1/publish')
      })
    })
  })

  describe('用户端 API', () => {
    describe('getPublishedPapers', () => {
      it('应使用 GET 请求获取已发布试卷', async () => {
        mockedRequest.get.mockResolvedValue({ code: 0, data: { records: [], total: 0 }, message: 'success' })

        await getPublishedPapers({ pageNum: 1, pageSize: 10 })

        expect(mockedRequest.get).toHaveBeenCalledWith('/exam/papers', {
          params: { pageNum: 1, pageSize: 10 },
        })
      })

      it('无参数时应使用默认值', async () => {
        mockedRequest.get.mockResolvedValue({ code: 0, data: { records: [], total: 0 }, message: 'success' })

        await getPublishedPapers()

        expect(mockedRequest.get).toHaveBeenCalledWith('/exam/papers', { params: undefined })
      })
    })

    describe('getPaperDetail', () => {
      it('应使用 GET 请求获取试卷详情', async () => {
        mockedRequest.get.mockResolvedValue({ code: 0, data: { id: 1, title: '考试' }, message: 'success' })

        await getPaperDetail(1)

        expect(mockedRequest.get).toHaveBeenCalledWith('/exam/papers/1')
      })
    })

    describe('startExam', () => {
      it('应使用 POST 请求开始考试', async () => {
        const mockRecord = { id: 10, examPaperId: 1, status: 0, answers: [] }
        mockedRequest.post.mockResolvedValue({ code: 0, data: mockRecord, message: 'success' })

        const result = await startExam(1)

        expect(mockedRequest.post).toHaveBeenCalledWith('/exam/start/1')
        expect(result).toEqual({ code: 0, data: mockRecord, message: 'success' })
      })
    })

    describe('试卷学习模式', () => {
      it('应创建、读取、逐题提交并完成学习会话', async () => {
        mockedRequest.post.mockResolvedValue({ code: 0, data: { id: 30 }, message: 'success' })
        mockedRequest.get.mockResolvedValue({ code: 0, data: { id: 30 }, message: 'success' })

        await startExamLearningSession(2)
        await getExamLearningSession(30)
        await submitExamLearningAnswer(30, { questionId: 10, userAnswer: 'A', answerTime: 12 })
        await completeExamLearningSession(30)

        expect(mockedRequest.post).toHaveBeenNthCalledWith(1, '/exam/papers/2/learning-sessions')
        expect(mockedRequest.get).toHaveBeenCalledWith('/exam/learning-sessions/30')
        expect(mockedRequest.post).toHaveBeenNthCalledWith(2, '/exam/learning-sessions/30/answers', {
          questionId: 10,
          userAnswer: 'A',
          answerTime: 12,
        })
        expect(mockedRequest.post).toHaveBeenNthCalledWith(3, '/exam/learning-sessions/30/complete')
      })
    })

    describe('submitExam', () => {
      it('应使用 POST 请求提交考试', async () => {
        const submitData = {
          examRecordId: 10,
          answers: [
            { questionId: 1, userAnswer: 'A' },
            { questionId: 2, userAnswer: 'B,C' },
          ],
        }
        const mockResult = { id: 10, score: 80, totalScore: 100, status: 1 }
        mockedRequest.post.mockResolvedValue({ code: 0, data: mockResult, message: 'success' })

        const result = await submitExam(submitData)

        expect(mockedRequest.post).toHaveBeenCalledWith('/exam/submit', submitData)
        expect(result).toEqual({ code: 0, data: mockResult, message: 'success' })
      })
    })

    describe('getExamResult', () => {
      it('应使用 GET 请求获取考试结果', async () => {
        const mockResult = { id: 10, score: 85, totalScore: 100, answers: [] }
        mockedRequest.get.mockResolvedValue({ code: 0, data: mockResult, message: 'success' })

        const result = await getExamResult(10)

        expect(mockedRequest.get).toHaveBeenCalledWith('/exam/result/10')
        expect(result).toEqual({ code: 0, data: mockResult, message: 'success' })
      })
    })

    describe('getMyExamRecords', () => {
      it('应使用 GET 请求获取考试记录', async () => {
        mockedRequest.get.mockResolvedValue({ code: 0, data: { records: [], total: 0 }, message: 'success' })

        await getMyExamRecords({ pageNum: 1, pageSize: 20 })

        expect(mockedRequest.get).toHaveBeenCalledWith('/exam/records', {
          params: { pageNum: 1, pageSize: 20 },
        })
      })
    })
  })
})
