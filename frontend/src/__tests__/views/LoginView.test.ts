import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'

const { mockPost, mockPush, mockSetLoginInfo, mockSuccess, mockValidate } = vi.hoisted(() => ({
  mockPost: vi.fn(),
  mockPush: vi.fn(),
  mockSetLoginInfo: vi.fn(),
  mockSuccess: vi.fn(),
  mockValidate: vi.fn().mockResolvedValue(true),
}))

vi.mock('@/utils/request', () => ({
  default: { post: (...args: unknown[]) => mockPost(...args) },
}))

const mockRoute: Record<string, unknown> = { query: {} }
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
  useRoute: () => mockRoute,
  RouterLink: { template: '<a><slot /></a>' },
}))

vi.mock('@/stores/user', () => ({
  useUserStore: () => ({ setLoginInfo: mockSetLoginInfo }),
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

import LoginView from '@/views/auth/LoginView.vue'

describe('LoginView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRoute.query = {}
    mockValidate.mockResolvedValue(true)
  })

  function mountLogin() {
    return mount(LoginView, { global: { stubs: { ...globalStubs } } })
  }

  it('should render the page title and subtitle', () => {
    const wrapper = mountLogin()
    expect(wrapper.find('h2').text()).toBe('AI 题库与错题复习系统')
    expect(wrapper.find('.login-subtitle').text()).toBe('用户登录')
  })

  it('should render username and password inputs', () => {
    const wrapper = mountLogin()
    const inputs = wrapper.findAll('input')
    expect(inputs.length).toBeGreaterThanOrEqual(2)
    // Verify the component rendered with correct form structure
    expect(wrapper.html()).toContain('请输入用户名')
    expect(wrapper.html()).toContain('请输入密码')
  })

  it('should render a login button', () => {
    const wrapper = mountLogin()
    expect(wrapper.find('button').text()).toContain('登 录')
  })

  it('should render a register link', () => {
    const wrapper = mountLogin()
    const footer = wrapper.find('.login-footer')
    expect(footer.text()).toContain('还没有账号？')
    expect(footer.text()).toContain('立即注册')
  })

  it('should update form values when inputs change', async () => {
    const wrapper = mountLogin()
    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('testuser')
    await inputs[1].setValue('password123')
    expect((inputs[0].element as HTMLInputElement).value).toBe('testuser')
    expect((inputs[1].element as HTMLInputElement).value).toBe('password123')
  })

  it('should call login API on successful form submission', async () => {
    mockPost.mockResolvedValue({
      data: { data: { token: 'jwt-token-123', user: { id: 1, username: 'testuser', role: 'USER' } } },
    })
    const wrapper = mountLogin()
    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('testuser')
    await inputs[1].setValue('password123')
    await nextTick()
    await wrapper.find('button').trigger('click')
    await flushPromises()
    expect(mockPost).toHaveBeenCalledWith('/auth/login', { username: 'testuser', password: 'password123' })
  })

  it('should show success message and navigate after login', async () => {
    mockPost.mockResolvedValue({
      data: { data: { token: 'jwt-token-123', user: { id: 1, username: 'testuser', role: 'USER' } } },
    })
    const wrapper = mountLogin()
    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('testuser')
    await inputs[1].setValue('password123')
    await wrapper.find('button').trigger('click')
    await flushPromises()
    expect(mockSetLoginInfo).toHaveBeenCalledWith('jwt-token-123', { id: 1, username: 'testuser', role: 'USER' })
    expect(mockSuccess).toHaveBeenCalledWith('登录成功')
    expect(mockPush).toHaveBeenCalledWith('/')
  })

  it('should redirect to query param path after login', async () => {
    mockRoute.query = { redirect: '/practice' }
    mockPost.mockResolvedValue({
      data: { data: { token: 'jwt-token-123', user: { id: 1, username: 'testuser', role: 'USER' } } },
    })
    const wrapper = mountLogin()
    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('testuser')
    await inputs[1].setValue('password123')
    await wrapper.find('button').trigger('click')
    await flushPromises()
    expect(mockPush).toHaveBeenCalledWith('/practice')
  })

  it('should not call API when validation fails', async () => {
    mockValidate.mockResolvedValue(false)
    const wrapper = mountLogin()
    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('testuser')
    await inputs[1].setValue('password123')
    await wrapper.find('button').trigger('click')
    await flushPromises()
    expect(mockPost).not.toHaveBeenCalled()
  })

  it('should handle login API error gracefully', async () => {
    mockPost.mockRejectedValue(new Error('Network error'))
    const wrapper = mountLogin()
    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('testuser')
    await inputs[1].setValue('wrongpassword')
    await wrapper.find('button').trigger('click')
    await flushPromises()
    expect(mockSetLoginInfo).not.toHaveBeenCalled()
  })
})