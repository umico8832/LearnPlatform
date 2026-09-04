interface ReviewSuggestionHandlers {
  onContent: (content: string) => void
  onError: (message: string) => void
}

export async function consumeReviewSuggestionStream(
  response: Response,
  handlers: ReviewSuggestionHandlers,
) {
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
  const reader = response.body?.getReader()
  if (!reader) throw new Error('无法读取响应流')

  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() || ''
    for (const line of lines) {
      if (!line.startsWith('data:')) continue
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
}
