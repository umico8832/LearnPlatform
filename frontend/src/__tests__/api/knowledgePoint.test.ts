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
  getKnowledgeTree,
  createKnowledgePoint,
  updateKnowledgePoint,
  deleteKnowledgePoint,
} from '@/api/knowledgePoint'

const mockedRequest = vi.mocked(request)

describe('KnowledgePoint API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getKnowledgeTree', () => {
    it('应使用 GET 请求获取知识点树', async () => {
      const mockTree = [
        {
          id: 1, name: 'Java基础', courseId: 1, parentId: 0, sortOrder: 1,
          children: [
            { id: 2, name: '变量与类型', courseId: 1, parentId: 1, sortOrder: 1 },
            { id: 3, name: '控制流', courseId: 1, parentId: 1, sortOrder: 2 },
          ],
        },
      ]
      mockedRequest.get.mockResolvedValue({ code: 0, data: mockTree, message: 'success' })

      const result = await getKnowledgeTree(1)

      expect(mockedRequest.get).toHaveBeenCalledWith('/knowledge-points/tree/1')
      expect(result).toEqual({ code: 0, data: mockTree, message: 'success' })
    })

    it('无知识点时返回空数组', async () => {
      mockedRequest.get.mockResolvedValue({ code: 0, data: [], message: 'success' })

      const result = await getKnowledgeTree(999)

      expect(mockedRequest.get).toHaveBeenCalledWith('/knowledge-points/tree/999')
      expect(result).toEqual({ code: 0, data: [], message: 'success' })
    })
  })

  describe('createKnowledgePoint', () => {
    it('应使用 POST 请求创建知识点', async () => {
      const kpData = { name: '新知识点', courseId: 1, parentId: 0 }
      mockedRequest.post.mockResolvedValue({ code: 0, data: { id: 10, ...kpData }, message: 'success' })

      await createKnowledgePoint(kpData)

      expect(mockedRequest.post).toHaveBeenCalledWith('/admin/knowledge-points', kpData)
    })

    it('创建子知识点应包含 parentId', async () => {
      const kpData = { name: '子知识点', courseId: 1, parentId: 1 }
      mockedRequest.post.mockResolvedValue({ code: 0, data: { id: 11, ...kpData }, message: 'success' })

      await createKnowledgePoint(kpData)

      expect(mockedRequest.post).toHaveBeenCalledWith('/admin/knowledge-points', kpData)
    })
  })

  describe('updateKnowledgePoint', () => {
    it('应使用 PUT 请求更新知识点', async () => {
      const updateData = { name: '更新后的知识点' }
      mockedRequest.put.mockResolvedValue({ code: 0, data: {}, message: 'success' })

      await updateKnowledgePoint(1, updateData)

      expect(mockedRequest.put).toHaveBeenCalledWith('/admin/knowledge-points/1', updateData)
    })
  })

  describe('deleteKnowledgePoint', () => {
    it('应使用 DELETE 请求删除知识点', async () => {
      mockedRequest.delete.mockResolvedValue({ code: 0, data: null, message: 'success' })

      await deleteKnowledgePoint(1)

      expect(mockedRequest.delete).toHaveBeenCalledWith('/admin/knowledge-points/1')
    })

    it('删除不存在的知识点应传递错误', async () => {
      mockedRequest.delete.mockRejectedValue(new Error('知识点不存在'))

      await expect(deleteKnowledgePoint(999)).rejects.toThrow('知识点不存在')
    })
  })
})