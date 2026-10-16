import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import AuthProviders from '@/components/auth/AuthProviders.vue'

const { mockGetOAuthProviders } = vi.hoisted(() => ({
  mockGetOAuthProviders: vi.fn(),
}))

vi.mock('@/api/auth', () => ({
  getOAuthProviders: (...args: unknown[]) => mockGetOAuthProviders(...args),
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
}))

describe('AuthProviders', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows only the available Google login method', async () => {
    mockGetOAuthProviders.mockResolvedValue({ data: { google: true } })
    const wrapper = mount(AuthProviders)

    await flushPromises()

    const buttons = wrapper.findAll('button')
    expect(buttons).toHaveLength(1)
    expect(buttons[0].attributes('disabled')).toBeUndefined()
    expect(buttons[0].text()).toBe('使用 Google 登录')
  })

  it('does not advertise unavailable providers', async () => {
    mockGetOAuthProviders.mockResolvedValue({ data: { google: false } })
    const wrapper = mount(AuthProviders)

    await flushPromises()

    expect(wrapper.findAll('button')).toHaveLength(0)
  })

  it('omits optional login methods when discovery fails', async () => {
    mockGetOAuthProviders.mockRejectedValue(new Error('Unavailable'))
    const wrapper = mount(AuthProviders)

    await flushPromises()

    expect(wrapper.findAll('button')).toHaveLength(0)
    wrapper.unmount()
  })
  it('shows the preview provider without discovery and disables it during submission', async () => {
    const wrapper = mount(AuthProviders, { props: { preview: true, disabled: true } })
    await flushPromises()
    expect(mockGetOAuthProviders).not.toHaveBeenCalled()
    expect(wrapper.get('button').text()).toBe('使用 Google 登录')
    expect(wrapper.get('button').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })
})
