import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, disposePinia, setActivePinia, type Pinia } from 'pinia'
import { useGamificationStore } from '@/stores/gamification'
import { getGamificationSummary, type GamificationSummary, type RewardFeedback } from '@/api/gamification'
import { removeToken, setToken } from '@/utils/auth'

vi.mock('@/api/gamification', () => ({ getGamificationSummary: vi.fn(), updateDailyGoal: vi.fn() }))

const summary = (version: number): GamificationSummary => ({
  totalXp: version * 10,
  level: 1,
  levelTitle: '起步',
  levelStartXp: 0,
  nextLevelXp: 60,
  xpIntoLevel: version * 10,
  xpToNextLevel: 60 - version * 10,
  streakDays: 1,
  currentCombo: version,
  maxCombo: version,
  todayAnsweredCount: version,
  dailyGoal: 10,
  todayGoalReached: false,
  zoneId: 'Asia/Shanghai',
  version,
  lastEventId: version,
  recentAchievements: [],
})
const reward = (eventId = 1, version = eventId): RewardFeedback => ({
  eventId,
  awardedXp: 10,
  reason: 'CORRECT_ANSWER',
  eligible: true,
  levelBefore: 1,
  levelAfter: 1,
  leveledUp: false,
  streakDays: 1,
  newAchievements: [],
  summary: summary(version),
})
let pinia: Pinia
beforeEach(() => {
  localStorage.clear()
  vi.clearAllMocks()
  pinia = createPinia()
  setActivePinia(pinia)
  setToken('test-session-a')
})
afterEach(() => disposePinia(pinia))

describe('account-scoped learning encouragement', () => {
  it('deduplicates feedback and never rolls back a newer server summary', () => {
    const store = useGamificationStore()
    expect(store.acceptReward(reward(2))).toBe(true)
    expect(store.acceptReward(reward(2))).toBe(false)
    store.acceptReward(reward(1))
    expect(store.summary?.version).toBe(2)
  })
  it.each(['logout', 'switch'])('clears all progress immediately on %s', (action) => {
    const store = useGamificationStore()
    store.acceptReward(reward())
    if (action === 'logout') removeToken()
    else setToken('test-session-b')
    expect(store.summary).toBeNull()
  })
  it('rejects rewards during an exam without rolling back existing progress', () => {
    const store = useGamificationStore()
    store.acceptReward(reward())
    store.setExamMode(true)
    expect(store.acceptReward(reward(2))).toBe(false)
    expect(store.summary?.version).toBe(1)
    store.setExamMode(false)
    expect(store.acceptReward(reward(2))).toBe(true)
  })
  it('invalidates an in-flight summary request on entering an exam', async () => {
    let resolve!: (value: { data: GamificationSummary }) => void
    vi.mocked(getGamificationSummary).mockImplementationOnce(
      () =>
        new Promise((r) => {
          resolve = (value) => r({ ...value, code: 0, message: 'ok' })
        }),
    )
    const store = useGamificationStore()
    const pending = store.load()
    expect(store.loading).toBe(true)
    store.setExamMode(true)
    expect(store.loading).toBe(false)
    resolve({ data: summary(5) })
    await pending
    expect(store.summary).toBeNull()
    expect(store.loading).toBe(false)
  })
  it('ignores a summary response from the previous account', async () => {
    let resolve!: (value: { data: GamificationSummary }) => void
    vi.mocked(getGamificationSummary).mockImplementationOnce(
      () =>
        new Promise((r) => {
          resolve = (value) => r({ ...value, code: 0, message: 'ok' })
        }),
    )
    const store = useGamificationStore()
    const pending = store.load()
    setToken('test-session-b')
    resolve({ data: summary(5) })
    await pending
    expect(store.summary).toBeNull()
  })
  it('keeps newer submission progress when an earlier load completes', async () => {
    let resolve!: (value: { data: GamificationSummary }) => void
    vi.mocked(getGamificationSummary).mockImplementationOnce(
      () =>
        new Promise((r) => {
          resolve = (value) => r({ ...value, code: 0, message: 'ok' })
        }),
    )
    const store = useGamificationStore()
    const pending = store.load()
    store.acceptReward(reward(3))
    resolve({ data: summary(1) })
    await pending
    expect(store.summary?.version).toBe(3)
  })
})
