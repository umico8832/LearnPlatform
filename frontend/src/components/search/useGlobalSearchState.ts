import { computed, onBeforeUnmount, ref } from 'vue'
import {
  clearSearchHistory,
  getSearchSuggestions,
  globalSearch,
  removeSearchHistoryItem,
  type GlobalSearchResult,
  type SearchSuggestions,
} from '@/api/search'
import { getAuthSessionVersion, isAuthenticated, onAuthSessionChange } from '@/utils/auth'
import { emptySearchResult } from './searchResultModel'

const emptySuggestions = (): SearchSuggestions => ({ history: [], hotKeywords: [] })

/** Owns cancellable search and account-scoped suggestion state for the global search dialog. */
export function useGlobalSearchState() {
  const keyword = ref('')
  const loading = ref(false)
  const error = ref('')
  const results = ref<GlobalSearchResult>(emptySearchResult())
  const suggestions = ref<SearchSuggestions>(emptySuggestions())
  const suggestionsLoading = ref(false)
  const suggestionsError = ref('')
  const historyError = ref('')
  const historyUpdating = ref(false)

  let searchTimer: ReturnType<typeof setTimeout> | undefined
  let searchGeneration = 0
  let suggestionGeneration = 0
  let historyGeneration = 0
  let isOpen = false
  let retryHistoryOperation: (() => Promise<void>) | undefined

  const hasQuery = computed(() => Boolean(keyword.value.trim()))

  const cancelSearch = () => {
    if (searchTimer) clearTimeout(searchTimer)
    searchTimer = undefined
    searchGeneration++
    loading.value = false
  }

  const clearQuery = () => {
    cancelSearch()
    keyword.value = ''
    results.value = emptySearchResult()
    error.value = ''
  }

  const clearSuggestions = () => {
    suggestionGeneration++
    historyGeneration++
    suggestions.value = emptySuggestions()
    suggestionsError.value = ''
    historyError.value = ''
    suggestionsLoading.value = false
    historyUpdating.value = false
    retryHistoryOperation = undefined
  }

  const reset = () => {
    clearQuery()
    clearSuggestions()
  }

  async function loadSuggestions() {
    const generation = ++suggestionGeneration
    const session = getAuthSessionVersion()
    suggestionsLoading.value = true
    suggestionsError.value = ''
    try {
      const response = await getSearchSuggestions()
      if (!isOpen || generation !== suggestionGeneration || session !== getAuthSessionVersion()) return
      suggestions.value = response.data
    } catch {
      if (isOpen && generation === suggestionGeneration && session === getAuthSessionVersion()) {
        suggestionsError.value = '搜索建议暂时无法加载'
      }
    } finally {
      if (generation === suggestionGeneration && session === getAuthSessionVersion()) suggestionsLoading.value = false
    }
  }

  async function runSearch(query: string, generation: number, session: number) {
    try {
      const response = await globalSearch(query, 5)
      if (!isOpen || generation !== searchGeneration || session !== getAuthSessionVersion()) return
      results.value = response.data
    } catch {
      if (isOpen && generation === searchGeneration && session === getAuthSessionVersion()) {
        results.value = emptySearchResult()
        error.value = '搜索暂时无法完成，请重试'
      }
    } finally {
      if (generation === searchGeneration && session === getAuthSessionVersion()) loading.value = false
    }
  }

  function search(value = keyword.value) {
    if (searchTimer) clearTimeout(searchTimer)
    const query = value.trim()
    const generation = ++searchGeneration
    results.value = emptySearchResult()
    error.value = ''
    if (!query) {
      loading.value = false
      return
    }

    loading.value = true
    const session = getAuthSessionVersion()
    searchTimer = setTimeout(() => {
      searchTimer = undefined
      void runSearch(query, generation, session)
    }, 250)
  }

  function retrySearch() {
    search()
  }

  async function clearHistory() {
    if (historyUpdating.value) return
    retryHistoryOperation = undefined
    const session = getAuthSessionVersion()
    const generation = ++historyGeneration
    historyUpdating.value = true
    historyError.value = ''
    try {
      await clearSearchHistory()
      if (isOpen && generation === historyGeneration && session === getAuthSessionVersion())
        suggestions.value.history = []
    } catch {
      if (isOpen && generation === historyGeneration && session === getAuthSessionVersion()) {
        historyError.value = '清除搜索历史失败，请重试'
        retryHistoryOperation = clearHistory
      }
    } finally {
      if (generation === historyGeneration && session === getAuthSessionVersion()) historyUpdating.value = false
    }
  }

  async function removeHistory(keywordToRemove: string) {
    if (historyUpdating.value) return
    retryHistoryOperation = undefined
    const session = getAuthSessionVersion()
    const generation = ++historyGeneration
    historyUpdating.value = true
    historyError.value = ''
    try {
      await removeSearchHistoryItem(keywordToRemove)
      if (isOpen && generation === historyGeneration && session === getAuthSessionVersion()) {
        suggestions.value.history = suggestions.value.history.filter((item) => item !== keywordToRemove)
      }
    } catch {
      if (isOpen && generation === historyGeneration && session === getAuthSessionVersion()) {
        historyError.value = '删除搜索历史失败，请重试'
        retryHistoryOperation = () => removeHistory(keywordToRemove)
      }
    } finally {
      if (generation === historyGeneration && session === getAuthSessionVersion()) historyUpdating.value = false
    }
  }

  function retryHistory() {
    return retryHistoryOperation?.()
  }

  function open() {
    isOpen = true
    reset()
    void loadSuggestions()
  }

  function close() {
    isOpen = false
    reset()
  }

  const unsubscribe = onAuthSessionChange(() => {
    reset()
    if (isOpen && isAuthenticated()) void loadSuggestions()
  })

  onBeforeUnmount(() => {
    isOpen = false
    reset()
    unsubscribe()
  })

  return {
    keyword,
    loading,
    error,
    results,
    suggestions,
    suggestionsLoading,
    suggestionsError,
    historyError,
    historyUpdating,
    hasQuery,
    open,
    close,
    clearQuery,
    search,
    retrySearch,
    clearHistory,
    removeHistory,
    retryHistory,
  }
}
