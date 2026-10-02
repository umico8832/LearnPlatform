import { afterEach, describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { reactive } from 'vue'
import { removeToken, setToken } from '@/utils/auth'

const { mockUpdateProfile, mockUpdatePassword, mockPush, mockReplace, mockClearLoginInfo } = vi.hoisted(() => ({
  mockUpdateProfile: vi.fn(),
  mockUpdatePassword: vi.fn(),
  mockPush: vi.fn(),
  mockReplace: vi.fn(),
  mockClearLoginInfo: vi.fn(),
}))

let mockValidateResult: boolean | Promise<boolean> = true
const mockProfileValidate = vi.fn().mockResolvedValue(true)
const mockPasswordValidate = vi.fn().mockResolvedValue(true)

vi.mock('@/api/user', () => ({
  updateProfile: (...args: unknown[]) => mockUpdateProfile(...args),
  updatePassword: (...args: unknown[]) => mockUpdatePassword(...args),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush, replace: mockReplace }),
  RouterLink: { template: '<a><slot /></a>' },
}))

const mockUserInfo = reactive({
  id: 1,
  username: 'testuser',
  nickname: 'TestNick',
  role: 'USER',
  createTime: '2025-01-15T10:00:00',
})

vi.mock('@/stores/user', () => ({
  useUserStore: () => ({ userInfo: mockUserInfo, clearLoginInfo: mockClearLoginInfo }),
}))

const globalStubs = {
  'el-row': { template: '<div class="row"><slot /></div>', props: ['gutter'] },
  'el-col': { template: '<div class="col"><slot /></div>', props: ['xs', 'sm'] },
  'el-card': { template: '<div class="card"><slot name="header" /><slot /></div>', props: ['shadow'] },
  'el-avatar': { template: '<div class="avatar"><slot /></div>', props: ['size'] },
  'el-tag': { template: '<span class="tag"><slot /></span>', props: ['size'] },
  'el-icon': { template: '<span class="icon"><slot /></span>' },
  'el-form': {
    template: '<form @submit.prevent><slot /></form>',
    props: ['model', 'rules', 'labelWidth', 'labelPosition'],
    methods: {
      validate: () => Promise.resolve(mockValidateResult),
      resetFields: vi.fn(),
    },
  },
  'el-form-item': { template: '<div class="form-item"><slot /></div>', props: ['label', 'prop'] },
  'el-input': {
    template:
      '<input :value="modelValue" :placeholder="placeholder" :disabled="disabled" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    props: ['modelValue', 'type', 'placeholder', 'disabled', 'showPassword', 'showWordLimit', 'maxlength'],
    emits: ['update:modelValue'],
  },
  'el-button': {
    template: '<button :disabled="loading || disabled" :class="type" @click="$emit(\'click\')"><slot /></button>',
    props: ['type', 'loading', 'disabled', 'plain', 'icon'],
    emits: ['click'],
  },
  User: { template: '<span />' },
  Clock: { template: '<span />' },
}

import ProfileView from '@/views/auth/ProfileView.vue'

describe('ProfileView', () => {
  const mountedWrappers: Array<{ unmount: () => void }> = []

  beforeEach(() => {
    vi.clearAllMocks()
    setToken('profile-test-session')
    mockValidateResult = true
    mockProfileValidate.mockResolvedValue(true)
    mockPasswordValidate.mockResolvedValue(true)
    mockUserInfo.nickname = 'TestNick'
    mockUserInfo.username = 'testuser'
    mockUserInfo.role = 'USER'
    mockUserInfo.createTime = '2025-01-15T10:00:00'
  })

  afterEach(() => {
    mountedWrappers.splice(0).forEach((wrapper) => wrapper.unmount())
    removeToken()
  })

  function mountProfile() {
    const wrapper = mount(ProfileView, {
      global: {
        stubs: { ...globalStubs, ProfileGamification: true },
        mocks: { $router: { push: mockPush } },
      },
    })
    mountedWrappers.push(wrapper)
    return wrapper
  }

  function findButtonByText(wrapper: ReturnType<typeof mountProfile>, text: string) {
    const button = wrapper.findAll('button').find((item) => item.text().includes(text))
    expect(button, `button with text ${text}`).toBeTruthy()
    return button!
  }

  it('should render user avatar with first character of name', () => {
    const wrapper = mountProfile()
    const avatar = wrapper.find('.avatar')
    expect(avatar.text()).toBe('T')
  })

  it('should render user nickname', () => {
    const wrapper = mountProfile()
    const name = wrapper.find('.user-name')
    expect(name.text()).toBe('TestNick')
  })

  it('should render username as fallback when nickname is null', () => {
    mockUserInfo.nickname = ''
    const wrapper = mountProfile()
    const name = wrapper.find('.user-name')
    expect(name.text()).toBe('testuser')
  })

  it('should render role tag for normal user', () => {
    const wrapper = mountProfile()
    const tag = wrapper.find('.tag')
    expect(tag.text()).toBe('普通用户')
  })

  it('should render admin tag for admin user', () => {
    mockUserInfo.role = 'ADMIN'
    const wrapper = mountProfile()
    const tag = wrapper.find('.tag')
    expect(tag.text()).toBe('管理员')
  })

  it('should render registration date', () => {
    const wrapper = mountProfile()
    expect(wrapper.html()).toContain('注册时间')
    expect(wrapper.html()).toContain('2025-01-15')
  })

  it('should render profile section title', () => {
    const wrapper = mountProfile()
    expect(wrapper.html()).toContain('个人信息')
    expect(wrapper.html()).toContain('修改密码')
  })

  it('should render disabled username field', () => {
    const wrapper = mountProfile()
    const disabledInput = wrapper.find('input[disabled]')
    expect(disabledInput.exists()).toBe(true)
    expect((disabledInput.element as HTMLInputElement).value).toBe('testuser')
  })

  it('should call updateProfile API on profile form submission', async () => {
    mockUpdateProfile.mockResolvedValue({ data: { nickname: 'NewNick' } })
    const wrapper = mountProfile()
    const saveBtn = findButtonByText(wrapper, '保存修改')
    await saveBtn.trigger('click')
    await flushPromises()
    expect(mockUpdateProfile).toHaveBeenCalledWith({ nickname: 'TestNick' }, { errorDisplay: 'inline' })
    expect(mockUserInfo.nickname).toBe('NewNick')
  })

  it('should call updatePassword API on password form submission', async () => {
    mockUpdatePassword.mockResolvedValue({ data: null })
    const wrapper = mountProfile()
    // Use the vm to set password form values directly (avoiding stub v-model quirks)
    const vm = wrapper.vm as unknown as Record<string, unknown>
    const passwordForm = vm.passwordForm as { oldPassword: string; newPassword: string; confirmPassword: string }
    passwordForm.oldPassword = 'oldpass'
    passwordForm.newPassword = 'newpass123'
    passwordForm.confirmPassword = 'newpass123'
    await wrapper.vm.$nextTick()
    const changePwdBtn = findButtonByText(wrapper, '修改密码')
    await changePwdBtn.trigger('click')
    await flushPromises()
    expect(mockUpdatePassword).toHaveBeenCalledWith(
      { oldPassword: 'oldpass', newPassword: 'newpass123' },
      { errorDisplay: 'inline' },
    )
    expect(mockClearLoginInfo).toHaveBeenCalledOnce()
    expect(mockReplace).toHaveBeenCalledWith('/login')
  })

  it('should handle updateProfile API error gracefully', async () => {
    mockUpdateProfile.mockRejectedValue(new Error('error'))
    const wrapper = mountProfile()
    const saveBtn = findButtonByText(wrapper, '保存修改')
    await saveBtn.trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('error')
    expect(wrapper.get('[role="alert"] button').text()).toBe('重试')
  })

  it('should handle updatePassword API error gracefully', async () => {
    mockUpdatePassword.mockRejectedValue(new Error('error'))
    const wrapper = mountProfile()
    const changePwdBtn = findButtonByText(wrapper, '修改密码')
    await changePwdBtn.trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('error')
  })

  it('does not apply a late profile response after unmount', async () => {
    let resolveProfile!: (value: { data: { nickname: string } }) => void
    mockUpdateProfile.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveProfile = resolve
        }),
    )
    const wrapper = mountProfile()
    const saveBtn = findButtonByText(wrapper, '保存修改')
    await saveBtn.trigger('click')
    wrapper.unmount()
    resolveProfile({ data: { nickname: '迟到昵称' } })
    await flushPromises()

    expect(mockUserInfo.nickname).toBe('TestNick')
  })

  it('locks both forms before deferred validation resolves, so duplicate and parallel submits do not send requests', async () => {
    let resolveValidation!: (value: boolean) => void
    mockValidateResult = new Promise<boolean>((resolve) => {
      resolveValidation = resolve
    })
    mockUpdateProfile.mockResolvedValue({ data: { nickname: 'NewNick' } })
    const wrapper = mountProfile()
    const vm = wrapper.vm as unknown as {
      handleUpdateProfile: () => Promise<void>
      handleUpdatePassword: () => Promise<void>
    }

    void vm.handleUpdateProfile()
    void vm.handleUpdateProfile()
    void vm.handleUpdatePassword()
    await wrapper.vm.$nextTick()

    expect(findButtonByText(wrapper, '保存修改').attributes('disabled')).toBeDefined()
    expect(findButtonByText(wrapper, '修改密码').attributes('disabled')).toBeDefined()

    resolveValidation(true)
    await flushPromises()

    expect(mockUpdateProfile).toHaveBeenCalledTimes(1)
    expect(mockUpdatePassword).not.toHaveBeenCalled()
  })

  it('does not send a profile request when the component unmounts during validation', async () => {
    let resolveValidation!: (value: boolean) => void
    mockValidateResult = new Promise<boolean>((resolve) => {
      resolveValidation = resolve
    })
    const wrapper = mountProfile()
    const vm = wrapper.vm as unknown as { handleUpdateProfile: () => Promise<void> }

    void vm.handleUpdateProfile()
    wrapper.unmount()
    resolveValidation(true)
    await flushPromises()

    expect(mockUpdateProfile).not.toHaveBeenCalled()
  })

  it('does not send a password request when the auth session changes during validation', async () => {
    let resolveValidation!: (value: boolean) => void
    mockValidateResult = new Promise<boolean>((resolve) => {
      resolveValidation = resolve
    })
    const wrapper = mountProfile()
    const vm = wrapper.vm as unknown as { handleUpdatePassword: () => Promise<void> }

    void vm.handleUpdatePassword()
    setToken('profile-next-session')
    resolveValidation(true)
    await flushPromises()

    expect(mockUpdatePassword).not.toHaveBeenCalled()
  })

  it('clears password fields when the auth session changes', async () => {
    const wrapper = mountProfile()
    const vm = wrapper.vm as unknown as {
      passwordForm: { oldPassword: string; newPassword: string; confirmPassword: string }
    }
    vm.passwordForm.oldPassword = 'oldpass'
    vm.passwordForm.newPassword = 'newpass123'
    vm.passwordForm.confirmPassword = 'newpass123'

    setToken('profile-cleared-session')
    await wrapper.vm.$nextTick()

    expect(vm.passwordForm).toEqual({ oldPassword: '', newPassword: '', confirmPassword: '' })
  })
})
