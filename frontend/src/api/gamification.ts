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

export function getGamificationSummary() {
  return request.get<unknown, ApiResponse<GamificationSummary>>('/gamification/summary')
}

export function getAchievements() {
  return request.get<unknown, ApiResponse<Achievement[]>>('/gamification/achievements')
}

export function updateDailyGoal(dailyGoal: number) {
  return request.post<unknown, ApiResponse<GamificationSummary>>('/gamification/daily-goal', { dailyGoal })
}

export function getLearningHeatmap(from: string, to: string) {
  return request.get<unknown, ApiResponse<HeatmapDay[]>>('/gamification/heatmap', { params: { from, to } })
}
