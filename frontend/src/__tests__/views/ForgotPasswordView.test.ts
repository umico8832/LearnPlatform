import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import ForgotPasswordView from '@/views/auth/ForgotPasswordView.vue'

enableAutoUnmount(afterEach)

const { mockForgotPassword, mockVerify } = vi.hoisted(() => ({
  mockForgotPassword: vi.fn(),
  mockVerify: vi.fn(),
}))

const mockRoute = { query: {} as Record<string, string> }

vi.mock('@/api/auth', () => ({
  forgotPassword: (...args: unknown[]) => mockForgotPassword(...args),
}))

vi.mock('vue-router', () => ({
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

describe('ForgotPasswordView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRoute.query = {}
    mockVerify.mockResolvedValue('turnstile-ok')
    mockForgotPassword.mockResolvedValue({ data: null })
  })

  it('sends email and Turnstile token, then shows a neutral result', async () => {
    const wrapper = mount(ForgotPasswordView, { attachTo: document.body, global: { stubs } })
    await wrapper.find('input').setValue('learner@example.com')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(mockForgotPassword).toHaveBeenCalledWith('learner@example.com', 'turnstile-ok', { errorDisplay: 'inline' })
    expect(wrapper.text()).toContain('如果该邮箱已注册')
    expect(wrapper.text()).toContain('重置链接 30 分钟内有效。没有收到邮件？')
    expect(wrapper.get('.recovery-resend-link').text()).toBe('重新发送')

    await wrapper.get('.recovery-resend-link').trigger('click')
    await flushPromises()
    expect(mockForgotPassword).toHaveBeenCalledTimes(2)
    expect(mockForgotPassword).toHaveBeenLastCalledWith('learner@example.com', 'turnstile-ok', {
      errorDisplay: 'inline',
    })
    expect(wrapper.get('.recovery-resend-link').text()).toBe('再次发送')

    await wrapper.get('.recovery-resend-link').trigger('click')
    await flushPromises()
    expect(mockForgotPassword).toHaveBeenCalledTimes(3)
    expect(wrapper.find('.recovery-resend-link').exists()).toBe(false)
    expect(wrapper.get('.recovery-support-link').text()).toBe('重新填写邮箱')
    await wrapper.get('.recovery-support-link').trigger('click')
    expect(wrapper.find('input').element.value).toBe('learner@example.com')
  })

  it('does not call the API when validation fails', async () => {
    const wrapper = mount(ForgotPasswordView, { attachTo: document.body, global: { stubs } })
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(mockForgotPassword).not.toHaveBeenCalled()
    expect(mockVerify).not.toHaveBeenCalled()
  })
  it('preserves the email and sends nothing when verification is cancelled', async () => {
    mockVerify.mockResolvedValue(null)
    const wrapper = mount(ForgotPasswordView, { attachTo: document.body, global: { stubs } })
    await wrapper.find('input').setValue('learner@example.com')
    expect(mockVerify).not.toHaveBeenCalled()
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(mockVerify).toHaveBeenCalledOnce()
    expect(mockForgotPassword).not.toHaveBeenCalled()
    expect(wrapper.find('input').element.value).toBe('learner@example.com')
  })
  it('validates email format, focuses the field and clears its error when edited', async () => {
    const w = mount(ForgotPasswordView, { attachTo: document.body, global: { stubs } })
    const input = w.get('input')
    await input.setValue('invalid')
    expect(input.attributes('aria-invalid')).toBe('false')
    await w.get('form').trigger('submit')
    expect(w.text()).toContain('邮箱格式不正确')
    expect(document.activeElement).toBe(input.element)
    expect(mockVerify).not.toHaveBeenCalled()
    await input.setValue('learner@example.com')
    expect(input.attributes('aria-invalid')).toBe('false')
  })

  it('retains email and displays a failed request above the submit button', async () => {
    mockForgotPassword.mockRejectedValueOnce(new Error('邮件服务不可用'))
    const w = mount(ForgotPasswordView, { global: { stubs } })
    await w.get('input').setValue('learner@example.com')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(w.get('[role="alert"]').text()).toContain('邮件服务不可用')
    expect(w.get('.error').element.nextElementSibling?.getAttribute('type')).toBe('submit')
    expect(w.get('input').element.value).toBe('learner@example.com')
    await w.get('input').setValue('other@example.com')
    expect(w.find('[role="alert"]').exists()).toBe(false)
  })

  it('does not consume a resend attempt after cancellation or request failure', async () => {
    const w = mount(ForgotPasswordView, { global: { stubs } })
    await w.get('input').setValue('learner@example.com')
    await w.get('form').trigger('submit')
    await flushPromises()
    mockVerify.mockResolvedValueOnce(null)
    await w.get('.recovery-resend-link').trigger('click')
    await flushPromises()
    expect(mockForgotPassword).toHaveBeenCalledOnce()
    expect(w.get('.recovery-resend-link').text()).toBe('重新发送')
    mockForgotPassword.mockRejectedValueOnce(new Error('发送失败'))
    await w.get('.recovery-resend-link').trigger('click')
    await flushPromises()
    expect(w.get('[role="alert"]').text()).toContain('发送失败')
    expect(w.get('.recovery-resend-link').text()).toBe('重新发送')
  })

  it('locks pending requests and ignores late responses after leaving', async () => {
    let finish!: (value: unknown) => void
    mockForgotPassword.mockImplementationOnce(() => new Promise((resolve) => (finish = resolve)))
    const w = mount(ForgotPasswordView, { global: { stubs } })
    await w.get('input').setValue('learner@example.com')
    await w.get('form').trigger('submit')
    await flushPromises()
    await w.get('form').trigger('submit')
    expect(mockForgotPassword).toHaveBeenCalledOnce()
    expect(w.get('input').attributes('disabled')).toBeDefined()
    expect(w.get('button[type="submit"]').text()).toBe('正在发送…')
    w.unmount()
    finish({ data: null })
    await flushPromises()
    expect(w.text()).not.toContain('查看重置邮件')
  })

  it.each(['form', 'sent', 'resent', 'support'])('preserves the %s preview without sending requests', async (state) => {
    mockRoute.query = { 'auth-preview': state }
    const w = mount(ForgotPasswordView, { global: { stubs } })
    await flushPromises()
    expect(mockForgotPassword).not.toHaveBeenCalled()
    expect(mockVerify).not.toHaveBeenCalled()
    if (state === 'form') expect(w.get('input').element.value).toBe('')
    else expect(w.text()).toContain('查看重置邮件')
    if (state === 'resent') expect(w.get('.recovery-resend-link').text()).toBe('再次发送')
    if (state === 'support') expect(w.get('.recovery-support-link').text()).toBe('重新填写邮箱')
  })
})
