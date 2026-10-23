import { afterEach, expect, it, vi } from 'vitest'
import { streamAsk } from '@/api/questionAsk'

vi.mock('@/utils/auth', () => ({ getToken: () => null }))
vi.mock('@/utils/request', () => ({ default: {} }))
afterEach(() => vi.unstubAllGlobals())

it('只把 content 事件交给正文，状态与心跳不能完成回答', async () => {
  const done = { requestId: 'request', questionRemaining: 4, dailyRemaining: null, suggestion: null }
  const events = 'event:status\ndata:{"state":"processing"}\n\n' +
    'event:heartbeat\ndata:{}\n\nevent:content\ndata:{"content":"已审核正文"}\n\n' +
    `event:done\ndata:${JSON.stringify(done)}\n\n`
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(events, {
    headers: { 'Content-Type': 'text/event-stream' },
  })))
  const onContent = vi.fn()
  await expect(streamAsk(1, { requestId: 'request', message: '概念' }, { onContent })).resolves.toEqual(done)
  expect(onContent.mock.calls).toEqual([['已审核正文']])
})

it('只有状态与心跳后断流仍属于失败', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(
    'event:status\ndata:{"state":"processing"}\n\nevent:heartbeat\ndata:{}\n\n',
    { headers: { 'Content-Type': 'text/event-stream' } },
  )))
  const onContent = vi.fn()
  await expect(streamAsk(1, { requestId: 'request', message: '概念' }, { onContent })).rejects.toMatchObject({ code: 1012 })
  expect(onContent).not.toHaveBeenCalled()
})
