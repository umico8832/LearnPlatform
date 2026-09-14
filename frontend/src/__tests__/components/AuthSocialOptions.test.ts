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

  it('enables Google when configured and keeps Facebook and Apple disabled', async () => {
    mockGetOAuthProviders.mockResolvedValue({ data: { google: true } })
    const wrapper = mount(AuthSocialOptions)

    await flushPromises()

    const buttons = wrapper.findAll('button')
    expect(buttons).toHaveLength(3)
    expect(buttons[0].attributes('disabled')).toBeUndefined()
    expect(buttons[1].attributes('disabled')).toBeDefined()
    expect(buttons[2].attributes('disabled')).toBeDefined()
  })

  it('keeps every provider disabled when Google is not configured', async () => {
    mockGetOAuthProviders.mockResolvedValue({ data: { google: false } })
    const wrapper = mount(AuthSocialOptions)

    await flushPromises()

    expect(wrapper.findAll('button').every((button) => button.attributes('disabled') !== undefined)).toBe(true)
  })

  it('keeps provider buttons disabled when discovery fails', async () => {
    mockGetOAuthProviders.mockRejectedValue(new Error('Unavailable'))
    const wrapper = mount(AuthSocialOptions)

    await flushPromises()

    expect(wrapper.findAll('button')).toHaveLength(3)
    expect(wrapper.findAll('button').every((button) => button.attributes('disabled') !== undefined)).toBe(true)
    wrapper.unmount()
  })
})
