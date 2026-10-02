/**
 * 这些类型只承载服务端已经认定的奖励事实。组件不得据此推导答题结果、经验或成就。
 */
export interface GamificationAchievement {
  id: string
  title: string
  description?: string
  icon?: string
}

export interface GamificationPracticeSummaryData {
  /** null keeps an ungraded subjective answer distinct from an incorrect one. */
  correctRate: number | null
  xpGained: number
  answeredCount: number
  pendingCount?: number
  achievements?: GamificationAchievement[]
  kicker?: string
  rateLabel?: string
}
