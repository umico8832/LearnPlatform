import { beforeEach, describe, expect, it, vi } from 'vitest'
import request from '@/utils/request'
import { getMySubmissions, submitQuestion } from '@/api/submission'
import { getAllCourses } from '@/api/course'
import { getFavorites, removeFavorite } from '@/api/favorite'
vi.mock('@/utils/request', () => ({ default: { get: vi.fn(), post: vi.fn(), delete: vi.fn() } }))
beforeEach(() => vi.clearAllMocks())
describe('个人题目列表的原位错误配置', () => {
  it('投稿仍写入本人端点并可选择由表单显示失败', () => {
    const payload = {
      courseId: 1,
      content: '原创题目',
      questionType: 'TRUE_FALSE',
      difficulty: 2,
      correctAnswer: 'TRUE',
    }
    submitQuestion(payload, { errorDisplay: 'inline' })
    expect(request.post).toHaveBeenCalledWith('/submission', payload, { errorDisplay: 'inline' })
    getMySubmissions({ pageNum: 2, pageSize: 10, status: 0 }, { errorDisplay: 'inline' })
    expect(request.get).toHaveBeenCalledWith('/submission/my', {
      params: { pageNum: 2, pageSize: 10, status: 0 },
      errorDisplay: 'inline',
    })
  })
  it('收藏加载和移除不丢失原位错误选项', () => {
    getFavorites({ pageNum: 1 }, { errorDisplay: 'inline' })
    expect(request.get).toHaveBeenCalledWith('/favorites', { params: { pageNum: 1 }, errorDisplay: 'inline' })
    removeFavorite(7, { errorDisplay: 'inline' })
    expect(request.delete).toHaveBeenCalledWith('/favorites/7', { errorDisplay: 'inline' })
  })
  it('可用课程为表单请求原位错误，不改变默认读取', () => {
    getAllCourses({ errorDisplay: 'inline' })
    expect(request.get).toHaveBeenLastCalledWith('/courses/list', { errorDisplay: 'inline' })
    getAllCourses()
    expect(request.get).toHaveBeenLastCalledWith('/courses/list')
  })
})
