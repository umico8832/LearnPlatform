import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, disposePinia, setActivePinia, type Pinia } from 'pinia'
import { removeToken, setToken } from '@/utils/auth'

const { getGamificationSummary, getAchievements, getLearningHeatmap, updateDailyGoal } = vi.hoisted(() => ({
  getGamificationSummary: vi.fn(),
  getAchievements: vi.fn(),
  getLearningHeatmap: vi.fn(),
  updateDailyGoal: vi.fn(),
}))

vi.mock('@/api/gamification', () => ({
  getGamificationSummary,
  getAchievements,
  getLearningHeatmap,
  updateDailyGoal,
}))

import ProfileGamification from '@/components/gamification/ProfileGamification.vue'

const summary = {
  totalXp: 36,
  level: 1,
  levelTitle: '起步',
  levelStartXp: 0,
  nextLevelXp: 100,
  xpIntoLevel: 36,
  xpToNextLevel: 64,
  streakDays: 2,
  currentCombo: 1,
  maxCombo: 2,
  todayAnsweredCount: 3,
  dailyGoal: 10,
  todayGoalReached: false,
  zoneId: 'Asia/Shanghai',
  version: 4,
  lastEventId: 9,
  recentAchievements: [],
}

const stubs = {
  LpSkeleton: { template: '<div data-testid="skeleton" />', props: ['rows'] },
}

let pinia: Pinia
describe('ProfileGamification', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    pinia = createPinia()
    setActivePinia(pinia)
    setToken('profile-session-a')
    getGamificationSummary.mockResolvedValue({ data: summary })
    getAchievements.mockResolvedValue({
      data: [
        {
          code: 'FIRST_ANSWER',
          name: '初次作答',
          description: '完成一次作答',
          unlocked: true,
          unlockedAt: '2026-09-30T12:00:00',
          progress: 1,
          target: 1,
        },
      ],
    })
    getLearningHeatmap.mockResolvedValue({ data: [{ date: '2026-09-30', answeredCount: 3, earnedXp: 30 }] })
    updateDailyGoal.mockResolvedValue({ data: { ...summary, dailyGoal: 8, version: 5 } })
  })

  afterEach(() => {
    removeToken()
    disposePinia(pinia)
  })

  it('shows backend achievement and calendar facts after the summary loads', async () => {
    const wrapper = mount(ProfileGamification, { global: { stubs } })
    await flushPromises()
    expect(wrapper.get('[data-testid="gamification-achievements"]').text()).toContain('初次作答')
    expect(wrapper.get('[data-testid="gamification-heatmap"]').text()).toContain('Asia/Shanghai')
    expect(wrapper.findAll('[data-active="true"]')).not.toHaveLength(0)
  })

  it('does not render a prior account’s late achievements', async () => {
    let resolveAchievements!: (value: { data: (typeof summary)[] }) => void
    getAchievements.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveAchievements = resolve
        }),
    )
    const wrapper = mount(ProfileGamification, { global: { stubs } })
    await flushPromises()
    setToken('profile-session-b')
    resolveAchievements({ data: [] })
    await flushPromises()
    expect(wrapper.find('[data-testid="gamification-achievements"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('累计 36 经验')
  })

  it('keeps the form actionable and explains a failed daily-goal save', async () => {
    updateDailyGoal.mockRejectedValueOnce(new Error('network'))
    const wrapper = mount(ProfileGamification, { global: { stubs } })
    await flushPromises()
    const input = wrapper.find('input[type="number"]')
    await input.setValue('8')
    await wrapper.get('[data-testid="gamification-daily-goal"]').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('目标保存失败，请重试')
    expect((wrapper.get('[data-testid="gamification-daily-goal"] button').element as HTMLButtonElement).disabled).toBe(
      false,
    )
  })
})
