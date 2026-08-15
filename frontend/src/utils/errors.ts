/**
 * 前端统一错误提取：catch 子句变量在 TS 中是 unknown，这里集中提取可读信息，
 * 兼容业务错误（Error.message）、Axios 错误（response.data.message）与字符串异常。
 */
export function errorMessage(error: unknown, fallback: string): string {
  if (typeof error === 'object' && error !== null && 'response' in error) {
    const response = (error as { response?: { data?: { message?: unknown } } }).response
    const serverMessage = response?.data?.message
    if (typeof serverMessage === 'string' && serverMessage) return serverMessage
  }
  if (error instanceof Error && error.message) return error.message
  if (typeof error === 'string' && error) return error
  return fallback
}

export function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError'
}

/** Element Plus el-tag 等通用语义色板；未指定时用 undefined 表达默认样式。 */
export type SemanticTagType = 'success' | 'info' | 'warning' | 'danger' | 'primary' | undefined
