import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import RegisterView from '@/views/auth/RegisterView.vue'

const {
  mockRegister,
  mockSendCode,
  mockVerifyCode,
  mockPush,
  mockSuccess,
  mockValidate,
  mockValidateField,
  mockVerify,
  mockRoute,
} = vi.hoisted(() => ({
  mockRegister: vi.fn(),
  mockSendCode: vi.fn(),
  mockVerifyCode: vi.fn(),
  mockPush: vi.fn(),
  mockSuccess: vi.fn(),
  mockVerify: vi.fn(),
  mockValidate: vi.fn().mockResolvedValue(true),
  mockValidateField: vi.fn().mockResolvedValue(undefined),
  mockRoute: { query: {} as Record<string, string> },
}))
vi.mock('@/api/auth', () => ({
  register: (...a: unknown[]) => mockRegister(...a),
  sendRegisterCode: (...a: unknown[]) => mockSendCode(...a),
  verifyRegisterCode: (...a: unknown[]) => mockVerifyCode(...a),
  getOAuthProviders: () => Promise.resolve({ data: { google: false } }),
}))
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
    methods: { validate: () => mockValidate(), validateField: () => mockValidateField() },
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
function mountRegister() {
  return mount(RegisterView, { global: { stubs } })
}
async function advanceToPassword(w: ReturnType<typeof mountRegister>) {
  let inputs = w.findAll('input')
  await inputs[0].setValue('newlearner')
  await inputs[1].setValue('learner@example.com')
  await w.find('form').trigger('submit')
  await flushPromises()
  const send = w.findAll('button').find((b) => b.text().includes('获取验证码'))
  await send?.trigger('click')
  await flushPromises()
  inputs = w.findAll('input')
  await inputs[0].setValue('123456')
  await w.find('form').trigger('submit')
  await flushPromises()
}

describe('RegisterView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRoute.query = {}
    mockValidate.mockResolvedValue(true)
    mockVerify.mockResolvedValue('turnstile-ok')
    mockValidateField.mockResolvedValue(undefined)
    mockSendCode.mockResolvedValue({})
    mockVerifyCode.mockResolvedValue({ data: { verificationTicket: 'ticket-1', expiresIn: 300 } })
    mockRegister.mockResolvedValue({ data: { id: 2, username: 'newlearner' } })
  })
  it('renders three-step registration beginning with username and email', () => {
    const w = mountRegister()
    expect(w.text()).toContain('创建学习账号')
    expect(w.text()).toContain('步骤 1/3')
    expect(w.text()).not.toContain('填写用户名和邮箱')
    expect(w.html()).toContain('3-50 个字符')
    expect(w.html()).toContain('placeholder="邮箱"')
    expect(w.text()).toContain('其他登录方式')
  })
  it('keeps the first-step preview form empty', () => {
    mockRoute.query = { 'auth-preview': 'step-1' }
    const w = mountRegister()
    const inputs = w.findAll('input')
    expect(inputs[0].element.value).toBe('')
    expect(inputs[1].element.value).toBe('')
  })
  it('does not inject a sample account into the second-step preview', () => {
    mockRoute.query = { 'auth-preview': 'step-2' }
    const w = mountRegister()
    expect(w.text()).toContain('验证码将发送至注册邮箱')
    expect(w.text()).not.toContain('example.com')
  })
  it('requires Turnstile before sending registration email', async () => {
    const w = mountRegister()
    const inputs = w.findAll('input')
    await inputs[0].setValue('newlearner')
    await inputs[1].setValue('learner@example.com')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(mockVerify).not.toHaveBeenCalled()
    expect(w.text()).toContain('验证码将发送至 learner@example.com')
    expect(w.text()).not.toContain('其他登录方式')
    expect(w.text()).not.toContain('已有账号？')
    const send = w.findAll('button').find((b) => b.text().includes('获取验证码'))
    await send?.trigger('click')
    await flushPromises()
    expect(mockSendCode).toHaveBeenCalledWith('learner@example.com', 'turnstile-ok')
  })
  it('does not send or start the cooldown when verification is cancelled', async () => {
    mockVerify.mockResolvedValue(null)
    const w = mountRegister()
    await w.findAll('input')[0].setValue('newlearner')
    await w.findAll('input')[1].setValue('learner@example.com')
    await w.find('form').trigger('submit')
    await flushPromises()
    const send = w.findAll('button').find((b) => b.text() === '获取验证码')!
    await send.trigger('click')
    await flushPromises()
    expect(mockVerify).toHaveBeenCalledOnce()
    expect(mockSendCode).not.toHaveBeenCalled()
    expect(send.attributes('disabled')).toBeUndefined()
    expect(w.text()).toContain('learner@example.com')
    expect(w.text()).not.toContain('后重发')
    w.unmount()
  })
  it('uses verified ticket when creating account', async () => {
    const w = mountRegister()
    await advanceToPassword(w)
    expect(w.text()).toContain('步骤 3/3')
    expect(w.text()).not.toContain('设置用于登录的密码')
    const inputs = w.findAll('input')
    expect(inputs).toHaveLength(2)
    await inputs[0].setValue('Password1!')
    await inputs[1].setValue('Password1!')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(mockRegister).toHaveBeenCalledWith({
      username: 'newlearner',
      email: 'learner@example.com',
      password: 'Password1!',
      verificationTicket: 'ticket-1',
    })
    expect(mockPush).toHaveBeenCalledWith('/login')
  })
})
