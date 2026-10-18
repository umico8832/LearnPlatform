import { describe, expect, it } from 'vitest'
import { calendarDays, calendarRange, learningDate } from '@/utils/learningCalendar'

describe('learning natural days', () => {
  it('uses the backend zone across midnight rather than the browser zone', () => {
    expect(learningDate(new Date('2026-09-30T23:00:00Z'))).toBe('2026-09-30')
  })
  it('handles New York midnight on both DST transition dates', () => {
    expect(learningDate(new Date('2026-03-08T04:59:59Z'))).toBe('2026-03-07')
    expect(learningDate(new Date('2026-03-08T05:00:00Z'))).toBe('2026-03-08')
    expect(learningDate(new Date('2026-11-01T03:59:59Z'))).toBe('2026-10-31')
    expect(learningDate(new Date('2026-11-01T04:00:00Z'))).toBe('2026-11-01')
  })
  it('starts on Monday and fills zero days only inside the successful API range', () => {
    const { from, to } = calendarRange('2026-09-30')
    expect(new Date(`${from}T00:00:00Z`).getUTCDay()).toBe(1)
    const days = calendarDays(from, to, [{ date: to, earnedXp: 12, answeredCount: 2 }])
    expect(days.at(-1)?.earnedXp).toBe(12)
    expect(days[0].earnedXp).toBe(0)
    expect(days.length).toBeGreaterThanOrEqual(85)
  })
})
