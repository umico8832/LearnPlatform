import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import LoginView from '@/views/auth/LoginView.vue'

enableAutoUnmount(afterEach)

const { mockLogin, mockPush, mockSetLoginInfo, mockVerify } = vi.hoisted(() => ({
  mockLogin: vi.fn(),
  mockPush: vi.fn(),
  mockSetLoginInfo: vi.fn(),
  mockVerify: vi.fn(),
}))
const mockRoute: { query: Record<string, string> } = { query: {} }
vi.mock('@/api/auth', () => ({
  login: (...args: unknown[]) => mockLogin(...args),
  getOAuthProviders: () => Promise.resolve({ data: { google: false } }),
}))
vi.mock('@/stores/user', () => ({ useUserStore: () => ({ setLoginInfo: mockSetLoginInfo }) }))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
  useRoute: () => mockRoute,
  RouterLink: { template: '<a><slot /></a>' },
}))

const stubs = {
  TurnstileSheet: defineComponent({
    setup(_props, { expose }) {
      expose({ verify: mockVerify })
      return () => h('div')
    },
  }),
}
function mountLogin() {
  return mount(LoginView, { global: { stubs } })
}
async function fill(w: VueWrapper, account = 'learner', password = 'password123') {
  const inputs = w.findAll('input')
  await inputs[0].setValue(account)
  await inputs[1].setValue(password)
}

describe('LoginView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockVerify.mockResolvedValue('turnstile-ok')
    mockRoute.query = {}
    mockLogin.mockResolvedValue({ data: { token: 'jwt', user: { id: 1, username: 'learner', role: 'USER' } } })
  })
  it('renders mature account login fields and password recovery entry', () => {
    const w = mountLogin()
    expect(w.text()).toContain('欢迎回来')
    expect(w.html()).toContain('请输入用户名或邮箱')
    expect(w.html()).toContain('请输入密码')
    expect(w.text()).toContain('忘记密码？')
  })
  it('submits account, password and Turnstile token', async () => {
    const w = mountLogin()
    const inputs = w.findAll('input')
    await inputs[0].setValue('learner@example.com')
    await inputs[1].setValue('password123')
    await flushPromises()
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(mockLogin).toHaveBeenCalledWith(
      {
        account: 'learner@example.com',
        password: 'password123',
        turnstileToken: 'turnstile-ok',
      },
      { errorDisplay: 'inline' },
    )
    expect(mockSetLoginInfo).toHaveBeenCalled()
    expect(mockPush).toHaveBeenCalledWith('/my-courses')
  })
  it('preserves guarded redirect', async () => {
    mockRoute.query = { redirect: '/exams' }
    const w = mountLogin()
    const inputs = w.findAll('input')
    await inputs[0].setValue('learner')
    await inputs[1].setValue('password123')
    await flushPromises()
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(mockPush).toHaveBeenCalledWith('/exams')
  })
  it('does not submit an incomplete form and says which field is missing', async () => {
    const w = mountLogin()
    await w.findAll('input')[0].setValue('learner')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(mockLogin).not.toHaveBeenCalled()
    expect(mockVerify).not.toHaveBeenCalled()
    expect(w.text()).toContain('请输入密码')
    expect(w.findAll('input')[1].attributes('aria-invalid')).toBe('true')
    await w.findAll('input')[1].setValue('x')
    expect(w.text()).not.toContain('请输入密码')
  })
  it('requests verification only on submit and sends nothing after cancellation', async () => {
    mockVerify.mockResolvedValue(null)
    const w = mountLogin()
    expect(w.find('.turnstile-stub').exists()).toBe(false)
    expect(mockVerify).not.toHaveBeenCalled()
    await fill(w)
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(mockVerify).toHaveBeenCalledOnce()
    expect(mockLogin).not.toHaveBeenCalled()
    expect(w.find('input').element.value).toBe('learner')
  })
  it('waits for verification and ignores repeated submits while it is pending', async () => {
    let resolveVerification!: (token: string) => void
    mockVerify.mockImplementation(() => new Promise<string>((resolve) => (resolveVerification = resolve)))
    const w = mountLogin()
    await fill(w)
    await w.find('form').trigger('submit')
    await flushPromises()
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(mockVerify).toHaveBeenCalledOnce()
    expect(mockLogin).not.toHaveBeenCalled()
    resolveVerification('fresh-token')
    await flushPromises()
    expect(mockLogin).toHaveBeenCalledOnce()
    expect(mockLogin).toHaveBeenCalledWith(expect.objectContaining({ turnstileToken: 'fresh-token' }), {
      errorDisplay: 'inline',
    })
  })
  it('requests a new token when retrying a failed login', async () => {
    mockVerify.mockResolvedValueOnce('first-token').mockResolvedValueOnce('second-token')
    mockLogin.mockRejectedValueOnce(new Error('login failed'))
    const w = mountLogin()
    await fill(w)
    await w.find('form').trigger('submit')
    await flushPromises()
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(mockVerify).toHaveBeenCalledTimes(2)
    expect(mockLogin).toHaveBeenNthCalledWith(2, expect.objectContaining({ turnstileToken: 'second-token' }), {
      errorDisplay: 'inline',
    })
  })
  it('ignores a login response after leaving the page', async () => {
    let resolve!: (value: unknown) => void
    mockLogin.mockReturnValueOnce(
      new Promise((yes) => {
        resolve = yes
      }),
    )
    const w = mountLogin()
    await fill(w)
    await w.find('form').trigger('submit')
    await flushPromises()
    w.unmount()
    resolve({ data: { token: 'test-session-placeholder', user: { id: 1 } } })
    await flushPromises()
    expect(mockSetLoginInfo).not.toHaveBeenCalled()
    expect(mockPush).not.toHaveBeenCalled()
  })
  it('shows login failure beside the form and retains the account draft', async () => {
    mockLogin.mockRejectedValueOnce(new Error('用户名或密码错误'))
    const w = mountLogin()
    await fill(w)
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(w.get('[role="alert"]').text()).toContain('用户名或密码错误')
    expect((w.find('input').element as HTMLInputElement).value).toBe('learner')
    w.unmount()
  })
})
