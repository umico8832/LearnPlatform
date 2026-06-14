import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const { mockPost, mockPush, mockSuccess, mockValidate } = vi.hoisted(() => ({
  mockPost: vi.fn(),
  mockPush: vi.fn(),
  mockSuccess: vi.fn(),
  mockValidate: vi.fn().mockResolvedValue(true),
}))

vi.mock('@/utils/request', () => ({
  default: { post: (...args: unknown[]) => mockPost(...args) },
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
  RouterLink: { template: '<a><slot /></a>' },
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessage: { ...actual.ElMessage, success: mockSuccess } }
})

const globalStubs = {
  'el-form': {
    template: '<form @submit.prevent><slot /></form>',
    props: ['model', 'rules', 'labelWidth', 'size'],
    methods: { validate: (...args: unknown[]) => mockValidate(...args) },
  },
  'el-form-item': { template: '<div class="form-item"><slot /></div>', props: ['prop'] },
  'el-input': {
    template: '<input :value="modelValue" :placeholder="placeholder" :type="type || \'text\'" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    props: ['modelValue', 'type', 'placeholder', 'prefixIcon', 'showPassword'],
    emits: ['update:modelValue'],
  },
  'router-link': { template: '<a><slot /></a>' },
  'el-button': {
    template: '<button :disabled="loading" @click="$emit(\'click\')"><slot /></button>',
    props: ['type', 'loading'],
    emits: ['click'],
  },
}

import RegisterView from '@/views/auth/RegisterView.vue'

describe('RegisterView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockValidate.mockResolvedValue(true)
  })

  function mountRegister() {
    return mount(RegisterView, { global: { stubs: { ...globalStubs } } })
  }

  it('should render the page title and subtitle', () => {
    const wrapper = mountRegister()
    expect(wrapper.find('h2').text()).toBe('AI 题库与错题复习系统')
    expect(wrapper.find('.login-subtitle').text()).toBe('用户注册')
  })

  it('should render all form fields', () => {
    const wrapper = mountRegister()
    const inputs = wrapper.findAll('input')
    expect(inputs.length).toBeGreaterThanOrEqual(4)
    expect(wrapper.html()).toContain('请输入用户名')
    expect(wrapper.html()).toContain('请输入密码')
    expect(wrapper.html()).toContain('请确认密码')
    expect(wrapper.html()).toContain('昵称（可选）')
  })

  it('should render register button', () => {
    const wrapper = mountRegister()
    expect(wrapper.find('button').text()).toContain('注 册')
  })

  it('should render login link', () => {
    const wrapper = mountRegister()
    const footer = wrapper.find('.login-footer')
    expect(footer.text()).toContain('已有账号？')
    expect(footer.text()).toContain('立即登录')
  })

  it('should call register API on successful form submission', async () => {
    mockPost.mockResolvedValue({ data: { data: {} } })
    const wrapper = mountRegister()
    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('newuser')
    await inputs[1].setValue('password123')
    await inputs[2].setValue('password123')
    await inputs[3].setValue('MyNick')
    await wrapper.find('button').trigger('click')
    await flushPromises()
    expect(mockPost).toHaveBeenCalledWith('/auth/register', {
      username: 'newuser',
      password: 'password123',
      nickname: 'MyNick',
    })
  })

  it('should not send nickname when empty', async () => {
    mockPost.mockResolvedValue({ data: { data: {} } })
    const wrapper = mountRegister()
    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('newuser')
    await inputs[1].setValue('password123')
    await inputs[2].setValue('password123')
    await inputs[3].setValue('')
    await wrapper.find('button').trigger('click')
    await flushPromises()
    expect(mockPost).toHaveBeenCalledWith('/auth/register', {
      username: 'newuser',
      password: 'password123',
      nickname: undefined,
    })
  })

  it('should show success message and navigate to login after register', async () => {
    mockPost.mockResolvedValue({ data: { data: {} } })
    const wrapper = mountRegister()
    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('newuser')
    await inputs[1].setValue('password123')
    await inputs[2].setValue('password123')
    await inputs[3].setValue('')
    await wrapper.find('button').trigger('click')
    await flushPromises()
    expect(mockSuccess).toHaveBeenCalledWith('注册成功，请登录')
    expect(mockPush).toHaveBeenCalledWith('/login')
  })

  it('should not call API when validation fails', async () => {
    mockValidate.mockResolvedValue(false)
    const wrapper = mountRegister()
    await wrapper.find('button').trigger('click')
    await flushPromises()
    expect(mockPost).not.toHaveBeenCalled()
  })

  it('should handle register API error gracefully', async () => {
    mockPost.mockRejectedValue(new Error('Username exists'))
    const wrapper = mountRegister()
    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('existinguser')
    await inputs[1].setValue('password123')
    await inputs[2].setValue('password123')
    await inputs[3].setValue('')
    await wrapper.find('button').trigger('click')
    await flushPromises()
    expect(mockPush).not.toHaveBeenCalled()
  })
})