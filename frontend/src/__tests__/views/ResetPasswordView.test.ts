import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import ResetPasswordView from '@/views/auth/ResetPasswordView.vue'

enableAutoUnmount(afterEach)

const { mockResetPassword, mockValidateResetToken } = vi.hoisted(() => ({
  mockResetPassword: vi.fn(),
  mockValidateResetToken: vi.fn(),
}))
const mockRoute = reactive<{ query: Record<string, string> }>({ query: { token: 'reset-token' } })

vi.mock('@/api/auth', () => ({
  resetPassword: (...args: unknown[]) => mockResetPassword(...args),
  validateResetToken: (...args: unknown[]) => mockValidateResetToken(...args),
}))
vi.mock('vue-router', () => ({
  useRoute: () => mockRoute,
  RouterLink: { template: '<a><slot /></a>' },
}))

function mountReset() {
  return mount(ResetPasswordView, { attachTo: document.body })
}

describe('ResetPasswordView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRoute.query = { token: 'reset-token' }
    mockValidateResetToken.mockResolvedValue({ data: 'learner@example.com' })
    mockResetPassword.mockResolvedValue({ data: null })
  })

  it('validates the reset token and masks the account email', async () => {
    const wrapper = mountReset()
    await flushPromises()
    expect(mockValidateResetToken).toHaveBeenCalledWith('reset-token', { errorDisplay: 'inline' })
    expect(wrapper.text()).toContain('le***@example.com')
  })

  it('submits the new password with the reset token', async () => {
    const wrapper = mountReset()
    await flushPromises()
    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('new-password-123')
    await inputs[1].setValue('new-password-123')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(mockResetPassword).toHaveBeenCalledWith('reset-token', 'new-password-123', { errorDisplay: 'inline' })
    expect(wrapper.text()).toContain('密码已重置')
  })

  it('shows an invalid state when token validation fails', async () => {
    mockValidateResetToken.mockRejectedValue(new Error('重置链接无效或已过期'))
    const wrapper = mountReset()
    await flushPromises()
    expect(wrapper.text()).toContain('链接无效或已过期')
  })
  it('keeps a validation network failure recoverable instead of expiring the link', async () => {
    mockValidateResetToken.mockRejectedValueOnce(new Error('Network Error'))
    const wrapper = mountReset()
    await flushPromises()
    expect(wrapper.text()).toContain('暂时无法验证链接')
    expect(wrapper.text()).not.toContain('链接无效或已过期')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '重试验证')!
      .trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('设置新密码')
    wrapper.unmount()
  })

  it('keeps password drafts after submission network failure and prevents duplicate submits', async () => {
    let reject!: (cause: Error) => void
    mockResetPassword.mockImplementationOnce(
      () =>
        new Promise((_resolve, fail) => {
          reject = fail
        }),
    )
    const wrapper = mountReset()
    await flushPromises()
    await wrapper.findAll('input')[0].setValue('new-password-123')
    await wrapper.findAll('input')[1].setValue('new-password-123')
    await wrapper.find('form').trigger('submit')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(mockResetPassword).toHaveBeenCalledTimes(1)
    reject(new Error('Network Error'))
    await flushPromises()
    expect(wrapper.text()).toContain('暂时无法重置密码')
    expect(wrapper.text()).not.toContain('链接无效或已过期')
    expect((wrapper.findAll('input')[0].element as HTMLInputElement).value).toBe('new-password-123')
    wrapper.unmount()
  })
  it('validates password length and equality on submit, focuses the first error and clears errors on edit', async () => {
    const w = mountReset()
    await flushPromises()
    const inputs = w.findAll('input')
    await inputs[0].setValue('short')
    await inputs[1].setValue('different')
    expect(w.find('[data-invalid]').exists()).toBe(false)
    await w.get('form').trigger('submit')
    expect(mockResetPassword).not.toHaveBeenCalled()
    expect(w.text()).toContain('密码长度 8-64 个字符')
    expect(w.text()).toContain('两次输入的密码不一致')
    expect(document.activeElement).toBe(inputs[0].element)
    await inputs[0].setValue('new-password-123')
    expect(inputs[0].attributes('aria-invalid')).toBe('false')
    expect(inputs[1].attributes('aria-invalid')).toBe('true')
    await w.get('form').trigger('submit')
    expect(document.activeElement).toBe(inputs[1].element)
  })

  it('locks all controls while resetting and keeps failures above the submit button until editing', async () => {
    let reject!: (value: Error) => void
    mockResetPassword.mockImplementationOnce(() => new Promise((_resolve, fail) => (reject = fail)))
    const w = mountReset()
    await flushPromises()
    await w.findAll('input')[0].setValue('new-password-123')
    await w.findAll('input')[1].setValue('new-password-123')
    await w.get('form').trigger('submit')
    expect(w.get('button[type="submit"]').text()).toBe('正在重置…')
    expect(w.findAll('input, button').every((control) => control.attributes('disabled') !== undefined)).toBe(true)
    reject(new Error('重置失败'))
    await flushPromises()
    expect(w.get('[role="alert"]').text()).toContain('重置失败')
    expect(w.get('.error').element.nextElementSibling?.getAttribute('type')).toBe('submit')
    await w.findAll('input')[1].setValue('changed')
    expect(w.find('[role="alert"]').exists()).toBe(false)
  })

  it('handles expiry during submission', async () => {
    mockResetPassword.mockRejectedValueOnce(new Error('重置链接无效或已过期'))
    const w = mountReset()
    await flushPromises()
    await w.findAll('input')[0].setValue('new-password-123')
    await w.findAll('input')[1].setValue('new-password-123')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(w.text()).toContain('链接无效或已过期')
    expect(w.find('form').exists()).toBe(false)
  })

  it('ignores a late reset response after leaving', async () => {
    let finish!: (value: unknown) => void
    mockResetPassword.mockImplementationOnce(() => new Promise((resolve) => (finish = resolve)))
    const w = mountReset()
    await flushPromises()
    await w.findAll('input')[0].setValue('new-password-123')
    await w.findAll('input')[1].setValue('new-password-123')
    await w.get('form').trigger('submit')
    w.unmount()
    finish({ data: null })
    await flushPromises()
    expect(w.text()).not.toContain('密码已重置')
  })

  it.each(['checking', 'form', 'success', 'error'])(
    'preserves the %s preview without validating a link',
    async (state) => {
      mockRoute.query = { 'auth-preview': state }
      const w = mountReset()
      await flushPromises()
      expect(mockValidateResetToken).not.toHaveBeenCalled()
      expect(w.text()).toContain(
        { checking: '正在验证链接', form: '设置新密码', success: '密码已重置', error: '链接无效或已过期' }[state],
      )
    },
  )
  it('discards a link validation result when a newer link is opened', async () => {
    let finish!: (value: unknown) => void
    mockValidateResetToken.mockImplementationOnce(() => new Promise((resolve) => (finish = resolve)))
    const w = mountReset()
    expect(w.text()).toContain('正在验证链接')
    mockRoute.query = { token: 'new-reset-token' }
    await flushPromises()
    expect(mockValidateResetToken).toHaveBeenLastCalledWith('new-reset-token', { errorDisplay: 'inline' })
    expect(w.text()).toContain('le***@example.com')
    finish({ data: 'stale@old.example' })
    await flushPromises()
    expect(w.text()).toContain('le***@example.com')
    expect(w.text()).not.toContain('old.example')
  })

  it('ignores a reset for a previous link and clears its drafts when the route token changes', async () => {
    let finish!: (value: unknown) => void
    mockResetPassword.mockImplementationOnce(() => new Promise((resolve) => (finish = resolve)))
    const w = mountReset()
    await flushPromises()
    await w.findAll('input')[0].setValue('new-password-123')
    await w.findAll('input')[1].setValue('new-password-123')
    await w.get('form').trigger('submit')
    mockRoute.query = { token: 'new-reset-token' }
    await flushPromises()
    finish({ data: null })
    await flushPromises()
    expect(w.text()).toContain('设置新密码')
    expect(w.text()).not.toContain('密码已重置')
    expect(w.findAll('input').map((input) => input.element.value)).toEqual(['', ''])
  })

  it('does not request validation for a missing link token', async () => {
    mockRoute.query = {}
    const w = mountReset()
    await flushPromises()
    expect(mockValidateResetToken).not.toHaveBeenCalled()
    expect(w.text()).toContain('链接无效或已过期')
  })
})
