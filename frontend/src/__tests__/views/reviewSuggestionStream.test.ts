import { describe, expect, it, vi } from 'vitest'
import { consumeReviewSuggestionStream } from '@/views/practice/reviewSuggestionStream'

function streamResponse(chunks: string[]) {
  const encoder = new TextEncoder()
  return new Response(
    new ReadableStream({
      start(controller) {
        for (const chunk of chunks) controller.enqueue(encoder.encode(chunk))
        controller.close()
      },
    }),
  )
}

describe('consumeReviewSuggestionStream', () => {
  it('parses content and server errors without treating them as rendered HTML', async () => {
    const onContent = vi.fn()
    const onError = vi.fn()

    await consumeReviewSuggestionStream(
      streamResponse(['data: {"content":"先复习栈"}\n', 'data: {"message":"稍后重试"}\n']),
      { onContent, onError },
    )

    expect(onContent).toHaveBeenCalledWith('先复习栈')
    expect(onError).toHaveBeenCalledWith('稍后重试')
  })

  it('cancels reading and does not publish a chunk after the request is aborted', async () => {
    const controller = new AbortController()
    const onContent = vi.fn()
    const response = new Response(
      new ReadableStream({
        start(stream) {
          controller.abort()
          stream.enqueue(new TextEncoder().encode('data: {"content":"过期内容"}\n'))
          stream.close()
        },
      }),
    )

    await consumeReviewSuggestionStream(response, { onContent, onError: vi.fn() }, controller.signal)

    expect(onContent).not.toHaveBeenCalled()
    expect(response.body?.locked).toBe(false)
  })

  it('settles a pending read when cancellation itself rejects', async () => {
    const controller = new AbortController()
    const cancel = vi.fn().mockRejectedValue(new Error('Connection already closed'))
    const onContent = vi.fn()
    const response = new Response(new ReadableStream({ cancel }))
    const reading = consumeReviewSuggestionStream(response, { onContent, onError: vi.fn() }, controller.signal)

    controller.abort()
    await reading

    expect(cancel).toHaveBeenCalledOnce()
    expect(onContent).not.toHaveBeenCalled()
    expect(response.body?.locked).toBe(false)
  })
})
