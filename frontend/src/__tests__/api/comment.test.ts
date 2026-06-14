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
  getComments,
  addComment,
  deleteComment,
  toggleLike,
  getCommentCount,
} from '@/api/comment'

const mockedRequest = vi.mocked(request)

describe('Comment API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getComments', () => {
    it('应使用 GET 请求获取题目评论列表', async () => {
      const mockComments = [
        {
          id: 1,
          questionId: 10,
          userId: 1,
          nickname: '用户A',
          avatar: null,
          content: '这道题的解法很好',
          parentId: 0,
          replyToUserId: null,
          replyToNickname: null,
          likeCount: 3,
          likedByMe: false,
          createTime: '2024-01-01 10:00:00',
          replies: [],
        },
      ]
      mockedRequest.get.mockResolvedValue({ code: 0, data: mockComments, message: 'success' })

      const result = await getComments(10)

      expect(mockedRequest.get).toHaveBeenCalledWith('/comments/question/10')
      expect(result).toEqual({ code: 0, data: mockComments, message: 'success' })
    })

    it('应正确传递题目 ID', async () => {
      mockedRequest.get.mockResolvedValue({ code: 0, data: [], message: 'success' })

      await getComments(999)

      expect(mockedRequest.get).toHaveBeenCalledWith('/comments/question/999')
    })
  })

  describe('addComment', () => {
    it('应使用 POST 请求发表评论', async () => {
      const mockComment = {
        id: 2,
        questionId: 10,
        userId: 1,
        nickname: '用户A',
        avatar: null,
        content: '测试评论',
        parentId: 0,
        replyToUserId: null,
        replyToNickname: null,
        likeCount: 0,
        likedByMe: false,
        createTime: '2024-01-01 11:00:00',
      }
      mockedRequest.post.mockResolvedValue({ code: 0, data: mockComment, message: 'success' })

      const result = await addComment({ questionId: 10, content: '测试评论' })

      expect(mockedRequest.post).toHaveBeenCalledWith('/comments', {
        questionId: 10,
        content: '测试评论',
      })
      expect(result).toEqual({ code: 0, data: mockComment, message: 'success' })
    })

    it('应支持回复评论', async () => {
      mockedRequest.post.mockResolvedValue({ code: 0, data: {}, message: 'success' })

      await addComment({
        questionId: 10,
        content: '回复内容',
        parentId: 1,
        replyToUserId: 2,
      })

      expect(mockedRequest.post).toHaveBeenCalledWith('/comments', {
        questionId: 10,
        content: '回复内容',
        parentId: 1,
        replyToUserId: 2,
      })
    })
  })

  describe('deleteComment', () => {
    it('应使用 DELETE 请求删除评论', async () => {
      mockedRequest.delete.mockResolvedValue({ code: 0, data: null, message: 'success' })

      await deleteComment(5)

      expect(mockedRequest.delete).toHaveBeenCalledWith('/comments/5')
    })
  })

  describe('toggleLike', () => {
    it('应使用 POST 请求点赞/取消点赞', async () => {
      mockedRequest.post.mockResolvedValue({ code: 0, data: true, message: 'success' })

      const result = await toggleLike(3)

      expect(mockedRequest.post).toHaveBeenCalledWith('/comments/3/like')
      expect(result).toEqual({ code: 0, data: true, message: 'success' })
    })
  })

  describe('getCommentCount', () => {
    it('应使用 GET 请求获取题目评论数', async () => {
      mockedRequest.get.mockResolvedValue({ code: 0, data: 15, message: 'success' })

      const result = await getCommentCount(10)

      expect(mockedRequest.get).toHaveBeenCalledWith('/comments/count/10')
      expect(result).toEqual({ code: 0, data: 15, message: 'success' })
    })
  })
})