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
import { updateProfile, updatePassword } from '@/api/user'

const mockedRequest = vi.mocked(request)

describe('User API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('updateProfile', () => {
    it('应使用 PUT 请求修改个人信息', async () => {
      const mockUserInfo = {
        id: 1,
        username: 'testuser',
        nickname: '新昵称',
        role: 'USER',
        status: 1,
      }
      mockedRequest.put.mockResolvedValue({ code: 0, data: mockUserInfo, message: 'success' })

      const result = await updateProfile({ nickname: '新昵称' })

      expect(mockedRequest.put).toHaveBeenCalledWith('/auth/profile', { nickname: '新昵称' })
      expect(result).toEqual({ code: 0, data: mockUserInfo, message: 'success' })
    })
  })

  describe('updatePassword', () => {
    it('应使用 PUT 请求修改密码', async () => {
      mockedRequest.put.mockResolvedValue({ code: 0, data: null, message: 'success' })

      const result = await updatePassword({ oldPassword: 'old123', newPassword: 'new456' })

      expect(mockedRequest.put).toHaveBeenCalledWith('/auth/password', {
        oldPassword: 'old123',
        newPassword: 'new456',
      })
      expect(result).toEqual({ code: 0, data: null, message: 'success' })
    })

    it('修改密码失败时应传递错误', async () => {
      mockedRequest.put.mockRejectedValue(new Error('原密码不正确'))

      await expect(updatePassword({ oldPassword: 'wrong', newPassword: 'new456' })).rejects.toThrow('原密码不正确')
    })
  })
})