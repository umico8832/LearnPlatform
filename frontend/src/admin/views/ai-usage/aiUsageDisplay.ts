import type { AiLearningEffect } from '@/api/aiUsage'

type ConclusionLevel = AiLearningEffect['conclusionLevel']

export function formatTokens(tokens: number | undefined): string {
  if (!tokens) return '0'
  if (tokens >= 1_000_000) return `${(tokens / 1_000_000).toFixed(1)}M`
  if (tokens >= 1_000) return `${(tokens / 1_000).toFixed(1)}K`
  return tokens.toLocaleString()
}

export function formatCost(cost: number | null | undefined): string {
  if (cost === null || cost === undefined) return '-'
  return `$${cost.toFixed(cost < 0.01 ? 6 : 4)}`
}

export function shortHash(hash: string | null | undefined): string {
  return hash ? hash.slice(0, 10) : '-'
}

export function formatChange(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—'
  return `${value > 0 ? '+' : ''}${value.toFixed(1)}%`
}

export function formatPointChange(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—'
  return `${value > 0 ? '+' : ''}${value.toFixed(1)} 个百分点`
}

export function formatRate(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : `${value.toFixed(1)}%`
}

export function formatLift(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—'
  return `${value > 0 ? '+' : ''}${value.toFixed(1)} 个百分点`
}

export function rateWidth(value: number | null | undefined): string {
  if (value === null || value === undefined) return '0%'
  return `${Math.max(0, Math.min(100, value))}%`
}

export function liftClass(value: number | null | undefined): string {
  if (value === null || value === undefined || value === 0) return 'neutral'
  return value > 0 ? 'positive' : 'negative'
}

export function conclusionTagType(level: ConclusionLevel): 'info' | 'success' | 'warning' {
  if (level === 'POSITIVE_ASSOCIATION') return 'success'
  if (level === 'NEEDS_ATTENTION') return 'warning'
  return 'info'
}

export function conclusionAlertType(level: ConclusionLevel): 'info' | 'success' | 'warning' {
  return conclusionTagType(level)
}

export function effectTagLabel(level: ConclusionLevel): string {
  return {
    INSUFFICIENT_DATA: '样本积累中',
    POSITIVE_ASSOCIATION: '正向关联',
    NO_CLEAR_DIFFERENCE: '差异不明确',
    NEEDS_ATTENTION: '需要关注',
  }[level]
}

export function transferTagLabel(level: ConclusionLevel): string {
  return {
    INSUFFICIENT_DATA: '跨题样本积累中',
    POSITIVE_ASSOCIATION: '跨题正向关联',
    NO_CLEAR_DIFFERENCE: '跨题差异不明确',
    NEEDS_ATTENTION: '跨题效果需关注',
  }[level]
}

export function changeClass(value: number | null | undefined, isRiskMetric = false): string {
  if (value === null || value === undefined || value === 0) return 'neutral'
  const isIncrease = value > 0
  return isRiskMetric ? (isIncrease ? 'negative' : 'positive') : isIncrease ? 'positive' : 'negative'
}
