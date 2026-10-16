import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import RegisterView from '@/views/auth/RegisterView.vue'

enableAutoUnmount(afterEach)

const { mockRegister, mockSendCode, mockVerifyCode, mockPush, mockVerify, mockRoute, mockSetLoginInfo } = vi.hoisted(
  () => ({
    mockRegister: vi.fn(),
    mockSendCode: vi.fn(),
    mockVerifyCode: vi.fn(),
    mockPush: vi.fn(),
    mockVerify: vi.fn(),
    mockRoute: { query: {} as Record<string, string> },
    mockSetLoginInfo: vi.fn(),
  }),
)
vi.mock('@/stores/user', () => ({ useUserStore: () => ({ setLoginInfo: mockSetLoginInfo }) }))
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
const stubs = {
  TurnstileSheet: defineComponent({
    setup(_props, { expose }) {
      expose({ verify: mockVerify })
      return () => h('div')
    },
  }),
}
function mountRegister() {
  return mount(RegisterView, { attachTo: document.body, global: { stubs } })
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
    mockVerify.mockResolvedValue('turnstile-ok')
    mockSendCode.mockResolvedValue({})
    mockVerifyCode.mockResolvedValue({ data: { verificationTicket: 'ticket-1', expiresIn: 300 } })
    vi.useRealTimers()
    mockRegister.mockResolvedValue({
      data: { token: 'registered-session', user: { id: 2, username: 'newlearner', role: 'USER' } },
    })
  })
  it('renders three-step registration beginning with username and email', () => {
    const w = mountRegister()
    expect(w.text()).toContain('创建学习账号')
    expect(w.text()).toContain('步骤 1/3')
    expect(w.text()).not.toContain('填写用户名和邮箱')
    expect(w.html()).toContain('3-50 个字符')
    expect(w.html()).toContain('placeholder="邮箱"')
    expect(w.text()).not.toContain('其他登录方式')
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
    expect(mockSendCode).toHaveBeenCalledWith('learner@example.com', 'turnstile-ok', { errorDisplay: 'inline' })
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
    expect(mockRegister).toHaveBeenCalledWith(
      {
        username: 'newlearner',
        email: 'learner@example.com',
        password: 'Password1!',
        verificationTicket: 'ticket-1',
      },
      { errorDisplay: 'inline' },
    )
    expect(mockSetLoginInfo).toHaveBeenCalledWith('registered-session', {
      id: 2,
      username: 'newlearner',
      role: 'USER',
    })
    expect(mockPush).toHaveBeenCalledWith('/my-courses')
  })

  it('allows only one pending code verification and ignores it after leaving', async () => {
    let finish: (value: unknown) => void = () => undefined
    mockVerifyCode.mockImplementation(() => new Promise((resolve) => (finish = resolve)))
    const w = mountRegister()
    await w.findAll('input')[0].setValue('newlearner')
    await w.findAll('input')[1].setValue('learner@example.com')
    await w.find('form').trigger('submit')
    await flushPromises()
    await w.find('input').setValue('123456')
    await w.find('form').trigger('submit')
    await w.find('form').trigger('submit')
    expect(mockVerifyCode).toHaveBeenCalledOnce()
    w.unmount()
    finish({ data: { verificationTicket: 'late-test-ticket', expiresIn: 300 } })
    await flushPromises()
    expect(mockRegister).not.toHaveBeenCalled()
    expect(mockPush).not.toHaveBeenCalled()
  })

  it('locks registration and never authenticates or navigates for a late response', async () => {
    const w = mountRegister()
    await advanceToPassword(w)
    let finish: (value: unknown) => void = () => undefined
    mockRegister.mockImplementation(() => new Promise((resolve) => (finish = resolve)))
    await w.findAll('input')[0].setValue('Password1!')
    await w.findAll('input')[1].setValue('Password1!')
    await w.find('form').trigger('submit')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(mockRegister).toHaveBeenCalledOnce()
    await w.find('form').trigger('submit')
    expect(mockRegister).toHaveBeenCalledOnce()
    w.unmount()
    finish({ data: { id: 2 } })
    await flushPromises()
    expect(mockSetLoginInfo).not.toHaveBeenCalled()
    expect(mockPush).not.toHaveBeenCalled()
  })
  it('validates required fields on submit, focuses the first error and clears only the edited field', async () => {
    const w = mountRegister()
    const inputs = w.findAll('input')
    expect(w.find('[data-invalid]').exists()).toBe(false)
    await w.get('form').trigger('submit')
    expect(mockVerify).not.toHaveBeenCalled()
    expect(w.text()).toContain('请输入用户名')
    expect(w.text()).toContain('请输入邮箱')
    expect(document.activeElement).toBe(inputs[0].element)
    await inputs[0].setValue('ab')
    expect(inputs[0].attributes('aria-invalid')).toBe('false')
    expect(inputs[1].attributes('aria-invalid')).toBe('true')
    await inputs[1].setValue('invalid')
    await w.get('form').trigger('submit')
    expect(w.text()).toContain('用户名长度 3-50 个字符')
    expect(w.text()).toContain('邮箱格式不正确')
    expect(w.text()).toContain('步骤 1/3')
  })

  it('validates six-digit codes and preserves the verified-code contract', async () => {
    mockRoute.query = { 'auth-preview': 'step-2' }
    const w = mountRegister()
    const input = w.get('input')
    expect(input.attributes('inputmode')).toBe('numeric')
    expect(input.attributes('maxlength')).toBe('6')
    await input.setValue('abc123')
    await w.get('form').trigger('submit')
    expect(mockVerifyCode).not.toHaveBeenCalled()
    expect(w.text()).toContain('请输入 6 位数字验证码')
    expect(document.activeElement).toBe(input.element)
    await input.setValue('123456')
    expect(input.attributes('aria-invalid')).toBe('false')
  })

  it('shows send success inline, counts down by elapsed time and permits resending after 60 seconds', async () => {
    vi.useFakeTimers()
    mockRoute.query = { 'auth-preview': 'step-2' }
    const w = mountRegister()
    const send = w.findAll('button').find((b) => b.text() === '获取验证码')!
    await send.trigger('click')
    await flushPromises()
    expect(w.text()).toContain('验证码已发送')
    expect(send.text()).toBe('60s 后重发')
    expect(send.attributes('disabled')).toBeDefined()
    await vi.advanceTimersByTimeAsync(59_000)
    expect(send.text()).toBe('1s 后重发')
    await vi.advanceTimersByTimeAsync(1000)
    expect(send.text()).toBe('获取验证码')
    expect(send.attributes('disabled')).toBeUndefined()
    w.unmount()
    expect(vi.getTimerCount()).toBe(0)
    vi.useRealTimers()
  })

  it('keeps send and verification failures above the actions, retains the code, and clears errors when edited', async () => {
    mockRoute.query = { 'auth-preview': 'step-2' }
    mockSendCode.mockRejectedValueOnce(new Error('邮件发送失败'))
    const w = mountRegister()
    await w
      .findAll('button')
      .find((b) => b.text() === '获取验证码')!
      .trigger('click')
    await flushPromises()
    expect(w.get('[role="alert"]').text()).toContain('邮件发送失败')
    expect(w.text()).not.toContain('后重发')
    mockVerifyCode.mockRejectedValueOnce(new Error('验证码不正确'))
    await w.get('input').setValue('123456')
    expect(w.find('[role="alert"]').exists()).toBe(false)
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(mockVerifyCode).toHaveBeenCalledWith('', '123456', { errorDisplay: 'inline' })
    expect(w.get('[role="alert"]').text()).toContain('验证码不正确')
    expect(w.get('input').element.value).toBe('123456')
    expect(w.get('.error').element.nextElementSibling?.className).toBe('actions')
  })

  it('validates password length and confirmation, preserves strength feedback and registration drafts on error', async () => {
    const w = mountRegister()
    await advanceToPassword(w)
    const inputs = w.findAll('input')
    await inputs[0].setValue('short')
    await inputs[1].setValue('different')
    await w.get('form').trigger('submit')
    expect(mockRegister).not.toHaveBeenCalled()
    expect(w.text()).toContain('密码长度 8-64 个字符')
    expect(w.text()).toContain('两次输入的密码不一致')
    expect(document.activeElement).toBe(inputs[0].element)
    await inputs[0].setValue('Password1!')
    await inputs[1].setValue('Password1!')
    expect(w.text()).toContain('良好')
    mockRegister.mockRejectedValueOnce(new Error('用户名已存在'))
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(w.get('[role="alert"]').text()).toContain('用户名已存在')
    expect(inputs[0].element.value).toBe('Password1!')
    await inputs[0].setValue('Password2!')
    expect(w.find('[role="alert"]').exists()).toBe(false)
  })

  it('ignores abandoned verification before sending email and locks all controls while pending', async () => {
    let finish!: (value: string) => void
    mockVerify.mockImplementationOnce(() => new Promise<string>((resolve) => (finish = resolve)))
    mockRoute.query = { 'auth-preview': 'step-2' }
    const w = mountRegister()
    await w
      .findAll('button')
      .find((b) => b.text() === '获取验证码')!
      .trigger('click')
    expect(w.text()).toContain('正在发送…')
    expect(w.findAll('input, button').every((control) => control.attributes('disabled') !== undefined)).toBe(true)
    w.unmount()
    finish('late-verification')
    await flushPromises()
    expect(mockSendCode).not.toHaveBeenCalled()
  })

  it('clears the verification ticket and requires verification again when returning from the password step', async () => {
    const w = mountRegister()
    await advanceToPassword(w)
    await w
      .findAll('button')
      .find((b) => b.text() === '上一步')!
      .trigger('click')
    expect(w.text()).toContain('步骤 2/3')
    expect(w.get('input').element.value).toBe('')
    await w.get('form').trigger('submit')
    expect(mockRegister).not.toHaveBeenCalled()
    expect(mockVerifyCode).toHaveBeenCalledOnce()
  })
})
