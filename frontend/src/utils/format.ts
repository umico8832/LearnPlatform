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
