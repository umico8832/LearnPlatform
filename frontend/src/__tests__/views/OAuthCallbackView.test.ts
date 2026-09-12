import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import OAuthCallbackView from '@/views/auth/OAuthCallbackView.vue'

const { mockExchange, mockReplace, mockSetLoginInfo } = vi.hoisted(() => ({
  mockExchange: vi.fn(),
  mockReplace: vi.fn(),
  mockSetLoginInfo: vi.fn(),
}))
const mockRoute: { hash: string; path: string; query: Record<string, string> } = {
  hash: '',
  path: '/oauth/callback',
  query: {},
}

vi.mock('@/api/auth', () => ({
  exchangeOAuthTicket: (...args: unknown[]) => mockExchange(...args),
}))
vi.mock('@/stores/user', () => ({
  useUserStore: () => ({ setLoginInfo: mockSetLoginInfo }),
}))
vi.mock('vue-router', () => ({
  useRoute: () => mockRoute,
  useRouter: () => ({ replace: mockReplace }),
}))

describe('OAuthCallbackView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    mockRoute.hash = ''
    mockRoute.query = {}
  })

  it('exchanges the one-time ticket and restores the guarded redirect', async () => {
    mockRoute.hash = '#ticket=one-time-ticket'
    sessionStorage.setItem('oauth_login_redirect', '/exams')
    mockExchange.mockResolvedValue({
      data: { token: 'jwt', user: { id: 1, username: 'learner', role: 'USER' } },
    })

    mount(OAuthCallbackView, {
      global: {
        stubs: {
          AuthLayout: { template: '<main><slot /></main>' },
          'el-icon': { template: '<span><slot /></span>' },
          'el-button': { template: '<button><slot /></button>' },
        },
      },
    })
    await flushPromises()

    expect(mockExchange).toHaveBeenCalledWith('one-time-ticket')
    expect(mockSetLoginInfo).toHaveBeenCalledWith('jwt', expect.objectContaining({ username: 'learner' }))
    expect(mockReplace).toHaveBeenCalledWith('/exams')
  })

  it('shows a specific message when an existing account cannot be linked silently', async () => {
    mockRoute.query = { error: 'account_exists' }
    const wrapper = mount(OAuthCallbackView, {
      global: {
        stubs: {
          AuthLayout: { template: '<main><slot /></main>' },
          'el-icon': { template: '<span><slot /></span>' },
          'el-button': { template: '<button><slot /></button>' },
        },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('该邮箱已有账号，请先使用原登录方式。')
    expect(mockExchange).not.toHaveBeenCalled()
  })
})
