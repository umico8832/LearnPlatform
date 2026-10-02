interface ReviewSuggestionHandlers {
  onContent: (content: string) => void
  onError: (message: string) => void
}

export async function consumeReviewSuggestionStream(
  response: Response,
  handlers: ReviewSuggestionHandlers,
  signal?: AbortSignal,
) {
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
  const reader = response.body?.getReader()
  if (!reader) throw new Error('无法读取响应流')

  const decoder = new TextDecoder()
  let buffer = ''
  const cancelReader = () => {
    void reader.cancel().catch(() => undefined)
  }
  if (signal?.aborted) cancelReader()
  else signal?.addEventListener('abort', cancelReader, { once: true })
  try {
    while (!signal?.aborted) {
      const { done, value } = await reader.read()
      if (done || signal?.aborted) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      for (const line of lines) {
        if (signal?.aborted || !line.startsWith('data:')) continue
        const payload = line.slice(5).trim()
        if (!payload) continue
        try {
          const data = JSON.parse(payload)
          if (data.message) handlers.onError(data.message)
          else if (data.content) handlers.onContent(data.content)
        } catch {
          continue
        }
      }
    }
  } finally {
    signal?.removeEventListener('abort', cancelReader)
    reader.releaseLock()
  }
}
