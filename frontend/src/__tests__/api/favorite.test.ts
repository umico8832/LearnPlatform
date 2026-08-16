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
import { addFavorite, removeFavorite, checkFavorite, getFavorites, getFavoriteIds } from '@/api/favorite'

const mockedRequest = vi.mocked(request)

describe('Favorite API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('addFavorite', () => {
    it('应使用 POST 请求收藏题目', async () => {
      mockedRequest.post.mockResolvedValue({ code: 0, data: null, message: 'success' })

      const result = await addFavorite(42)

      expect(mockedRequest.post).toHaveBeenCalledWith('/favorites/42')
      expect(result).toEqual({ code: 0, data: null, message: 'success' })
    })

    it('不同题目 ID 应传入不同路径', async () => {
      mockedRequest.post.mockResolvedValue({ code: 0, data: null, message: 'success' })

      await addFavorite(1)
      expect(mockedRequest.post).toHaveBeenCalledWith('/favorites/1')

      await addFavorite(999)
      expect(mockedRequest.post).toHaveBeenCalledWith('/favorites/999')
    })
  })

  describe('removeFavorite', () => {
    it('应使用 DELETE 请求取消收藏', async () => {
      mockedRequest.delete.mockResolvedValue({ code: 0, data: null, message: 'success' })

      const result = await removeFavorite(42)

      expect(mockedRequest.delete).toHaveBeenCalledWith('/favorites/42')
      expect(result).toEqual({ code: 0, data: null, message: 'success' })
    })
  })

  describe('checkFavorite', () => {
    it('应使用 GET 请求检查收藏状态', async () => {
      mockedRequest.get.mockResolvedValue({ code: 0, data: { isFavorite: true }, message: 'success' })

      const result = await checkFavorite(42)

      expect(mockedRequest.get).toHaveBeenCalledWith('/favorites/42/status')
      expect(result).toEqual({ code: 0, data: { isFavorite: true }, message: 'success' })
    })

    it('未收藏时返回 false', async () => {
      mockedRequest.get.mockResolvedValue({ code: 0, data: { isFavorite: false }, message: 'success' })

      const result = await checkFavorite(1)

      expect(mockedRequest.get).toHaveBeenCalledWith('/favorites/1/status')
      expect(result).toEqual({ code: 0, data: { isFavorite: false }, message: 'success' })
    })
  })

  describe('getFavorites', () => {
    it('应使用 GET 请求获取收藏列表', async () => {
      const mockPageData = {
        records: [{ id: 1, questionId: 42, questionContent: '题目内容' }],
        total: 1,
        pageNum: 1,
        pageSize: 20,
      }
      mockedRequest.get.mockResolvedValue({ code: 0, data: mockPageData, message: 'success' })

      const result = await getFavorites({ pageNum: 1, pageSize: 20 })

      expect(mockedRequest.get).toHaveBeenCalledWith('/favorites', { params: { pageNum: 1, pageSize: 20 } })
      expect(result).toEqual({ code: 0, data: mockPageData, message: 'success' })
    })

    it('无参数时应使用默认值', async () => {
      mockedRequest.get.mockResolvedValue({ code: 0, data: { records: [], total: 0 }, message: 'success' })

      await getFavorites()

      expect(mockedRequest.get).toHaveBeenCalledWith('/favorites', { params: undefined })
    })
  })

  describe('getFavoriteIds', () => {
    it('应使用 GET 请求获取收藏 ID 列表', async () => {
      mockedRequest.get.mockResolvedValue({ code: 0, data: [1, 5, 42], message: 'success' })

      const result = await getFavoriteIds()

      expect(mockedRequest.get).toHaveBeenCalledWith('/favorites/ids')
      expect(result).toEqual({ code: 0, data: [1, 5, 42], message: 'success' })
    })

    it('无收藏时返回空数组', async () => {
      mockedRequest.get.mockResolvedValue({ code: 0, data: [], message: 'success' })

      const result = await getFavoriteIds()

      expect(result).toEqual({ code: 0, data: [], message: 'success' })
    })
  })
})
