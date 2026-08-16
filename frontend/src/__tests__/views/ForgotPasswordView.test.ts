import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import ForgotPasswordView from '@/views/auth/ForgotPasswordView.vue'

const { mockForgotPassword, mockValidate } = vi.hoisted(() => ({
  mockForgotPassword: vi.fn(),
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
  TurnstileWidget: defineComponent({
    emits: ['update:modelValue'],
    setup(_props, { emit, expose }) {
      const reset = vi.fn()
      expose({ reset })
      emit('update:modelValue', 'turnstile-ok')
      return () => h('div', { class: 'turnstile-stub' })
    },
  }),
}

describe('ForgotPasswordView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockValidate.mockResolvedValue(true)
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
  })
})
