import type { GlobalSearchResult, SearchItem } from '@/api/search'

export function emptySearchResult(): GlobalSearchResult {
  return { questions: [], courses: [], knowledgePoints: [], totalCount: 0 }
}

export function flattenSearchResults(results: GlobalSearchResult): SearchItem[] {
  return [...results.questions, ...results.courses, ...results.knowledgePoints]
}

export function searchResultCount(results: GlobalSearchResult) {
  return results.questions.length + results.courses.length + results.knowledgePoints.length
}

export function searchResultIndex(results: GlobalSearchResult, group: 'q' | 'c' | 'kp', index: number) {
  if (group === 'c') return results.questions.length + index
  if (group === 'kp') return results.questions.length + results.courses.length + index
  return index
}
