import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import LpCard from '@/components/ui/LpCard.vue'
import LpListItem from '@/components/ui/LpListItem.vue'
import LpPageHeader from '@/components/ui/LpPageHeader.vue'
import LpSkeleton from '@/components/ui/LpSkeleton.vue'
import LpStatePanel from '@/components/ui/LpStatePanel.vue'

describe('UI primitives', () => {
  it('keeps the page header sans by default and marks explicit display headings', () => {
    const defaultHeader = mount(LpPageHeader, { props: { title: '学习计划' } })
    const displayHeader = mount(LpPageHeader, { props: { title: '学习计划', display: true } })

    expect(defaultHeader.find('h1').classes()).not.toContain('is-display')
    expect(displayHeader.find('h1').classes()).toContain('is-display')
  })

  it('renders card slots with the selected semantic container and density', () => {
    const wrapper = mount(LpCard, {
      props: { as: 'article', density: 'compact' },
      slots: { header: '标题', default: '正文', footer: '操作' },
    })

    expect(wrapper.element.tagName).toBe('ARTICLE')
    expect(wrapper.classes()).toContain('is-compact')
    expect(wrapper.text()).toContain('标题')
    expect(wrapper.text()).toContain('正文')
    expect(wrapper.text()).toContain('操作')
  })

  it('uses a native disabled button for an unavailable list item and emits no click', async () => {
    const wrapper = mount(LpListItem, { props: { disabled: true, active: true }, slots: { default: '暂不可用' } })
    const button = wrapper.get('button')

    expect(button.attributes('disabled')).toBeDefined()
    expect(button.attributes('aria-pressed')).toBe('true')
    await button.trigger('click')
    expect(wrapper.emitted('click')).toBeUndefined()
  })

  it('keeps ordinary actions distinct from selectable items', () => {
    const action = mount(LpListItem, { slots: { default: '打开计划' } })
    const toggle = mount(LpListItem, { props: { active: false }, slots: { default: '选择计划' } })
    expect(action.get('button').attributes('aria-pressed')).toBeUndefined()
    expect(toggle.get('button').attributes('aria-pressed')).toBe('false')
  })

  it('uses a router link for navigation items and exposes the current page', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/review', component: { template: '<div />' } }],
    })
    await router.push('/review')
    await router.isReady()
    const wrapper = mount(LpListItem, {
      props: { to: '/review', active: true },
      slots: { leading: '复', default: '复习', trailing: '今天' },
      global: { plugins: [router] },
    })

    expect(wrapper.get('a').attributes('href')).toBe('/review')
    expect(wrapper.get('a').attributes('aria-current')).toBe('page')
    expect(wrapper.text()).toContain('复习')
  })

  it('announces loading without rendering ready, empty, or error content', () => {
    const wrapper = mount(LpStatePanel, {
      props: { state: 'loading', title: '不应显示的标题' },
      slots: { default: '真实数据', actions: '空状态动作' },
    })

    expect(wrapper.get('[role="status"]').attributes('aria-label')).toBe('正在加载')
    expect(wrapper.find('.lp-skeleton').attributes('aria-hidden')).toBe('true')
    expect(wrapper.text()).not.toContain('真实数据')
    expect(wrapper.text()).not.toContain('不应显示的标题')
    expect(wrapper.text()).not.toContain('空状态动作')
  })

  it('keeps failure distinct and emits retry from the in-place action', async () => {
    const wrapper = mount(LpStatePanel, {
      props: { state: 'error', title: '读取失败', description: '请检查网络后重试' },
    })

    expect(wrapper.get('[role="alert"]').text()).toContain('读取失败')
    expect(wrapper.text()).toContain('请检查网络后重试')
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('retry')).toEqual([[]])
  })

  it('gives standalone skeletons an accessible loading label', () => {
    const wrapper = mount(LpSkeleton, { props: { rows: 2, label: '正在读取课程' } })

    expect(wrapper.attributes('role')).toBe('status')
    expect(wrapper.attributes('aria-label')).toBe('正在读取课程')
    expect(wrapper.findAll('.lp-skeleton-line')).toHaveLength(2)
  })

  it('keeps the failure visible and blocks duplicate retries while recovering', async () => {
    const wrapper = mount(LpStatePanel, { props: { state: 'error', retrying: true } })
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    expect(wrapper.get('button').attributes('disabled')).toBeDefined()
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('retry')).toBeUndefined()
  })
})
