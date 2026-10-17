import { describe, expect, it } from 'vitest'
import {
  buildCalendar,
  DAYS,
  FOLLOWED,
  FOLLOWED_NEXT,
  dayLabel,
  gapLabel,
  level,
  monthStarts,
} from '@/views/home/showcase/remember/calendar'

describe('illustrative review calendar', () => {
  it('is deterministic, finite and includes each followed review', () => {
    const first = buildCalendar()
    expect(first).toEqual(buildCalendar())
    expect(first).not.toEqual(buildCalendar(9))
    expect(first).toHaveLength(DAYS)
    expect(first.every((count) => Number.isInteger(count) && count >= 0)).toBe(true)
    for (const day of FOLLOWED) expect(first[day]).toBeGreaterThan(0)
    expect(FOLLOWED.every((day, i) => day >= 0 && day < DAYS && (i === 0 || day > FOLLOWED[i - 1]))).toBe(true)
    expect(FOLLOWED_NEXT).toBeGreaterThan(0)
  })
  it('labels the actual intervals and UTC dates consistently', () => {
    expect(FOLLOWED.slice(1).map((day, i) => gapLabel(day - FOLLOWED[i]))).toEqual([
      '1 week',
      '2 weeks',
      '4 weeks',
      '7 weeks',
      '2½ months',
      '4 months',
    ])
    expect(dayLabel(0)).toBe('Jan 5')
    expect(dayLabel(27)).toBe('Feb 1')
    const months = monthStarts()
    expect(months.map((month) => month.label)).toEqual([
      'Jan',
      'Feb',
      'Mar',
      'Apr',
      'May',
      'Jun',
      'Jul',
      'Aug',
      'Sep',
      'Oct',
      'Nov',
    ])
    expect(months.every((month, i) => i === 0 || month.week > months[i - 1].week)).toBe(true)
    expect([0, 1, 2, 3, 4, 12].map(level)).toEqual([0, 1, 2, 3, 4, 4])
  })
})
