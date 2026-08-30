import { describe, expect, it } from 'vitest'
import {
  changeClass,
  conclusionTagType,
  effectTagLabel,
  formatCost,
  formatLift,
  formatRate,
  formatTokens,
  liftClass,
  rateWidth,
  shortHash,
  transferTagLabel,
} from '@/admin/views/ai-usage/aiUsageDisplay'

describe('AI 用量展示映射', () => {
  it('格式化 Token、成本和审计指纹', () => {
    expect(formatTokens(1_250_000)).toBe('1.3M')
    expect(formatTokens(1_250)).toBe('1.3K')
    expect(formatCost(0.0012)).toBe('$0.001200')
    expect(formatCost(null)).toBe('-')
    expect(shortHash('1234567890abcdef')).toBe('1234567890')
  })

  it('对比值保持空值、方向和宽度边界', () => {
    expect(formatRate(null)).toBe('—')
    expect(formatLift(2.5)).toBe('+2.5 个百分点')
    expect(rateWidth(120)).toBe('100%')
    expect(rateWidth(-3)).toBe('0%')
    expect(liftClass(-1)).toBe('negative')
    expect(changeClass(-2, true)).toBe('positive')
  })

  it('区分同题和跨题结论文案', () => {
    expect(effectTagLabel('POSITIVE_ASSOCIATION')).toBe('正向关联')
    expect(transferTagLabel('POSITIVE_ASSOCIATION')).toBe('跨题正向关联')
    expect(conclusionTagType('NEEDS_ATTENTION')).toBe('warning')
  })
})
