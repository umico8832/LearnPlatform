import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export interface Achievement {
  code: string
  name: string
  description: string
  unlocked: boolean
  unlockedAt: string | null
  progress: number
  target: number
}

export interface GamificationSummary {
  totalXp: number
  level: number
  levelTitle: string
  levelStartXp: number
  nextLevelXp: number | null
  xpIntoLevel: number
  xpToNextLevel: number | null
  streakDays: number
  currentCombo: number
  maxCombo: number
  todayAnsweredCount: number
  dailyGoal: number
  todayGoalReached: boolean
  zoneId: string
  version: number
  lastEventId: number
  recentAchievements: Achievement[]
}

export interface RewardFeedback {
  eventId: number
  awardedXp: number
  reason: string
  eligible: boolean
  levelBefore: number
  levelAfter: number
  leveledUp: boolean
  streakDays: number
  newAchievements: Achievement[]
  summary: GamificationSummary
}

export interface HeatmapDay {
  date: string
  answeredCount: number
  earnedXp: number
}

export type GamificationRequestOptions = { errorDisplay?: 'inline' }

export function getGamificationSummary(options?: GamificationRequestOptions) {
  return options
    ? request.get<unknown, ApiResponse<GamificationSummary>>('/gamification/summary', options)
    : request.get<unknown, ApiResponse<GamificationSummary>>('/gamification/summary')
}

export function getAchievements(options?: GamificationRequestOptions) {
  return options
    ? request.get<unknown, ApiResponse<Achievement[]>>('/gamification/achievements', options)
    : request.get<unknown, ApiResponse<Achievement[]>>('/gamification/achievements')
}

export function updateDailyGoal(dailyGoal: number, options?: GamificationRequestOptions) {
  return options
    ? request.post<unknown, ApiResponse<GamificationSummary>>('/gamification/daily-goal', { dailyGoal }, options)
    : request.post<unknown, ApiResponse<GamificationSummary>>('/gamification/daily-goal', { dailyGoal })
}

export function getLearningHeatmap(from: string, to: string, options?: GamificationRequestOptions) {
  const config = { params: { from, to }, ...options }
  return request.get<unknown, ApiResponse<HeatmapDay[]>>('/gamification/heatmap', config)
}
