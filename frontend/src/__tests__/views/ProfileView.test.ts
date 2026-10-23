import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { removeToken, setToken } from '@/utils/auth'

const { updateProfile, updatePassword, replace, clearLoginInfo } = vi.hoisted(() => ({
  updateProfile: vi.fn(),
  updatePassword: vi.fn(),
  replace: vi.fn(),
  clearLoginInfo: vi.fn(),
}))
vi.mock('@/api/user', () => ({ updateProfile, updatePassword }))
vi.mock('vue-router', () => ({ useRouter: () => ({ replace }) }))
const user = reactive({
  id: 1,
  username: 'testuser',
  nickname: 'TestNick',
  role: 'USER',
  createTime: '2025-01-15T10:00:00',
})
vi.mock('@/stores/user', () => ({ useUserStore: () => ({ userInfo: user, clearLoginInfo }) }))
import ProfileView from '@/views/auth/ProfileView.vue'

function page() {
  return mount(ProfileView, {
    global: {
      stubs: {
        ProfileGamification: true,
        RouterLink: { template: '<a><slot/></a>' },
        LtLinkButton: { template: '<a><slot/></a>' },
        LtCard: { template: '<section><slot name="title"/><slot/></section>' },
        LtButton: {
          props: ['disabled', 'type'],
          template: '<button :disabled="disabled" :type="type"><slot/></button>',
        },
      },
    },
  })
}
function profileForm(wrapper: ReturnType<typeof page>) {
  return wrapper.findAll('form')[0]!
}
function passwordForm(wrapper: ReturnType<typeof page>) {
  return wrapper.findAll('form')[1]!
}
function vm(wrapper: ReturnType<typeof page>) {
  return wrapper.vm as unknown as {
    profileForm: { nickname: string }
    passwordForm: { oldPassword: string; newPassword: string; confirmPassword: string }
    handleUpdateProfile: () => Promise<void>
    handleUpdatePassword: () => Promise<void>
  }
}

describe('ProfileView', () => {
  const wrappers: ReturnType<typeof page>[] = []
  beforeEach(() => {
    vi.clearAllMocks()
    setToken('profile-test-session')
    user.nickname = 'TestNick'
    user.username = 'testuser'
    user.role = 'USER'
    user.createTime = '2025-01-15T10:00:00'
  })
  afterEach(() => {
    wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
    removeToken()
  })
  function mountProfile() {
    const wrapper = page()
    wrappers.push(wrapper)
    return wrapper
  }

  it('renders avatar from the first character of the display name', () =>
    expect(mountProfile().get('.avatar').text()).toBe('T'))
  it('renders nickname', () => expect(mountProfile().get('.user-name').text()).toBe('TestNick'))
  it('falls back to username when nickname is empty', () => {
    user.nickname = ''
    expect(mountProfile().get('.user-name').text()).toBe('testuser')
  })
  it('renders the normal-user role', () => expect(mountProfile().get('.tag').text()).toBe('普通用户'))
  it('renders the administrator role', () => {
    user.role = 'ADMIN'
    expect(mountProfile().get('.tag').text()).toBe('管理员')
  })
  it('renders registration date', () => {
    const wrapper = mountProfile()
    expect(wrapper.text()).toContain('注册时间')
    expect(wrapper.text()).toContain('2025-01-15')
  })
  it('renders both profile sections', () => {
    const wrapper = mountProfile()
    expect(wrapper.text()).toContain('个人信息')
    expect(wrapper.text()).toContain('修改密码')
  })
  it('renders username as a disabled field', () => {
    const input = mountProfile().get<HTMLInputElement>('input[disabled]')
    expect(input.element.value).toBe('testuser')
  })
  it('keeps each password field revealable with an accessible toggle', async () => {
    const wrapper = mountProfile()
    const toggles = wrapper.findAll('button').filter((button) => button.attributes('aria-label') === '显示密码')
    expect(toggles).toHaveLength(3)
    const password = wrapper.get<HTMLInputElement>('input[autocomplete="current-password"]')
    expect(password.attributes('type')).toBe('password')
    await toggles[0]!.trigger('click')
    expect(password.attributes('type')).toBe('text')
    expect(toggles[0]!.attributes('aria-label')).toBe('隐藏密码')
  })

  it('saves profile through the existing API', async () => {
    updateProfile.mockResolvedValue({ data: { nickname: 'NewNick' } })
    const wrapper = mountProfile()
    await profileForm(wrapper).trigger('submit')
    await flushPromises()
    expect(updateProfile).toHaveBeenCalledWith({ nickname: 'TestNick' }, { errorDisplay: 'inline' })
    expect(user.nickname).toBe('NewNick')
  })
  it('changes password and returns to login', async () => {
    updatePassword.mockResolvedValue({ data: null })
    const wrapper = mountProfile()
    Object.assign(vm(wrapper).passwordForm, {
      oldPassword: 'oldpass',
      newPassword: 'newpass123',
      confirmPassword: 'newpass123',
    })
    await passwordForm(wrapper).trigger('submit')
    await flushPromises()
    expect(updatePassword).toHaveBeenCalledWith(
      { oldPassword: 'oldpass', newPassword: 'newpass123' },
      { errorDisplay: 'inline' },
    )
    expect(clearLoginInfo).toHaveBeenCalledOnce()
    expect(replace).toHaveBeenCalledWith('/login')
  })
  it('keeps profile API failures visible with retry', async () => {
    updateProfile.mockRejectedValue(new Error('error'))
    const wrapper = mountProfile()
    await profileForm(wrapper).trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('error')
    expect(wrapper.get('[role="alert"] button').text()).toBe('重试')
  })
  it('keeps password API failures visible', async () => {
    updatePassword.mockRejectedValue(new Error('error'))
    const wrapper = mountProfile()
    Object.assign(vm(wrapper).passwordForm, {
      oldPassword: 'oldpass',
      newPassword: 'newpass123',
      confirmPassword: 'newpass123',
    })
    await passwordForm(wrapper).trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('error')
  })
  it('keeps native nickname and password validation visible without sending requests', async () => {
    const wrapper = mountProfile()
    vm(wrapper).profileForm.nickname = ''
    await profileForm(wrapper).trigger('submit')
    await flushPromises()
    expect(updateProfile).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toContain('请输入昵称')
    Object.assign(vm(wrapper).passwordForm, {
      oldPassword: 'oldpass',
      newPassword: 'short',
      confirmPassword: 'different',
    })
    await passwordForm(wrapper).trigger('submit')
    await flushPromises()
    expect(updatePassword).not.toHaveBeenCalled()
    expect(wrapper.findAll('[role="alert"]').at(-1)!.text()).toMatch(/密码长度|两次输入/)
  })

  it('does not apply a late profile response after unmount', async () => {
    let resolve!: (value: { data: { nickname: string } }) => void
    updateProfile.mockImplementationOnce(
      () =>
        new Promise((yes) => {
          resolve = yes
        }),
    )
    const wrapper = mountProfile()
    const pending = vm(wrapper).handleUpdateProfile()
    await flushPromises()
    wrapper.unmount()
    resolve({ data: { nickname: '迟到昵称' } })
    await pending
    expect(user.nickname).toBe('TestNick')
  })
  it('locks both forms during native validation and allows only the first submit', async () => {
    let resolveProfile!: (value: { data: { nickname: string } }) => void
    updateProfile.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveProfile = resolve
        }),
    )
    const wrapper = mountProfile()
    void vm(wrapper).handleUpdateProfile()
    void vm(wrapper).handleUpdateProfile()
    void vm(wrapper).handleUpdatePassword()
    await wrapper.vm.$nextTick()
    expect(
      wrapper
        .findAll('button')
        .filter((button) => ['保存修改', '修改密码'].includes(button.text()))
        .every((button) => button.attributes('disabled') !== undefined),
    ).toBe(true)
    resolveProfile({ data: { nickname: 'NewNick' } })
    await flushPromises()
    expect(updateProfile).toHaveBeenCalledTimes(1)
    expect(updatePassword).not.toHaveBeenCalled()
  })
  it('does not send a profile request when unmounted during native validation', async () => {
    const wrapper = mountProfile()
    const pending = vm(wrapper).handleUpdateProfile()
    wrapper.unmount()
    await pending
    expect(updateProfile).not.toHaveBeenCalled()
  })
  it('does not send a password request when session changes during native validation', async () => {
    const wrapper = mountProfile()
    Object.assign(vm(wrapper).passwordForm, {
      oldPassword: 'oldpass',
      newPassword: 'newpass123',
      confirmPassword: 'newpass123',
    })
    const pending = vm(wrapper).handleUpdatePassword()
    setToken('profile-next-session')
    await pending
    expect(updatePassword).not.toHaveBeenCalled()
  })
  it('clears password fields on auth session change', async () => {
    const wrapper = mountProfile()
    Object.assign(vm(wrapper).passwordForm, {
      oldPassword: 'oldpass',
      newPassword: 'newpass123',
      confirmPassword: 'newpass123',
    })
    setToken('profile-cleared-session')
    await wrapper.vm.$nextTick()
    expect(vm(wrapper).passwordForm).toEqual({ oldPassword: '', newPassword: '', confirmPassword: '' })
  })
})
