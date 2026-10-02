import { onScopeDispose, ref } from 'vue'
import { getAiReviewSuggestionStream } from '@/api/review'
import { getAuthSessionVersion, getToken } from '@/utils/auth'
import { errorMessage, isAbortError } from '@/utils/errors'
import { consumeReviewSuggestionStream } from './reviewSuggestionStream'

/** Keeps a streaming suggestion bound to the page and authenticated session that requested it. */
export function useReviewAiSuggestion(isPageCurrent: () => boolean) {
  const loading = ref(false)
  const content = ref('')
  const error = ref('')
  let controller: AbortController | undefined
  let requestVersion = 0

  function cancel() {
    requestVersion++
    controller?.abort()
    controller = undefined
    loading.value = false
  }

  function clear() {
    cancel()
    content.value = ''
    error.value = ''
  }

  async function request() {
    if (loading.value) return
    const token = getToken()
    content.value = ''
    error.value = ''
    if (!token) {
      error.value = '登录状态已失效，请重新登录后获取复习建议'
      return
    }

    const session = getAuthSessionVersion()
    const version = ++requestVersion
    const nextController = new AbortController()
    controller?.abort()
    controller = nextController
    const current = () =>
      isPageCurrent() &&
      version === requestVersion &&
      session === getAuthSessionVersion() &&
      !nextController.signal.aborted
    loading.value = true
    try {
      const response = await getAiReviewSuggestionStream(token, nextController.signal)
      await consumeReviewSuggestionStream(
        response,
        {
          onContent: (chunk) => {
            if (current()) content.value += chunk
          },
          onError: (message) => {
            if (current()) error.value = message
          },
        },
        nextController.signal,
      )
    } catch (cause) {
      if (!isAbortError(cause) && current()) error.value = errorMessage(cause, 'AI 复习建议暂时无法获取，请重试')
    } finally {
      if (current() && controller === nextController) {
        loading.value = false
        controller = undefined
      }
    }
  }

  onScopeDispose(cancel)
  return { loading, content, error, request, clear, cancel }
}
