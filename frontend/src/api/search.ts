import request from '@/utils/request'

/** 搜索结果项 */
export interface SearchItem {
  id: number
  title: string
  subtitle: string
  type: 'QUESTION' | 'COURSE' | 'KNOWLEDGE_POINT'
  link: string
  highlight?: string
}

/** 全局搜索结果 */
export interface GlobalSearchResult {
  questions: SearchItem[]
  courses: SearchItem[]
  knowledgePoints: SearchItem[]
  totalCount: number
}

/** 搜索建议（历史 + 热门） */
export interface SearchSuggestions {
  history: string[]
  hotKeywords: string[]
}

/**
 * 全局搜索
 * @param keyword 搜索关键词
 * @param limit 每类结果最大条数（默认 5）
 */
export function globalSearch(keyword: string, limit?: number) {
  return request.get<GlobalSearchResult>('/search', {
    params: { keyword, limit },
  })
}

/**
 * 获取搜索建议（搜索历史 + 热门搜索）
 */
export function getSearchSuggestions() {
  return request.get<SearchSuggestions>('/search/suggestions')
}

/**
 * 清除当前用户全部搜索历史
 */
export function clearSearchHistory() {
  return request.delete<void>('/search/history')
}

/**
 * 删除单条搜索历史
 * @param keyword 要删除的关键词
 */
export function removeSearchHistoryItem(keyword: string) {
  return request.delete<void>('/search/history/item', {
    params: { keyword },
  })
}
