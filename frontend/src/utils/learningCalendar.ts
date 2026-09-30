import type { HeatmapDay } from '@/api/gamification'

export function learningDate(now = new Date(), zoneId = 'Asia/Shanghai') {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: zoneId,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(now)
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find((item) => item.type === type)?.value
  return `${part('year')}-${part('month')}-${part('day')}`
}

export function calendarRange(today: string) {
  const start = new Date(`${today}T00:00:00Z`)
  start.setUTCDate(start.getUTCDate() - 84 - ((start.getUTCDay() + 6) % 7))
  return { from: start.toISOString().slice(0, 10), to: today }
}

export function calendarDays(from: string, to: string, facts: HeatmapDay[]) {
  const byDate = new Map(facts.map((day) => [day.date, day]))
  const date = new Date(`${from}T00:00:00Z`)
  const days: HeatmapDay[] = []
  while (date.toISOString().slice(0, 10) <= to && days.length < 366) {
    const key = date.toISOString().slice(0, 10)
    days.push(byDate.get(key) ?? { date: key, answeredCount: 0, earnedXp: 0 })
    date.setUTCDate(date.getUTCDate() + 1)
  }
  return days
}
