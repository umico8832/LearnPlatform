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
} = vi.hoisted(() => ({
  mockRegister: vi.fn(),
  mockSendCode: vi.fn(),
  mockVerifyCode: vi.fn(),
  mockPush: vi.fn(),
  mockSuccess: vi.fn(),
  mockVerify: vi.fn(),
  mockValidate: vi.fn().mockResolvedValue(true),
  mockValidateField: vi.fn().mockResolvedValue(undefined),
}))
vi.mock('@/api/auth', () => ({
  register: (...a: unknown[]) => mockRegister(...a),
  sendRegisterCode: (...a: unknown[]) => mockSendCode(...a),
  verifyRegisterCode: (...a: unknown[]) => mockVerifyCode(...a),
  getOAuthProviders: () => Promise.resolve({ data: { google: false } }),
}))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
  useRoute: () => ({ query: {} }),
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
    expect(w.text()).toContain('账户信息 · 1/3')
    expect(w.html()).toContain('3-50 个字符')
    expect(w.html()).toContain('placeholder="邮箱"')
  })
  it('requires Turnstile before sending registration email', async () => {
    const w = mountRegister()
    const inputs = w.findAll('input')
    await inputs[0].setValue('newlearner')
    await inputs[1].setValue('learner@example.com')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(mockVerify).not.toHaveBeenCalled()
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
    expect(w.text()).toContain('设置密码 · 3/3')
    const inputs = w.findAll('input')
    await inputs[0].setValue('Password1!')
    await inputs[1].setValue('Password1!')
    await inputs[2].setValue('学习者')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(mockRegister).toHaveBeenCalledWith({
      username: 'newlearner',
      email: 'learner@example.com',
      password: 'Password1!',
      nickname: '学习者',
      verificationTicket: 'ticket-1',
    })
    expect(mockPush).toHaveBeenCalledWith('/login')
  })
})
