import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import AdminLoginView from '@/admin/views/AdminLoginView.vue'

const { mockLogin, mockVerify, mockPush, mockSetLoginInfo, mockClearLoginInfo } = vi.hoisted(() => ({
  mockLogin: vi.fn(),
  mockVerify: vi.fn(),
  mockPush: vi.fn(),
  mockSetLoginInfo: vi.fn(),
  mockClearLoginInfo: vi.fn(),
}))
vi.mock('@/api/auth', () => ({ login: (...args: unknown[]) => mockLogin(...args) }))
vi.mock('@/stores/user', () => ({
  useUserStore: () => ({ setLoginInfo: mockSetLoginInfo, clearLoginInfo: mockClearLoginInfo }),
}))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
  useRoute: () => ({ query: { redirect: '/subjective-reviews' } }),
}))
vi.mock('element-plus', async (importOriginal) => ({
  ...(await importOriginal<typeof import('element-plus')>()),
  ElMessage: { success: vi.fn(), error: vi.fn() },
}))

function mountLogin() {
  return mount(AdminLoginView, {
    global: {
      stubs: {
        'el-form': { template: '<form><slot /></form>', methods: { validate: () => Promise.resolve(true) } },
        'el-form-item': { template: '<div><slot /></div>' },
        'el-input': true,
        'el-button': { template: '<button><slot /></button>' },
        TurnstileDialog: defineComponent({
          setup(_props, { expose }) {
            expose({ verify: mockVerify })
            return () => h('div')
          },
        }),
      },
    },
  })
}

describe('AdminLoginView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockVerify.mockResolvedValue('verified-token')
    mockLogin.mockResolvedValue({ data: { token: 'jwt', user: { id: 1, role: 'ADMIN' } } })
  })

  it('continues the requested admin route only after verification and admin login', async () => {
    const w = mountLogin()
    expect(mockVerify).not.toHaveBeenCalled()
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(mockVerify).toHaveBeenCalledOnce()
    expect(mockLogin).toHaveBeenCalledWith(expect.objectContaining({ turnstileToken: 'verified-token' }))
    expect(mockSetLoginInfo).toHaveBeenCalledOnce()
    expect(mockPush).toHaveBeenCalledWith('/subjective-reviews')
  })

  it('does not establish an admin session for a learner account', async () => {
    mockLogin.mockResolvedValue({ data: { token: 'jwt', user: { id: 2, role: 'USER' } } })
    const w = mountLogin()
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(mockClearLoginInfo).toHaveBeenCalledOnce()
    expect(mockSetLoginInfo).not.toHaveBeenCalled()
    expect(mockPush).not.toHaveBeenCalled()
  })

  it('sends no login request after cancelling verification', async () => {
    mockVerify.mockResolvedValue(null)
    const w = mountLogin()
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(mockLogin).not.toHaveBeenCalled()
    expect(mockPush).not.toHaveBeenCalled()
  })
})
