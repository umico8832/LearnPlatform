import { afterEach, describe, expect, it, vi } from 'vitest'
vi.mock('@/utils/request', () => ({ aiService: {} }))
vi.mock('@/utils/auth', () => ({ getToken: () => '' }))
import { streamQuestionAi } from '@/api/ai'

const encoder = new TextEncoder()
afterEach(() => vi.unstubAllGlobals())

describe('AI stream lifecycle', () => {
  it('rejects an incomplete answer when the stream closes without a completion event', async () => {
    const body = new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode('event: content\ndata: {"content":"未完成回答"}\n\n'))
        controller.close()
      },
    })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, body }))
    const onDone = vi.fn()
    await expect(streamQuestionAi('explanation', 1, { onContent: vi.fn(), onDone })).rejects.toThrow('中断')
    expect(onDone).not.toHaveBeenCalled()
    expect(body.locked).toBe(false)
  })

  it('releases a reader on a business error without losing the original error', async () => {
    const cancel = vi.fn().mockRejectedValue(new Error('cancel failed'))
    const body = new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode('event: error\ndata: {"message":"额度已用完"}\n\n'))
      },
      cancel,
    })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, body }))
    await expect(streamQuestionAi('explanation', 1, { onContent: vi.fn() })).rejects.toThrow('额度已用完')
    expect(cancel).toHaveBeenCalledOnce()
    expect(body.locked).toBe(false)
  })

  it('cancels a pending read and ignores later content when the caller leaves', async () => {
    const cancel = vi.fn()
    const body = new ReadableStream({ cancel })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, body }))
    const controller = new AbortController()
    const onContent = vi.fn()
    const result = streamQuestionAi('explanation', 1, { onContent }, controller.signal)
    await Promise.resolve()
    controller.abort()
    await expect(result).rejects.toMatchObject({ name: 'AbortError' })
    expect(cancel).toHaveBeenCalledOnce()
    expect(onContent).not.toHaveBeenCalled()
    expect(body.locked).toBe(false)
  })

  it('finishes on the completion event, ignores later events, and handles split UTF-8 chunks', async () => {
    const bytes = encoder.encode(
      'event: content\ndata: {"content":"树"}\n\nevent: done\ndata: {"source":"fallback"}\n\nevent: content\ndata: {"content":"不应出现"}\n\n',
    )
    const body = new ReadableStream({
      start(controller) {
        const split = bytes.indexOf(0xe6) + 1
        controller.enqueue(bytes.slice(0, split))
        controller.enqueue(bytes.slice(split))
      },
    })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, body }))
    const onContent = vi.fn(),
      onDone = vi.fn()
    await streamQuestionAi('explanation', 1, { onContent, onDone })
    expect(onContent.mock.calls).toEqual([['树']])
    expect(onDone).toHaveBeenCalledWith('fallback')
    expect(body.locked).toBe(false)
  })
})
