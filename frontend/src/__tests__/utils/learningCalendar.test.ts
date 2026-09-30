import { describe, expect, it } from 'vitest'
import { calendarDays, calendarRange, learningDate } from '@/utils/learningCalendar'

describe('learning natural days', () => {
  it('uses the backend zone across midnight rather than the browser zone', () => {
    expect(learningDate(new Date('2026-09-30T23:00:00Z'))).toBe('2026-10-01')
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
