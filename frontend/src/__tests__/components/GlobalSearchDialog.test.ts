import { createMemoryHistory, createRouter } from 'vue-router'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

const searchApi = vi.hoisted(() => ({
  clearSearchHistory: vi.fn(),
  getSearchSuggestions: vi.fn(),
  globalSearch: vi.fn(),
  removeSearchHistoryItem: vi.fn(),
}))

vi.mock('@/api/search', () => searchApi)

import GlobalSearchDialog from '@/components/GlobalSearchDialog.vue'

const router = createRouter({
  history: createMemoryHistory(),
  routes: [{ path: '/questions/1', component: { template: '<div />' } }],
})
const dialogStub = {
  template: '<div class="dialog-stub"><slot name="header" title-id="test-dialog-title" /><slot /></div>',
}
const iconStub = { template: '<i><slot /></i>' }

describe('GlobalSearchDialog', () => {
  afterEach(() => {
    vi.useRealTimers()
    vi.clearAllMocks()
  })

  it('uses combobox and listbox semantics and lets ArrowDown select the first result', async () => {
    vi.useFakeTimers()
    searchApi.getSearchSuggestions.mockResolvedValue({ data: { history: [], hotKeywords: [] } })
    searchApi.globalSearch.mockResolvedValue({
      data: {
        questions: [{ id: 1, title: '二叉树', subtitle: '题目', type: 'QUESTION', link: '/questions/1' }],
        courses: [],
        knowledgePoints: [],
        totalCount: 1,
      },
    })
    const wrapper = mount(GlobalSearchDialog, {
      global: { plugins: [router], stubs: { ElDialog: dialogStub, ElIcon: iconStub } },
    })
    ;(wrapper.vm as unknown as { open: () => void }).open()
    const input = wrapper.get('input')

    expect(wrapper.text()).toContain('全局搜索')
    expect(input.attributes('role')).toBe('combobox')
    await input.setValue('二叉')
    vi.advanceTimersByTime(250)
    await flushPromises()
    await input.trigger('keydown', { key: 'ArrowDown' })

    expect(wrapper.get('[role="listbox"]')).toBeTruthy()
    expect(wrapper.get('[role="option"]').attributes('aria-selected')).toBe('true')
    expect(input.attributes('aria-activedescendant')).toBe('global-search-option-0')
  })

  it('keeps the combobox expanded while its queried result panel is loading or empty', async () => {
    vi.useFakeTimers()
    searchApi.getSearchSuggestions.mockResolvedValue({ data: { history: [], hotKeywords: [] } })
    searchApi.globalSearch.mockResolvedValue({
      data: { questions: [], courses: [], knowledgePoints: [], totalCount: 0 },
    })
    const wrapper = mount(GlobalSearchDialog, {
      global: { plugins: [router], stubs: { ElDialog: dialogStub, ElIcon: iconStub } },
    })
    ;(wrapper.vm as unknown as { open: () => void }).open()
    const input = wrapper.get('input')

    await input.setValue('不存在')
    expect(input.attributes('aria-expanded')).toBe('true')
    expect(wrapper.get('[role="listbox"]')).toBeTruthy()

    vi.advanceTimersByTime(250)
    await flushPromises()
    expect(input.attributes('aria-expanded')).toBe('true')
    expect(wrapper.get('[role="listbox"]')).toBeTruthy()
    expect(wrapper.text()).toContain('未找到匹配结果')
  })
})
