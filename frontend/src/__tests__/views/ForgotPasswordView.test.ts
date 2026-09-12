import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import ForgotPasswordView from '@/views/auth/ForgotPasswordView.vue'

const { mockForgotPassword, mockValidate, mockVerify } = vi.hoisted(() => ({
  mockForgotPassword: vi.fn(),
  mockVerify: vi.fn(),
  mockValidate: vi.fn().mockResolvedValue(true),
}))

vi.mock('@/api/auth', () => ({
  forgotPassword: (...args: unknown[]) => mockForgotPassword(...args),
}))

const stubs = {
  AuthLayout: { template: '<main><slot /></main>' },
  'router-link': { template: '<a><slot /></a>' },
  'el-form': { template: '<form><slot /></form>', methods: { validate: () => mockValidate() } },
  'el-form-item': { template: '<label><slot /></label>' },
  'el-input': {
    template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    props: ['modelValue'],
  },
  'el-button': { template: '<button :disabled="disabled"><slot /></button>', props: ['disabled'] },
  'el-icon': { template: '<span><slot /></span>' },
  TurnstileDialog: defineComponent({
    setup(_props, { expose }) {
      expose({ verify: mockVerify })
      return () => h('div')
    },
  }),
}

describe('ForgotPasswordView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockValidate.mockResolvedValue(true)
    mockVerify.mockResolvedValue('turnstile-ok')
    mockForgotPassword.mockResolvedValue({ data: null })
  })

  it('sends email and Turnstile token, then shows a neutral result', async () => {
    const wrapper = mount(ForgotPasswordView, { global: { stubs } })
    await wrapper.find('input').setValue('learner@example.com')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(mockForgotPassword).toHaveBeenCalledWith('learner@example.com', 'turnstile-ok')
    expect(wrapper.text()).toContain('如果该邮箱已注册')
  })

  it('does not call the API when validation fails', async () => {
    mockValidate.mockResolvedValue(false)
    const wrapper = mount(ForgotPasswordView, { global: { stubs } })
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(mockForgotPassword).not.toHaveBeenCalled()
    expect(mockVerify).not.toHaveBeenCalled()
  })
  it('preserves the email and sends nothing when verification is cancelled', async () => {
    mockVerify.mockResolvedValue(null)
    const wrapper = mount(ForgotPasswordView, { global: { stubs } })
    await wrapper.find('input').setValue('learner@example.com')
    expect(mockVerify).not.toHaveBeenCalled()
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(mockVerify).toHaveBeenCalledOnce()
    expect(mockForgotPassword).not.toHaveBeenCalled()
    expect(wrapper.find('input').element.value).toBe('learner@example.com')
  })
})
