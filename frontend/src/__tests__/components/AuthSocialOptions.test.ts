import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import AuthSocialOptions from '@/components/auth/AuthSocialOptions.vue'

const { mockGetOAuthProviders } = vi.hoisted(() => ({
  mockGetOAuthProviders: vi.fn(),
}))

vi.mock('@/api/auth', () => ({
  getOAuthProviders: (...args: unknown[]) => mockGetOAuthProviders(...args),
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
}))

describe('AuthSocialOptions', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows only the available Google login method', async () => {
    mockGetOAuthProviders.mockResolvedValue({ data: { google: true } })
    const wrapper = mount(AuthSocialOptions)

    await flushPromises()

    const buttons = wrapper.findAll('button')
    expect(buttons).toHaveLength(1)
    expect(buttons[0].attributes('disabled')).toBeUndefined()
    expect(buttons[0].text()).toBe('使用 Google 登录')
  })

  it('does not advertise unavailable providers', async () => {
    mockGetOAuthProviders.mockResolvedValue({ data: { google: false } })
    const wrapper = mount(AuthSocialOptions)

    await flushPromises()

    expect(wrapper.findAll('button')).toHaveLength(0)
  })

  it('omits optional login methods when discovery fails', async () => {
    mockGetOAuthProviders.mockRejectedValue(new Error('Unavailable'))
    const wrapper = mount(AuthSocialOptions)

    await flushPromises()

    expect(wrapper.findAll('button')).toHaveLength(0)
    wrapper.unmount()
  })
})
