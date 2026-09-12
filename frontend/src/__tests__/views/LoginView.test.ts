import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import LoginView from '@/views/auth/LoginView.vue'

const { mockLogin, mockPush, mockSetLoginInfo, mockSuccess, mockValidate, mockVerify } = vi.hoisted(() => ({
  mockLogin: vi.fn(),
  mockPush: vi.fn(),
  mockSetLoginInfo: vi.fn(),
  mockSuccess: vi.fn(),
  mockValidate: vi.fn().mockResolvedValue(true),
  mockVerify: vi.fn(),
}))
const mockRoute: { query: Record<string, string> } = { query: {} }
vi.mock('@/api/auth', () => ({ login: (...args: unknown[]) => mockLogin(...args) }))
vi.mock('@/stores/user', () => ({ useUserStore: () => ({ setLoginInfo: mockSetLoginInfo }) }))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
  useRoute: () => mockRoute,
  RouterLink: { template: '<a><slot /></a>' },
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessage: { ...actual.ElMessage, success: mockSuccess } }
})

const stubs = {
  'router-link': { template: '<a><slot /></a>' },
  'el-form': {
    template: '<form><slot /></form>',
    props: ['model', 'rules'],
    methods: { validate: () => mockValidate() },
  },
  'el-form-item': { template: '<label><slot /></label>', props: ['label', 'prop'] },
  'el-input': {
    template:
      '<input :value="modelValue" :placeholder="placeholder" :type="type||\'text\'" @input="$emit(\'update:modelValue\',$event.target.value)" />',
    props: ['modelValue', 'placeholder', 'type'],
  },
  'el-button': {
    template: '<button :disabled="disabled"><slot /></button>',
    props: ['disabled', 'loading', 'nativeType'],
  },
  'el-icon': { template: '<span><slot /></span>' },
  TurnstileDialog: defineComponent({
    setup(_props, { expose }) {
      expose({ verify: mockVerify })
      return () => h('div')
    },
  }),
}
function mountLogin() {
  return mount(LoginView, { global: { stubs } })
}

describe('LoginView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockValidate.mockResolvedValue(true)
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
    expect(mockLogin).toHaveBeenCalledWith({
      account: 'learner@example.com',
      password: 'password123',
      turnstileToken: 'turnstile-ok',
    })
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
  it('does not submit invalid form', async () => {
    mockValidate.mockResolvedValue(false)
    const w = mountLogin()
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(mockLogin).not.toHaveBeenCalled()
    expect(mockVerify).not.toHaveBeenCalled()
  })
  it('requests verification only on submit and sends nothing after cancellation', async () => {
    mockVerify.mockResolvedValue(null)
    const w = mountLogin()
    expect(w.find('.turnstile-stub').exists()).toBe(false)
    expect(mockVerify).not.toHaveBeenCalled()
    await w.find('input').setValue('learner')
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
    await w.find('form').trigger('submit')
    await flushPromises()
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(mockVerify).toHaveBeenCalledOnce()
    expect(mockLogin).not.toHaveBeenCalled()
    resolveVerification('fresh-token')
    await flushPromises()
    expect(mockLogin).toHaveBeenCalledOnce()
    expect(mockLogin).toHaveBeenCalledWith(expect.objectContaining({ turnstileToken: 'fresh-token' }))
  })
  it('requests a new token when retrying a failed login', async () => {
    mockVerify.mockResolvedValueOnce('first-token').mockResolvedValueOnce('second-token')
    mockLogin.mockRejectedValueOnce(new Error('login failed'))
    const w = mountLogin()
    await w.find('form').trigger('submit')
    await flushPromises()
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(mockVerify).toHaveBeenCalledTimes(2)
    expect(mockLogin).toHaveBeenNthCalledWith(2, expect.objectContaining({ turnstileToken: 'second-token' }))
  })
})
