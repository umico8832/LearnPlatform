import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { removeToken, setToken } from '@/utils/auth'

const searchApi = vi.hoisted(() => ({
  clearSearchHistory: vi.fn(),
  getSearchSuggestions: vi.fn(),
  globalSearch: vi.fn(),
  removeSearchHistoryItem: vi.fn(),
}))

vi.mock('@/api/search', () => searchApi)

import { useGlobalSearchState } from '@/components/search/useGlobalSearchState'

const emptySuggestions = { data: { history: [], hotKeywords: [] } }
const result = (title: string) => ({
  data: {
    questions: [{ id: 1, title, subtitle: '题目', type: 'QUESTION', link: '/questions/1' }],
    courses: [],
    knowledgePoints: [],
    totalCount: 1,
  },
})

const mounted: { unmount: () => void }[] = []

function mountState() {
  const wrapper = mount(
    defineComponent({
      setup: () => ({ state: useGlobalSearchState() }),
      template: '<div />',
    }),
  )
  mounted.push(wrapper)
  return wrapper
}

describe('useGlobalSearchState', () => {
  afterEach(() => {
    mounted.splice(0).forEach((wrapper) => wrapper.unmount())
    removeToken()
    vi.useRealTimers()
    vi.clearAllMocks()
  })

  it('clears old results immediately and ignores a late response from a prior query', async () => {
    vi.useFakeTimers()
    searchApi.getSearchSuggestions.mockResolvedValue(emptySuggestions)
    let resolveFirst!: (value: ReturnType<typeof result>) => void
    let resolveSecond!: (value: ReturnType<typeof result>) => void
    searchApi.globalSearch
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveFirst = resolve
          }),
      )
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveSecond = resolve
          }),
      )
    const wrapper = mountState()
    const state = (wrapper.vm as unknown as { state: ReturnType<typeof useGlobalSearchState> }).state

    state.open()
    state.keyword.value = '旧查询'
    state.search()
    vi.advanceTimersByTime(250)
    state.keyword.value = '新查询'
    state.search()
    expect(state.results.value.questions).toHaveLength(0)
    expect(state.loading.value).toBe(true)

    resolveFirst(result('旧题目'))
    await flushPromises()
    expect(state.results.value.questions).toHaveLength(0)

    vi.advanceTimersByTime(250)
    resolveSecond(result('新题目'))
    await flushPromises()
    expect(state.results.value.questions[0]?.title).toBe('新题目')
    expect(state.loading.value).toBe(false)
  })

  it('does not restore results after the dialog closes while a request is pending', async () => {
    vi.useFakeTimers()
    searchApi.getSearchSuggestions.mockResolvedValue(emptySuggestions)
    let resolveSearch!: (value: ReturnType<typeof result>) => void
    searchApi.globalSearch.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveSearch = resolve
        }),
    )
    const wrapper = mountState()
    const state = (wrapper.vm as unknown as { state: ReturnType<typeof useGlobalSearchState> }).state

    state.open()
    state.keyword.value = '课程'
    state.search()
    vi.advanceTimersByTime(250)
    state.close()
    resolveSearch(result('迟到课程'))
    await flushPromises()

    expect(state.keyword.value).toBe('')
    expect(state.results.value.questions).toHaveLength(0)
    expect(state.loading.value).toBe(false)
  })

  it('keeps a failed search distinct from an empty result and retries it', async () => {
    vi.useFakeTimers()
    searchApi.getSearchSuggestions.mockResolvedValue(emptySuggestions)
    searchApi.globalSearch.mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce(result('恢复结果'))
    const wrapper = mountState()
    const state = (wrapper.vm as unknown as { state: ReturnType<typeof useGlobalSearchState> }).state

    state.open()
    state.keyword.value = '图'
    state.search()
    vi.advanceTimersByTime(250)
    await flushPromises()
    expect(state.error.value).toBe('搜索暂时无法完成，请重试')

    state.retrySearch()
    vi.advanceTimersByTime(250)
    await flushPromises()
    expect(state.error.value).toBe('')
    expect(state.results.value.questions[0]?.title).toBe('恢复结果')
  })

  it('retains history when clearing fails and retries the same action', async () => {
    searchApi.getSearchSuggestions.mockResolvedValue({ data: { history: ['图论'], hotKeywords: [] } })
    searchApi.clearSearchHistory.mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce(undefined)
    const wrapper = mountState()
    const state = (wrapper.vm as unknown as { state: ReturnType<typeof useGlobalSearchState> }).state

    state.open()
    await flushPromises()
    await state.clearHistory()
    expect(state.suggestions.value.history).toEqual(['图论'])
    expect(state.historyError.value).toBe('清除搜索历史失败，请重试')

    await state.retryHistory()
    expect(state.suggestions.value.history).toEqual([])
    expect(state.historyError.value).toBe('')
  })

  it('retries a failed item removal without clearing the rest of history', async () => {
    searchApi.getSearchSuggestions.mockResolvedValue({ data: { history: ['图论', '树'], hotKeywords: [] } })
    searchApi.removeSearchHistoryItem.mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce(undefined)
    const wrapper = mountState()
    const state = (wrapper.vm as unknown as { state: ReturnType<typeof useGlobalSearchState> }).state

    state.open()
    await flushPromises()
    await state.removeHistory('图论')
    expect(state.suggestions.value.history).toEqual(['图论', '树'])
    expect(state.historyError.value).toBe('删除搜索历史失败，请重试')

    await state.retryHistory()
    expect(searchApi.removeSearchHistoryItem).toHaveBeenCalledTimes(2)
    expect(searchApi.clearSearchHistory).not.toHaveBeenCalled()
    expect(state.suggestions.value.history).toEqual(['树'])
    expect(state.historyError.value).toBe('')
  })

  it('ignores an old history action after close and reopen, including its loading cleanup', async () => {
    let resolveOldClear!: () => void
    let resolveNewRemove!: () => void
    searchApi.getSearchSuggestions
      .mockResolvedValueOnce({ data: { history: ['旧历史'], hotKeywords: [] } })
      .mockResolvedValueOnce({ data: { history: ['新历史'], hotKeywords: [] } })
    searchApi.clearSearchHistory.mockImplementationOnce(
      () =>
        new Promise<void>((resolve) => {
          resolveOldClear = resolve
        }),
    )
    searchApi.removeSearchHistoryItem.mockImplementationOnce(
      () =>
        new Promise<void>((resolve) => {
          resolveNewRemove = resolve
        }),
    )
    const wrapper = mountState()
    const state = (wrapper.vm as unknown as { state: ReturnType<typeof useGlobalSearchState> }).state

    state.open()
    await flushPromises()
    void state.clearHistory()
    expect(state.historyUpdating.value).toBe(true)

    state.close()
    state.open()
    await flushPromises()
    void state.removeHistory('新历史')
    expect(state.historyUpdating.value).toBe(true)

    resolveOldClear()
    await flushPromises()
    expect(state.suggestions.value.history).toEqual(['新历史'])
    expect(state.historyUpdating.value).toBe(true)

    resolveNewRemove()
    await flushPromises()
    expect(state.suggestions.value.history).toEqual([])
    expect(state.historyUpdating.value).toBe(false)
  })

  it('clears an open dialog without refetching after the token is removed', async () => {
    vi.useFakeTimers()
    searchApi.getSearchSuggestions.mockResolvedValue({ data: { history: ['旧历史'], hotKeywords: ['图论'] } })
    searchApi.globalSearch.mockResolvedValue(result('旧题目'))
    setToken('search-session')
    const wrapper = mountState()
    const state = (wrapper.vm as unknown as { state: ReturnType<typeof useGlobalSearchState> }).state

    state.open()
    await flushPromises()
    expect(searchApi.getSearchSuggestions).toHaveBeenCalledTimes(1)
    state.keyword.value = '题目'
    state.search()
    vi.advanceTimersByTime(250)
    await flushPromises()
    expect(state.results.value.questions).toHaveLength(1)

    removeToken()
    await flushPromises()

    expect(searchApi.getSearchSuggestions).toHaveBeenCalledTimes(1)
    expect(state.results.value.questions).toHaveLength(0)
    expect(state.suggestions.value.history).toEqual([])
  })
})
