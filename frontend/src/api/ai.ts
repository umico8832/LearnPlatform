import { aiService } from '@/utils/request'
import { getToken } from '@/utils/auth'
import type { ApiResponse } from '@/types/api'

export interface AiResponse {
  content: string
  source: string
}

/** AI 生成题目解析 */
export function getExplanation(questionId: number) {
  return aiService.post<ApiResponse<AiResponse>>('/ai/explanation', { questionId }).then((res) => res.data)
}

/** AI 生成变式题 */
export function getVariant(questionId: number) {
  return aiService.post<ApiResponse<AiResponse>>('/ai/variant', { questionId }).then((res) => res.data)
}

/** AI 生成复习建议 */
export function getReviewSuggestion(courseId?: number) {
  return aiService.post<ApiResponse<AiResponse>>('/ai/review-suggestion', courseId ? { courseId } : {}).then((res) => res.data)
}

/** AI 生成知识点总结 */
export function getSummary(knowledgePointId: number) {
  return aiService.post<ApiResponse<AiResponse>>('/ai/summary', { knowledgePointId }).then((res) => res.data)
}

/** 查询当前用户今日 AI 调用用量 */
export function getAiUsage() {
  return aiService.get<ApiResponse<{ todayCount: number; dailyQuota: number }>>('/ai/usage').then((res) => res.data)
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
  await streamAiResponse(`${baseUrl}/ai/${type}/stream`, { questionId }, handlers, signal)
}

/** 流式生成复习建议。 */
export async function streamReviewSuggestion(
  courseId: number | undefined,
  handlers: StreamHandlers,
  signal?: AbortSignal,
) {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
  await streamAiResponse(`${baseUrl}/ai/review-suggestion/stream`, courseId ? { courseId } : {}, handlers, signal)
}

async function streamAiResponse(
  url: string,
  body: Record<string, unknown>,
  handlers: StreamHandlers,
  signal?: AbortSignal,
) {
  const token = getToken()
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
    signal,
  })

  if (!response.ok) {
    // 尝试从响应体中提取错误消息（配额超限等）
    let errorMsg = ''
    try {
      const errBody = await response.json()
      if (errBody?.message) errorMsg = errBody.message
    } catch { /* ignore */ }
    if (response.status === 401) throw new Error('登录已过期，请重新登录')
    if (errorMsg) throw new Error(errorMsg)
    throw new Error(`AI 服务请求失败 (${response.status})`)
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

// ======================== AI 学习资产 API ========================

/** AI 学习资产类型 */
export type AiAssetType =
  | 'FULL_EXPLANATION'
  | 'BEGINNER_EXPLANATION'
  | 'STEP_BY_STEP'
  | 'WRONG_OPTION_ANALYSIS'
  | 'COMMON_MISTAKES'
  | 'VARIANT'

/** AI 学习资产类型标签映射 */
export const AI_ASSET_LABELS: Record<AiAssetType, string> = {
  FULL_EXPLANATION: '标准解析',
  BEGINNER_EXPLANATION: '小白版',
  STEP_BY_STEP: '步骤拆解',
  WRONG_OPTION_ANALYSIS: '错误选项分析',
  COMMON_MISTAKES: '常见误区',
  VARIANT: '变式题',
}

/** AI 学习资产 VO */
export interface QuestionLearningAsset {
  id: number
  questionId: number
  assetType: AiAssetType
  assetTypeLabel: string
  content: string
  model: string
  createTime: string
}

/** 查询一道题的所有已缓存 AI 学习资产 */
export function getQuestionAssets(questionId: number) {
  return aiService.get<ApiResponse<QuestionLearningAsset[]>>(`/ai/assets/${questionId}`).then((res) => res.data)
}

/** 同步生成或获取指定类型的 AI 学习资产 */
export function generateAsset(questionId: number, assetType: AiAssetType) {
  return aiService.post<ApiResponse<QuestionLearningAsset>>('/ai/asset/generate', { questionId, assetType }).then((res) => res.data)
}

/** 流式生成指定类型的 AI 学习资产 */
export async function streamAsset(
  questionId: number,
  assetType: AiAssetType,
  handlers: StreamHandlers,
  signal?: AbortSignal,
) {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
  await streamAiResponse(`${baseUrl}/ai/asset/stream`, { questionId, assetType }, handlers, signal)
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
