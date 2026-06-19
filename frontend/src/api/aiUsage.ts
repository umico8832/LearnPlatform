import request from '@/utils/request'

/** AI 调用功能统计 */
export interface FunctionStats {
  functionType: string
  count: number
  successCount: number
  failedCount: number
  totalTokens: number
  avgDuration: number
}

/** AI 调用模型统计 */
export interface ModelStats {
  model: string
  count: number
  totalTokens: number
  avgDuration: number
}

/** 每日调用趋势 */
export interface DailyTrend {
  date: string
  totalCount: number
  successCount: number
  failedCount: number
  totalTokens: number
}

/** Top 活跃用户 */
export interface TopUser {
  userId: number
  username: string
  callCount: number
  totalTokens: number
  avgDuration: number
}

/** 最近失败调用 */
export interface RecentFailure {
  id: number
  userId: number
  functionType: string
  model: string
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
  functionStats: FunctionStats[]
  modelStats: ModelStats[]
  dailyTrends: DailyTrend[]
  topUsers: TopUser[]
  recentFailures: RecentFailure[]
}

/** 获取 AI 调用总览 */
export function getAiUsageOverview(days?: number) {
  return request.get<AiUsageOverview>('/api/admin/ai-usage/overview', {
    params: days ? { days } : {},
  })
}