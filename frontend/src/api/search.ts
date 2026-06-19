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

/**
 * 全局搜索
 * @param keyword 搜索关键词
 * @param limit 每类结果最大条数（默认 5）
 */
export function globalSearch(keyword: string, limit?: number) {
  return request.get<GlobalSearchResult>('/api/search', {
    params: { keyword, limit },
  })
}