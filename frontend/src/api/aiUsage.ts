import request from '@/utils/request'

/** AI 调用功能统计 */
export interface FunctionStats {
  functionType: string
  count: number
  successCount: number
  failedCount: number
  totalTokens: number
  totalCostUsd: number | null
  avgDuration: number
}

/** AI 调用模型统计 */
export interface ModelStats {
  model: string
  count: number
  totalTokens: number
  totalCostUsd: number | null
  avgDuration: number
}

/** 每日调用趋势 */
export interface DailyTrend {
  date: string
  totalCount: number
  successCount: number
  failedCount: number
  totalTokens: number
  totalCostUsd: number | null
}

/** Top 活跃用户 */
export interface TopUser {
  userId: number
  username: string
  callCount: number
  totalTokens: number
  totalCostUsd: number | null
  avgDuration: number
}

/** 最近失败调用 */
export interface RecentFailure {
  id: number
  userId: number
  functionType: string
  model: string
  traceId?: string
  promptTemplate?: string
  promptHash?: string
  modelConfigVersion?: string
  errorMessage: string
  createTime: string
}

/** AI 调用总览 */
export interface AiUsageOverview {
  totalCalls: number
  successCalls: number
  failedCalls: number
  successRate: number
  totalTokens: number
  avgDuration: number
  todayCalls: number
  todayTokens: number
  totalCostUsd: number | null
  todayCostUsd: number | null
  functionStats: FunctionStats[]
  modelStats: ModelStats[]
  dailyTrends: DailyTrend[]
  topUsers: TopUser[]
  recentFailures: RecentFailure[]
}

export interface AiUsagePeriodStats {
  totalCalls: number
  failedCalls: number
  failureRate: number
  totalTokens: number
  avgDuration: number
  totalCostUsd: number | null
}

export interface AiUsageChanges {
  callsPercent: number | null
  tokensPercent: number | null
  costPercent: number | null
  failureRatePointChange: number
  avgDurationPercent: number | null
}

export interface AiUsageAlert {
  id?: number
  level: 'INFO' | 'WARNING'
  type: string
  message: string
  periodDays?: number
  periodStart?: string
  periodEnd?: string
  status?: 'OPEN' | 'ACKNOWLEDGED'
  acknowledgedBy?: number
  acknowledgedTime?: string
  createTime?: string
  updateTime?: string
}

/** 当前周期与前一等长周期的 AI 运营报告 */
export interface AiUsageReport {
  days: number
  current: AiUsagePeriodStats
  previous: AiUsagePeriodStats
  changes: AiUsageChanges
  alerts: AiUsageAlert[]
}

export interface AiAssetTypeEffect {
  assetType: string
  assetTypeLabel: string
  viewCount: number
  userCount: number
  feedbackCount: number
  helpfulRate: number | null
}

/** AI 学习资产使用与后续同题作答的观察性统计 */
export interface AiLearningEffect {
  days: number
  periodStart: string
  periodEnd: string
  assetViewCount: number
  engagedUserCount: number
  viewedQuestionCount: number
  feedbackCount: number
  helpfulRate: number | null
  variantTrainingStartedCount: number
  variantTrainingCompletedCount: number
  variantTrainingCompletionRate: number | null
  afterViewPracticeCount: number
  afterViewCorrectRate: number | null
  baselinePracticeCount: number
  baselineCorrectRate: number | null
  correctRateLift: number | null
  conclusionLevel: 'INSUFFICIENT_DATA' | 'POSITIVE_ASSOCIATION' | 'NO_CLEAR_DIFFERENCE' | 'NEEDS_ATTENTION'
  conclusion: string
  assetTypeStats: AiAssetTypeEffect[]
}

/** 获取 AI 调用总览 */
export function getAiUsageOverview(days?: number) {
  return request.get<AiUsageOverview>('/admin/ai-usage/overview', {
    params: days ? { days } : {},
  })
}

/** 获取 AI 调用运营报告与实时异常提醒 */
export function getAiUsageReport(days?: number) {
  return request.get<AiUsageReport>('/admin/ai-usage/report', {
    params: days ? { days } : {},
  })
}

/** 获取 AI 学习资产使用与后续答题表现的观察性统计 */
export function getAiLearningEffect(days?: number) {
  return request.get<AiLearningEffect>('/admin/ai-usage/learning-effect', {
    params: days ? { days } : {},
  })
}

/** 获取未确认 AI 运营提醒 */
export function getAiUsageAlerts(limit?: number) {
  return request.get<AiUsageAlert[]>('/admin/ai-usage/alerts', {
    params: limit ? { limit } : {},
  })
}

/** 确认 AI 运营提醒 */
export function acknowledgeAiUsageAlert(id: number) {
  return request.post<AiUsageAlert>(`/admin/ai-usage/alerts/${id}/acknowledge`)
}
