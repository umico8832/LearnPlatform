import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import LearningProgressHeader from '@/components/gamification/LearningProgressHeader.vue'
import AnswerRewardFeedback from '@/components/gamification/AnswerRewardFeedback.vue'
import GamificationPracticeSummary from '@/components/gamification/GamificationPracticeSummary.vue'

const summary = {
  totalXp: 36,
  level: 2,
  levelTitle: '进阶',
  levelStartXp: 20,
  nextLevelXp: 60,
  xpIntoLevel: 16,
  xpToNextLevel: 24,
  streakDays: 3,
  currentCombo: 2,
  maxCombo: 4,
  todayAnsweredCount: 4,
  dailyGoal: 8,
  todayGoalReached: false,
  zoneId: 'Asia/Shanghai',
  version: 2,
  lastEventId: 9,
  recentAchievements: [],
}

describe('quiet learning progress', () => {
  it('names the header link with the level and today facts instead of exposing a decorative XP meter', () => {
    const wrapper = mount(LearningProgressHeader, { props: { summary } })
    const link = wrapper.get('[data-testid="gamification-summary"]')

    expect(link.attributes('aria-label')).toContain('等级 2')
    expect(link.attributes('aria-label')).toContain('今日已答 4/8')
    expect(wrapper.text()).not.toContain('◆')
    expect(wrapper.find('[data-testid="gamification-xp-progress"]').exists()).toBe(false)
  })

  it('keeps answer experience feedback quiet and does not announce a combo', () => {
    const wrapper = mount(AnswerRewardFeedback, {
      props: {
        reward: {
          eventId: 10,
          awardedXp: 6,
          reason: 'ANSWERED',
          eligible: true,
          levelBefore: 2,
          levelAfter: 2,
          leveledUp: false,
          streakDays: 3,
          newAchievements: [],
          summary,
        },
      },
    })

    expect(wrapper.attributes('aria-live')).toBeUndefined()
    expect(wrapper.text()).toContain('本次记录 +6 经验')
    expect(wrapper.text()).not.toContain('连击')
  })

  it('summarizes answered facts without a combo metric or a large animated score ring', () => {
    const wrapper = mount(GamificationPracticeSummary, {
      props: {
        summary: {
          correctRate: 75,
          xpGained: 12,
          answeredCount: 8,
          achievements: [{ id: 'first', title: '初次作答' }],
        },
      },
    })

    expect(wrapper.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('75')
    expect(wrapper.text()).toContain('+12')
    expect(wrapper.text()).toContain('初次作答')
    expect(wrapper.text()).not.toContain('连击')
    expect(wrapper.find('svg').exists()).toBe(false)
  })

  it('keeps an ungraded summary distinct from a zero-percent result', () => {
    const wrapper = mount(GamificationPracticeSummary, {
      props: { summary: { correctRate: null, xpGained: 0, answeredCount: 1, pendingCount: 1 } },
    })

    expect(wrapper.text()).toContain('待判分')
    expect(wrapper.text()).toContain('1 题')
    expect(wrapper.find('[role="progressbar"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('0%')
  })
})
