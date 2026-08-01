import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ResetPasswordView from '@/views/auth/ResetPasswordView.vue'

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
  'el-input': { template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />', props: ['modelValue'] },
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
    expect(mockValidateResetToken).toHaveBeenCalledWith('reset-token')
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
    expect(mockResetPassword).toHaveBeenCalledWith('reset-token', 'new-password-123')
    expect(wrapper.text()).toContain('密码已重置')
  })

  it('shows an invalid state when token validation fails', async () => {
    mockValidateResetToken.mockRejectedValue(new Error('expired'))
    const wrapper = mount(ResetPasswordView, { global: { stubs } })
    await flushPromises()
    expect(wrapper.text()).toContain('链接无效或已过期')
  })
})
