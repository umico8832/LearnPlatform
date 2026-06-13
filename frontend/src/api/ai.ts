import { aiService } from '@/utils/request'
import { getToken } from '@/utils/auth'
import type { ApiResponse } from '@/types/api'

export interface AiResponse {
  content: string
  source: string
}

/** AI 生成题目解析 */
export function getExplanation(questionId: number) {
  return aiService.post<any, ApiResponse<AiResponse>>('/ai/explanation', { questionId })
}

/** AI 生成变式题 */
export function getVariant(questionId: number) {
  return aiService.post<any, ApiResponse<AiResponse>>('/ai/variant', { questionId })
}

/** AI 生成复习建议 */
export function getReviewSuggestion(courseId?: number) {
  return aiService.post<any, ApiResponse<AiResponse>>('/ai/review-suggestion', courseId ? { courseId } : {})
}

/** AI 生成知识点总结 */
export function getSummary(knowledgePointId: number) {
  return aiService.post<any, ApiResponse<AiResponse>>('/ai/summary', { knowledgePointId })
}

export type AiStreamType = 'explanation' | 'variant'

interface StreamHandlers {
  onContent: (content: string) => void
  onDone?: (source: string) => void
}

/** 使用 fetch 读取 SSE，支持 POST 请求体和 JWT 请求头。 */
export async function streamQuestionAi(
  type: AiStreamType,
  questionId: number,
  handlers: StreamHandlers,
  signal?: AbortSignal,
) {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
  const token = getToken()
  const response = await fetch(`${baseUrl}/ai/${type}/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ questionId }),
    signal,
  })

  if (!response.ok) {
    throw new Error(response.status === 401 ? '登录已过期，请重新登录' : `AI 服务请求失败 (${response.status})`)
  }
  if (!response.body) {
    throw new Error('浏览器不支持流式响应')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    buffer += decoder.decode(value, { stream: !done })
    const events = buffer.split(/\r?\n\r?\n/)
    buffer = events.pop() || ''

    for (const eventBlock of events) {
      handleStreamEvent(eventBlock, handlers)
    }
    if (done) break
  }

  if (buffer.trim()) {
    handleStreamEvent(buffer, handlers)
  }
}

function handleStreamEvent(eventBlock: string, handlers: StreamHandlers) {
  let eventName = 'message'
  const dataLines: string[] = []

  for (const line of eventBlock.split(/\r?\n/)) {
    if (line.startsWith('event:')) eventName = line.slice(6).trim()
    if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart())
  }

  if (dataLines.length === 0) return

  const data = JSON.parse(dataLines.join('\n'))
  if (eventName === 'content' && typeof data.content === 'string') {
    handlers.onContent(data.content)
  } else if (eventName === 'done') {
    handlers.onDone?.(data.source || 'ai')
  } else if (eventName === 'error') {
    throw new Error(data.message || 'AI 服务调用失败')
  }
}
