import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import ResetPasswordView from '@/views/auth/ResetPasswordView.vue'

enableAutoUnmount(afterEach)

const { mockResetPassword, mockValidateResetToken, mockValidate } = vi.hoisted(() => ({
  mockResetPassword: vi.fn(),
  mockValidateResetToken: vi.fn(),
  mockValidate: vi.fn().mockResolvedValue(true),
}))
const mockRoute: { query: Record<string, string> } = { query: { token: 'reset-token' } }

vi.mock('@/api/auth', () => ({
  resetPassword: (...args: unknown[]) => mockResetPassword(...args),
  validateResetToken: (...args: unknown[]) => mockValidateResetToken(...args),
}))
vi.mock('vue-router', () => ({ useRoute: () => mockRoute }))

const stubs = {
  AuthLayout: { template: '<main><slot /></main>' },
  'el-form': { template: '<form><slot /></form>', methods: { validate: () => mockValidate() } },
  'el-form-item': { template: '<label><slot /></label>' },
  'el-input': {
    template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    props: ['modelValue'],
  },
  'el-button': { template: '<button><slot /></button>' },
  'el-icon': { template: '<span><slot /></span>' },
}

describe('ResetPasswordView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRoute.query = { token: 'reset-token' }
    mockValidate.mockResolvedValue(true)
    mockValidateResetToken.mockResolvedValue({ data: 'learner@example.com' })
    mockResetPassword.mockResolvedValue({ data: null })
  })

  it('validates the reset token and masks the account email', async () => {
    const wrapper = mount(ResetPasswordView, { global: { stubs } })
    await flushPromises()
    expect(mockValidateResetToken).toHaveBeenCalledWith('reset-token', { errorDisplay: 'inline' })
    expect(wrapper.text()).toContain('le***@example.com')
  })

  it('submits the new password with the reset token', async () => {
    const wrapper = mount(ResetPasswordView, { global: { stubs } })
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
    const wrapper = mount(ResetPasswordView, { global: { stubs } })
    await flushPromises()
    expect(wrapper.text()).toContain('链接无效或已过期')
  })
  it('keeps a validation network failure recoverable instead of expiring the link', async () => {
    mockValidateResetToken.mockRejectedValueOnce(new Error('Network Error'))
    const wrapper = mount(ResetPasswordView, { global: { stubs } })
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
    const wrapper = mount(ResetPasswordView, { global: { stubs } })
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
})
