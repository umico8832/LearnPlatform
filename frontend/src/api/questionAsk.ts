import request from '@/utils/request'
import { getToken } from '@/utils/auth'
import type { ApiResponse } from '@/types/api'
import type { TutorNext } from './questionTutor'

// 题目追问（Phase 27）：契约见 docs/reference/api/question-tutor.md 的“题目追问”

export type AskRecordStatus = 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED'

/** Only a hint or the misconception player; never advances a level or plays by itself. */
export type AskSuggestion = Pick<TutorNext, 'action' | 'reason' | 'reasonSource'>

export interface AskRecord {
  requestId: string
  attemptId: number
  message: string
  /** Markdown; only a succeeded record has a body. */
  content: string | null
  status: AskRecordStatus
  errorCode: number | null
  createdAt: string
  suggestion: AskSuggestion | null
}

export interface AskStatus {
  /** Configured and switched on; says nothing about remaining quota. */
  available: boolean
  reason: 'DISABLED' | 'UNCONFIGURED' | null
  questionRemaining: number
  /** null: the shared daily AI quota is unlimited. */
  dailyRemaining: number | null
  records: AskRecord[]
}

export interface AskDone {
  requestId: string
  questionRemaining: number
  dailyRemaining: number | null
  suggestion: AskSuggestion | null
}

/** A business error from the ask endpoints, before or during the stream. */
export class AskError extends Error {
  constructor(
    readonly code: number,
    message: string,
  ) {
    super(message)
  }
}

const inline = { errorDisplay: 'inline' as const }

export function getAskStatus(attemptId: number) {
  return request.get<unknown, ApiResponse<AskStatus>>(`/understanding/attempts/${attemptId}/ask`, inline)
}

export function stopAsk(attemptId: number, requestId: string) {
  return request.delete<unknown, ApiResponse<{ stopped: true }>>(
    `/understanding/attempts/${attemptId}/ask/${requestId}`,
    inline,
  )
}

interface AskHandlers {
  onContent: (text: string) => void
}

/**
 * Ask one question. The server checks the whole answer before sending any of it, so the
 * first `content` may arrive only after the model has finished. Resolves with `done`;
 * rejects with AskError for a business error, or an AbortError when the signal stops it.
 */
export async function streamAsk(
  attemptId: number,
  body: { requestId: string; message: string },
  handlers: AskHandlers,
  signal?: AbortSignal,
): Promise<AskDone> {
  const token = getToken()
  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
  const response = await fetch(`${baseUrl}/understanding/attempts/${attemptId}/ask/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
    signal,
  })
  if (response.status === 401) throw new AskError(401, '登录已过期，请重新登录')
  // Validation, access and quota errors arrive before the stream as the usual envelope.
  if (!response.ok || response.headers.get('Content-Type')?.includes('application/json')) {
    const envelope = (await response.json().catch(() => null)) as { code?: number; message?: string } | null
    throw new AskError(envelope?.code ?? response.status, envelope?.message || `追问请求失败（${response.status}）`)
  }
  if (!response.body) throw new AskError(1012, '浏览器不支持流式响应')

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  try {
    while (true) {
      const { done, value } = await reader.read()
      buffer += decoder.decode(value, { stream: !done })
      const blocks = buffer.split(/\r?\n\r?\n/)
      buffer = done ? '' : (blocks.pop() ?? '')
      for (const block of blocks) {
        const event = parseEvent(block)
        if (!event) continue
        if (event.name === 'status' || event.name === 'heartbeat') continue
        if (event.name === 'content') handlers.onContent(String((event.data as { content?: string }).content ?? ''))
        else if (event.name === 'done') return event.data as AskDone
        else if (event.name === 'error') {
          const error = event.data as { code?: number; message?: string }
          throw new AskError(error.code ?? 1012, error.message || '这次没有得到回答')
        }
      }
      if (done) throw new AskError(1012, '回答中断了，请重试')
    }
  } finally {
    reader.cancel().catch(() => undefined)
  }
}

function parseEvent(block: string): { name: string; data: unknown } | null {
  let name = 'message'
  const data: string[] = []
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith('event:')) name = line.slice(6).trim()
    else if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''))
  }
  if (!data.length) return null
  try {
    return { name, data: JSON.parse(data.join('\n')) }
  } catch {
    return null
  }
}
