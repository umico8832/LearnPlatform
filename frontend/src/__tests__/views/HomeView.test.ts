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
const VizPlayerStub = defineComponent({
  props: { spec: { type: Object, required: true }, title: { type: String, default: '' } },
  template: '<div class="viz-stub" :data-scene="spec.scene" :data-title="title" />',
})

function mountHome() {
  return mount(HomeView, { global: { stubs: { RouterLink: RouterLinkStub, VizPlayer: VizPlayerStub } } })
}

describe('HomeView', () => {
  beforeEach(() => {
    mockIsAuthenticated.mockReturnValue(false)
  })

  it('walks through a real reviewed question from the chosen wrong option, without giving the answer away', () => {
    const wrapper = mountHome()

    expect(wrapper.get('h1').text()).toContain('选错的那一项')
    expect(wrapper.text()).toContain('2026 年 408 真题 第 3 题')
    expect(wrapper.text()).toContain('已核对来源')
    expect(wrapper.text()).toContain('你的选择')
    expect(wrapper.text()).toContain('把 c 放成了 b 的左孩子')
    expect(wrapper.text()).not.toContain('正确答案')
    expect(wrapper.find('[data-state="right"]').exists()).toBe(false)
    const viz = wrapper.get('.viz-stub')
    expect(viz.attributes('data-scene')).toBe('binary-tree')
  })

  it('sends visitors to sign up and keeps the placeholder pages in the navigation', () => {
    const wrapper = mountHome()

    expect(wrapper.findAll('[data-to="/register"]').length).toBeGreaterThan(0)
    expect(wrapper.find('[data-to="/login"]').exists()).toBe(true)
    for (const path of ['/product', '/learning', '/resources', '/roadmap', '/about']) {
      expect(wrapper.find(`[data-to="${path}"]`).exists()).toBe(true)
    }
  })

  it('points signed-in learners back to their courses', () => {
    mockIsAuthenticated.mockReturnValue(true)
    const wrapper = mountHome()

    expect(wrapper.text()).toContain('回到课程')
    expect(wrapper.find('[data-to="/login"]').exists()).toBe(false)
    expect(wrapper.findAll('[data-to="/my-courses"]').length).toBeGreaterThan(0)
  })
})
