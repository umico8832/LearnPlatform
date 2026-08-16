/**
 * 纯展示型格式化工具，不依赖组件或请求上下文，便于单测与跨页面复用。
 */

/** 将后端 `yyyy-MM-ddTHH:mm:ss` 时间转为 `yyyy-MM-dd HH:mm:ss`；空值返回占位符。 */
export function formatTime(time: string | null | undefined): string {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}

/** 将字节数格式化为易读的 KB / MB。 */
export function formatStorage(bytes: number): string {
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(bytes % (1024 * 1024) === 0 ? 0 : 1)} MB`
}

/** 将时间转为本地化的 `yyyy-MM-dd HH:mm`（用于学习记录等展示）。 */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** 相对时间：刚刚 / N 分钟前 / N 小时前 / N 天前 / 具体日期。 */
export function formatRelativeTime(value: string | null | undefined): string {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const diffMs = Date.now() - date.getTime()
  const minutes = Math.floor(diffMs / 60_000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days} 天前`
  return formatDateTime(value)
}
