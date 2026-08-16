import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: {
    get: vi.fn(),
    delete: vi.fn(),
  },
}))

import request from '@/utils/request'
import { globalSearch, getSearchSuggestions, clearSearchHistory, removeSearchHistoryItem } from '@/api/search'

const mockedRequest = vi.mocked(request)

describe('Search API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('全局搜索不应重复拼接 /api 前缀', async () => {
    mockedRequest.get.mockResolvedValue({ questions: [], courses: [], knowledgePoints: [], totalCount: 0 })

    await globalSearch('Java', 5)

    expect(mockedRequest.get).toHaveBeenCalledWith('/search', {
      params: { keyword: 'Java', limit: 5 },
    })
  })

  it('搜索建议和历史操作使用统一的相对 API 路径', async () => {
    mockedRequest.get.mockResolvedValue({ history: [], hotKeywords: [] })
    mockedRequest.delete.mockResolvedValue(undefined)

    await getSearchSuggestions()
    await clearSearchHistory()
    await removeSearchHistoryItem('SQL')

    expect(mockedRequest.get).toHaveBeenCalledWith('/search/suggestions')
    expect(mockedRequest.delete).toHaveBeenCalledWith('/search/history')
    expect(mockedRequest.delete).toHaveBeenCalledWith('/search/history/item', {
      params: { keyword: 'SQL' },
    })
  })
})
