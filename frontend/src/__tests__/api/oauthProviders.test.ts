import { afterEach, describe, expect, it, vi } from 'vitest'
import axios, { AxiosError, type AxiosAdapter } from 'axios'
import request from '@/utils/request'
import { getOAuthProviders } from '@/api/auth'
import { redirectToLogin } from '@/utils/authNavigation'
import { ElMessage } from 'element-plus'

vi.mock('@/utils/authNavigation', () => ({ redirectToLogin: vi.fn() }))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() } }))

const defaultAdapter = axios.defaults.adapter
const requestAdapter = request.defaults.adapter

afterEach(() => {
  axios.defaults.adapter = defaultAdapter
  request.defaults.adapter = requestAdapter
  vi.clearAllMocks()
})

function useAdapter(adapter: AxiosAdapter) {
  axios.defaults.adapter = adapter
  request.defaults.adapter = adapter
}

describe('optional OAuth provider discovery', () => {
  it('rejects a 401 without redirecting the public page or displaying a session error', async () => {
    useAdapter(async (config) => {
      throw new AxiosError('Unavailable', 'ERR_BAD_RESPONSE', config, undefined, {
        status: 401,
        statusText: 'Unauthorized',
        headers: {},
        config,
        data: {},
      })
    })
    await expect(getOAuthProviders()).rejects.toThrow()
    expect(redirectToLogin).not.toHaveBeenCalled()
    expect(ElMessage.error).not.toHaveBeenCalled()
  })

  it('rejects an application authentication error without leaving the public page', async () => {
    useAdapter(async (config) => ({
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
      data: { code: 1002, message: 'Unavailable', data: null },
    }))
    await expect(getOAuthProviders()).rejects.toThrow('Unavailable')
    expect(redirectToLogin).not.toHaveBeenCalled()
    expect(ElMessage.error).not.toHaveBeenCalled()
  })

  it('returns provider availability on success', async () => {
    const data = { code: 0, message: 'success', data: { google: true } }
    useAdapter(async (config) => ({ status: 200, statusText: 'OK', headers: {}, config, data }))
    await expect(getOAuthProviders()).resolves.toEqual(data)
  })
})
