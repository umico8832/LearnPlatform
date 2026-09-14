import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import HomeView from '@/views/home/HomeView.vue'

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

function mountHome() {
  return mount(HomeView, { global: { stubs: { RouterLink: RouterLinkStub } } })
}

describe('HomeView', () => {
  beforeEach(() => {
    mockIsAuthenticated.mockReturnValue(false)
  })

  it('呈现 AI 相伴的通用学习空间定位，并如实标注当前课程案例', () => {
    const wrapper = mountHome()

    expect(wrapper.get('h1').text()).toContain('难懂的知识')
    expect(wrapper.text()).toContain('AI 相伴的学习空间')
    expect(wrapper.text()).toContain('当前可体验的完整课程是 408 数据结构')
    expect(wrapper.findAll('[data-to="/register"]').length).toBeGreaterThan(0)
  })

  it('允许学习者通过调整重叠数量理解并集计数', async () => {
    const wrapper = mountHome()
    const overlapInput = wrapper.get('[data-testid="overlap-input"]')

    expect(wrapper.get('[data-testid="union-formula"]').text()).toContain('18 + 14 − 6 = 26')

    await overlapInput.setValue('2')

    expect(wrapper.get('[data-testid="union-formula"]').text()).toContain('18 + 14 − 2 = 30')
    expect(wrapper.text()).toContain('重叠的 2 人被数了两次')
  })

  it('已登录时把主要操作指向我的课程', () => {
    mockIsAuthenticated.mockReturnValue(true)
    const wrapper = mountHome()

    expect(wrapper.text()).toContain('回到我的课程')
    expect(wrapper.findAll('[data-to="/my-courses"]').length).toBeGreaterThan(0)
  })
})
