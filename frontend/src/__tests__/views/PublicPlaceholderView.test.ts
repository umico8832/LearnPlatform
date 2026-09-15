import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import PublicPlaceholderView from '@/views/public/PublicPlaceholderView.vue'

const { mockIsAuthenticated } = vi.hoisted(() => ({
  mockIsAuthenticated: vi.fn(),
}))

vi.mock('@/utils/auth', () => ({
  isAuthenticated: () => mockIsAuthenticated(),
}))

const RouterLinkStub = defineComponent({
  props: { to: { type: [String, Object], required: true } },
  template: '<a :data-to="typeof to === \'string\' ? to : JSON.stringify(to)"><slot /></a>',
})

describe('PublicPlaceholderView', () => {
  beforeEach(() => {
    mockIsAuthenticated.mockReturnValue(false)
  })

  it('展示页面名称、开发中状态和返回首页入口', () => {
    const wrapper = mount(PublicPlaceholderView, {
      props: { title: '产品' },
      global: { stubs: { RouterLink: RouterLinkStub } },
    })

    expect(wrapper.get('h1').text()).toBe('产品')
    expect(wrapper.text()).toContain('开发中')
    expect(wrapper.find('[data-to="/"]').text()).toBe('返回首页')
  })

  it('匿名用户的开始学习入口前往注册页', () => {
    const wrapper = mount(PublicPlaceholderView, {
      props: { title: '学习方式' },
      global: { stubs: { RouterLink: RouterLinkStub } },
    })

    expect(wrapper.find('[data-to="/register"]').text()).toBe('开始学习')
  })

  it('已登录用户的开始学习入口前往我的课程', () => {
    mockIsAuthenticated.mockReturnValue(true)
    const wrapper = mount(PublicPlaceholderView, {
      props: { title: '学习方式' },
      global: { stubs: { RouterLink: RouterLinkStub } },
    })

    expect(wrapper.find('[data-to="/my-courses"]').text()).toBe('开始学习')
  })
})
