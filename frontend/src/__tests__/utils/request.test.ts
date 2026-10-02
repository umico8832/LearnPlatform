import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AxiosError, type AxiosAdapter } from 'axios'

const mocks = vi.hoisted(() => ({ error: vi.fn(), redirect: vi.fn() }))
vi.mock('element-plus', () => ({ ElMessage: { error: mocks.error } }))
vi.mock('@/utils/authNavigation', () => ({ redirectToLogin: mocks.redirect }))

import request, { aiService } from '@/utils/request'
import { getToken, setToken } from '@/utils/auth'

const networkFailure: AxiosAdapter = async (config) => {
  throw new AxiosError('Network Error', 'ERR_NETWORK', config)
}
const responseWithCode =
  (code: number): AxiosAdapter =>
  async (config) => ({
    data: { code, message: '测试错误', data: null },
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  })

beforeEach(() => vi.clearAllMocks())

describe.each([
  ['学习请求', request],
  ['AI 请求', aiService],
] as const)('%s 的原位错误反馈', (_name, client) => {
  it('原位展示的网络和业务错误仍拒绝请求，不重复弹消息', async () => {
    await expect(client.get('/test', { errorDisplay: 'inline', adapter: networkFailure })).rejects.toThrow(
      'Network Error',
    )
    await expect(client.get('/test', { errorDisplay: 'inline', adapter: responseWithCode(1001) })).rejects.toThrow(
      '测试错误',
    )
    expect(mocks.error).not.toHaveBeenCalled()
  })

  it('未选择原位处理的请求保持默认消息', async () => {
    await expect(client.get('/test', { adapter: networkFailure })).rejects.toThrow()
    expect(mocks.error).toHaveBeenCalledWith('网络异常，请检查网络连接')
  })

  it('原位请求遇到登录失效仍清理登录、跳转并明确通知', async () => {
    setToken('test-session-placeholder')
    await expect(client.get('/test', { errorDisplay: 'inline', adapter: responseWithCode(1002) })).rejects.toThrow()
    expect(getToken()).toBeNull()
    expect(mocks.redirect).toHaveBeenCalledOnce()
    expect(mocks.error).toHaveBeenCalledWith('登录已过期，请重新登录')
  })

  it('HTTP 401 仍触发认证失效处理', async () => {
    const unauthorized: AxiosAdapter = async (config) => {
      throw new AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, undefined, {
        config,
        data: {},
        status: 401,
        statusText: 'Unauthorized',
        headers: {},
      })
    }
    setToken('test-session-placeholder')
    await expect(client.get('/test', { errorDisplay: 'inline', adapter: unauthorized })).rejects.toThrow()
    expect(getToken()).toBeNull()
    expect(mocks.redirect).toHaveBeenCalledOnce()
    expect(mocks.error).toHaveBeenCalledWith('登录已过期，请重新登录')
  })
})

describe('login credentials with inline feedback', () => {
  it('does not clear an existing token for an inline login HTTP 401', async () => {
    const unauthorized: AxiosAdapter = async (config) => {
      throw new AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, undefined, {
        config,
        data: {},
        status: 401,
        statusText: 'Unauthorized',
        headers: {},
      })
    }
    setToken('test-session-placeholder')
    await expect(request.post('/auth/login', {}, { errorDisplay: 'inline', adapter: unauthorized })).rejects.toThrow()
    expect(getToken()).toBe('test-session-placeholder')
    expect(mocks.redirect).not.toHaveBeenCalled()
  })

  it('does not treat rejected credentials as an expired protected session', async () => {
    setToken('test-session-placeholder')
    await expect(
      request.post('/auth/login', {}, { errorDisplay: 'inline', adapter: responseWithCode(1002) }),
    ).rejects.toThrow('测试错误')
    expect(getToken()).toBe('test-session-placeholder')
    expect(mocks.redirect).not.toHaveBeenCalled()
    expect(mocks.error).not.toHaveBeenCalled()
  })
})
