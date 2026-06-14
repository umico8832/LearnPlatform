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
  getAdminUserList,
  createAdminUser,
  updateUserRole,
  updateUserStatus,
  resetUserPassword,
  deleteAdminUser,
  getAdminUserStats,
} from '@/api/adminUser'

const mockedRequest = vi.mocked(request)

describe('AdminUser API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getAdminUserList', () => {
    it('应使用 GET 请求获取用户分页列表', async () => {
      const mockData = {
        records: [
          { id: 1, username: 'user1', nickname: '用户1', avatar: null, role: 'USER', status: 1, createTime: '2024-01-01' },
        ],
        total: 1,
        current: 1,
        size: 20,
      }
      mockedRequest.get.mockResolvedValue({ code: 0, data: mockData, message: 'success' })

      const result = await getAdminUserList({ page: 1, size: 20 })

      expect(mockedRequest.get).toHaveBeenCalledWith('/admin/users', {
        params: { page: 1, size: 20 },
      })
      expect(result).toEqual({ code: 0, data: mockData, message: 'success' })
    })

    it('应支持按关键词、角色、状态筛选', async () => {
      mockedRequest.get.mockResolvedValue({ code: 0, data: { records: [], total: 0 }, message: 'success' })

      await getAdminUserList({ keyword: 'test', role: 'ADMIN', status: 1 })

      expect(mockedRequest.get).toHaveBeenCalledWith('/admin/users', {
        params: { keyword: 'test', role: 'ADMIN', status: 1 },
      })
    })
  })

  describe('createAdminUser', () => {
    it('应使用 POST 请求创建用户', async () => {
      const mockUser = { id: 10, username: 'newuser', nickname: '新用户', role: 'USER', status: 1 }
      mockedRequest.post.mockResolvedValue({ code: 0, data: mockUser, message: 'success' })

      const result = await createAdminUser({ username: 'newuser', password: 'pass123', nickname: '新用户' })

      expect(mockedRequest.post).toHaveBeenCalledWith('/admin/users', {
        username: 'newuser',
        password: 'pass123',
        nickname: '新用户',
      })
      expect(result).toEqual({ code: 0, data: mockUser, message: 'success' })
    })

    it('应支持指定角色', async () => {
      mockedRequest.post.mockResolvedValue({ code: 0, data: {}, message: 'success' })

      await createAdminUser({ username: 'admin2', password: 'pass123', role: 'ADMIN' })

      expect(mockedRequest.post).toHaveBeenCalledWith('/admin/users', {
        username: 'admin2',
        password: 'pass123',
        role: 'ADMIN',
      })
    })
  })

  describe('updateUserRole', () => {
    it('应使用 PUT 请求修改用户角色', async () => {
      mockedRequest.put.mockResolvedValue({ code: 0, data: null, message: 'success' })

      await updateUserRole(5, 'ADMIN')

      expect(mockedRequest.put).toHaveBeenCalledWith('/admin/users/5/role', { role: 'ADMIN' })
    })
  })

  describe('updateUserStatus', () => {
    it('应使用 PUT 请求启用用户', async () => {
      mockedRequest.put.mockResolvedValue({ code: 0, data: null, message: 'success' })

      await updateUserStatus(3, 1)

      expect(mockedRequest.put).toHaveBeenCalledWith('/admin/users/3/status', { status: 1 })
    })

    it('应支持禁用用户', async () => {
      mockedRequest.put.mockResolvedValue({ code: 0, data: null, message: 'success' })

      await updateUserStatus(3, 0)

      expect(mockedRequest.put).toHaveBeenCalledWith('/admin/users/3/status', { status: 0 })
    })
  })

  describe('resetUserPassword', () => {
    it('应使用 PUT 请求重置用户密码', async () => {
      mockedRequest.put.mockResolvedValue({ code: 0, data: null, message: 'success' })

      await resetUserPassword(7, 'newPassword123')

      expect(mockedRequest.put).toHaveBeenCalledWith('/admin/users/7/reset-password', {
        newPassword: 'newPassword123',
      })
    })
  })

  describe('deleteAdminUser', () => {
    it('应使用 DELETE 请求删除用户', async () => {
      mockedRequest.delete.mockResolvedValue({ code: 0, data: null, message: 'success' })

      await deleteAdminUser(4)

      expect(mockedRequest.delete).toHaveBeenCalledWith('/admin/users/4')
    })
  })

  describe('getAdminUserStats', () => {
    it('应使用 GET 请求获取用户统计', async () => {
      const mockStats = { total: 100, active: 85, disabled: 10, admins: 5 }
      mockedRequest.get.mockResolvedValue({ code: 0, data: mockStats, message: 'success' })

      const result = await getAdminUserStats()

      expect(mockedRequest.get).toHaveBeenCalledWith('/admin/users/stats')
      expect(result).toEqual({ code: 0, data: mockStats, message: 'success' })
    })
  })
})