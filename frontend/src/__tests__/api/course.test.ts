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
  getAllCourses,
  getCoursePage,
  getCourseById,
  getMyCourses,
  addCourseToLibrary,
  getCourseOverview,
  startTutorSession,
  submitTutorCheck,
  isArrayQueueRepresentationCourseware,
  createCourse,
  updateCourse,
  deleteCourse,
} from '@/api/course'

const mockedRequest = vi.mocked(request)

describe('Course API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('只接受受限的 ArrayQueue 循环数组课件配置', () => {
    expect(isArrayQueueRepresentationCourseware({
      kind: 'ARRAY_QUEUE_REPRESENTATION', version: 1, capacity: 8, headIndex: 6, elements: ['A', 'B'],
    })).toBe(true)
    expect(isArrayQueueRepresentationCourseware({
      kind: 'ARRAY_QUEUE_REPRESENTATION', version: 1, capacity: 8, headIndex: 8, elements: ['A'], script: 'alert(1)',
    })).toBe(false)
  })

  describe('getAllCourses', () => {
    it('应使用 GET 请求获取全部课程', async () => {
      const mockCourses = [
        { id: 1, name: 'Java基础', description: 'Java入门课程', status: 1 },
        { id: 2, name: '数据库', description: 'MySQL入门', status: 1 },
      ]
      mockedRequest.get.mockResolvedValue({ code: 0, data: mockCourses, message: 'success' })

      const result = await getAllCourses()

      expect(mockedRequest.get).toHaveBeenCalledWith('/courses/list')
      expect(result).toEqual({ code: 0, data: mockCourses, message: 'success' })
    })

    it('无课程时返回空数组', async () => {
      mockedRequest.get.mockResolvedValue({ code: 0, data: [], message: 'success' })

      const result = await getAllCourses()

      expect(result).toEqual({ code: 0, data: [], message: 'success' })
    })
  })

  describe('getCoursePage', () => {
    it('应使用 GET 请求获取课程分页', async () => {
      const mockPageData = { records: [], total: 0, size: 10, current: 1, pages: 0 }
      mockedRequest.get.mockResolvedValue({ code: 0, data: mockPageData, message: 'success' })

      await getCoursePage({ pageNum: 1, pageSize: 10 })

      expect(mockedRequest.get).toHaveBeenCalledWith('/courses', { params: { pageNum: 1, pageSize: 10 } })
    })

    it('应支持按关键词搜索', async () => {
      mockedRequest.get.mockResolvedValue({ code: 0, data: { records: [], total: 0 }, message: 'success' })

      await getCoursePage({ pageNum: 1, pageSize: 10, keyword: 'Java' })

      expect(mockedRequest.get).toHaveBeenCalledWith('/courses', {
        params: { pageNum: 1, pageSize: 10, keyword: 'Java' },
      })
    })
  })

  describe('getCourseById', () => {
    it('应使用 GET 请求获取课程详情', async () => {
      const mockCourse = { id: 1, name: 'Java基础', description: '描述', status: 1 }
      mockedRequest.get.mockResolvedValue({ code: 0, data: mockCourse, message: 'success' })

      const result = await getCourseById(1)

      expect(mockedRequest.get).toHaveBeenCalledWith('/courses/1')
      expect(result).toEqual({ code: 0, data: mockCourse, message: 'success' })
    })
  })

  describe('个人课程库', () => {
    it('应使用 GET 请求获取当前用户的课程库', async () => {
      mockedRequest.get.mockResolvedValue({ code: 0, data: [], message: 'success' })

      await getMyCourses()

      expect(mockedRequest.get).toHaveBeenCalledWith('/my-courses')
    })

    it('应使用 POST 请求将课程加入当前用户的课程库', async () => {
      mockedRequest.post.mockResolvedValue({ code: 0, data: { courseId: 408 }, message: 'success' })

      await addCourseToLibrary(408)

      expect(mockedRequest.post).toHaveBeenCalledWith('/my-courses/408')
    })

    it('应使用 GET 请求获取已加入课程的学习总览', async () => {
      mockedRequest.get.mockResolvedValue({ code: 0, data: { courseId: 408 }, message: 'success' })

      await getCourseOverview(408)

      expect(mockedRequest.get).toHaveBeenCalledWith('/my-courses/408/overview')
    })

    it('应以课程和知识点创建 Tutor 会话并提交服务端检查', async () => {
      mockedRequest.post.mockResolvedValue({ code: 0, data: {}, message: 'success' })
      await startTutorSession(408, 31)
      await submitTutorCheck(408, 'session-key', 'RIGHT_TO_LEFT')
      expect(mockedRequest.post).toHaveBeenNthCalledWith(1, '/my-courses/408/tutor-sessions', undefined, { params: { knowledgePointId: 31 } })
      expect(mockedRequest.post).toHaveBeenNthCalledWith(2, '/my-courses/408/tutor-sessions/session-key/check', { optionId: 'RIGHT_TO_LEFT' })
    })
  })

  describe('createCourse', () => {
    it('应使用 POST 请求创建课程', async () => {
      const courseData = { name: '新课程', description: '描述' }
      mockedRequest.post.mockResolvedValue({ code: 0, data: { id: 3, ...courseData }, message: 'success' })

      await createCourse(courseData)

      expect(mockedRequest.post).toHaveBeenCalledWith('/admin/courses', courseData)
    })
  })

  describe('updateCourse', () => {
    it('应使用 PUT 请求更新课程', async () => {
      const updateData = { name: '更新后的课程' }
      mockedRequest.put.mockResolvedValue({ code: 0, data: {}, message: 'success' })

      await updateCourse(1, updateData)

      expect(mockedRequest.put).toHaveBeenCalledWith('/admin/courses/1', updateData)
    })
  })

  describe('deleteCourse', () => {
    it('应使用 DELETE 请求删除课程', async () => {
      mockedRequest.delete.mockResolvedValue({ code: 0, data: null, message: 'success' })

      await deleteCourse(1)

      expect(mockedRequest.delete).toHaveBeenCalledWith('/admin/courses/1')
    })

    it('删除不存在的课程应传递错误', async () => {
      mockedRequest.delete.mockRejectedValue(new Error('课程不存在'))

      await expect(deleteCourse(999)).rejects.toThrow('课程不存在')
    })
  })
})
