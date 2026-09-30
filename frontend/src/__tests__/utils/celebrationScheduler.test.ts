import { describe, expect, it, vi } from 'vitest'
import { createCelebrationScheduler } from '@/utils/celebrationScheduler'

const firstAchievement = { id: 'first-answer', title: '第一次作答' }
const secondAchievement = { id: 'three-day-streak', title: '三日坚持' }

describe('celebrationScheduler', () => {
  it('rejects exam celebrations before they can reach the host', () => {
    const scheduler = createCelebrationScheduler()
    expect(scheduler.enqueue({ id: 'exam-result', kind: 'level', level: 2, context: 'exam' })).toBe(false)
    expect(scheduler.current()).toBeUndefined()
  })

  it('merges repeated feedback and reveals every achievement one at a time', () => {
    const scheduler = createCelebrationScheduler()
    const observe = vi.fn()
    scheduler.subscribe(observe)
    scheduler.enqueue({
      id: 'one',
      kind: 'achievement',
      context: 'learning',
      xpGained: 8,
      achievements: [firstAchievement],
    })
    scheduler.enqueue({
      id: 'two',
      kind: 'achievement',
      context: 'learning',
      xpGained: 5,
      achievements: [secondAchievement, firstAchievement],
    })

    expect(scheduler.current()).toMatchObject({ xpGained: 13, achievements: [firstAchievement, secondAchievement] })
    scheduler.dismiss()
    expect(scheduler.current()).toMatchObject({ achievements: [secondAchievement] })
    scheduler.dismiss()
    expect(scheduler.current()).toBeUndefined()
    expect(observe).toHaveBeenCalled()
  })

  it('clears active and queued messages on account reset', () => {
    const scheduler = createCelebrationScheduler()
    scheduler.enqueue({ id: 'level', kind: 'level', context: 'learning', level: 2 })
    scheduler.enqueue({ id: 'achievement', kind: 'achievement', context: 'learning', achievements: [firstAchievement] })
    scheduler.reset()
    expect(scheduler.current()).toBeUndefined()
  })
})
