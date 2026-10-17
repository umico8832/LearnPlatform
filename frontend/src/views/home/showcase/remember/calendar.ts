/**
 * A made-up ten months of review days, shaped the way spaced review behaves: questions
 * arrive early and each comes back after a longer gap than the last, so the calendar is
 * busy on the left and thins out to the right. Seeded, so every visit draws the same year.
 */
export const WEEKS = 44
export const DAYS = WEEKS * 7
/** A Monday, so every column is one week; only month and day are ever shown. */
const START = Date.UTC(2026, 0, 5)
const DAY_MS = 86_400_000

/**
 * One question followed through the year; its gaps are what the arcs show. Its days all
 * fall on the calendar's top row, so the arcs can leap along the top edge.
 */
export const FOLLOWED = [0, 7, 21, 49, 98, 175, 294]
/** Where the followed question would come back after its last review. */
export const FOLLOWED_NEXT = 182

const GAPS = [1, 3, 7, 15, 32, 70, 150]
const QUESTIONS = 22

function random(seed: number) {
  let state = seed >>> 0
  return () => {
    state = (state * 1664525 + 1013904223) >>> 0
    return state / 2 ** 32
  }
}

/** Reviews on each day. */
export function buildCalendar(seed = 7): number[] {
  const days = new Array<number>(DAYS).fill(0)
  const rand = random(seed)
  for (const day of FOLLOWED) days[day] += 1
  for (let q = 0; q < QUESTIONS; q++) {
    let day = Math.floor(rand() ** 1.7 * 84)
    let step = 0
    while (day < DAYS) {
      days[day] += 1
      step = Math.min(step + 1, GAPS.length)
      day += Math.max(1, Math.round(GAPS[step - 1] * (0.8 + rand() * 0.45)))
    }
  }
  return days
}

/** GitHub-style shade, 0 for an empty day up to 4. */
export function level(count: number): number {
  return Math.min(4, count)
}

const dateOf = (day: number) => new Date(START + day * DAY_MS)

/** "Mar 12" */
export function dayLabel(day: number): string {
  return dateOf(day).toLocaleDateString('en-US', { month: 'short', day: 'numeric', timeZone: 'UTC' })
}

/** The week columns where a new month begins, with the month's short name. */
export function monthStarts(): { week: number; label: string }[] {
  const out: { week: number; label: string }[] = []
  let last = -1
  for (let week = 0; week < WEEKS; week++) {
    const month = dateOf(week * 7 + 6).getUTCMonth()
    if (month === last) continue
    last = month
    out.push({ week, label: dateOf(week * 7 + 6).toLocaleDateString('en-US', { month: 'short', timeZone: 'UTC' }) })
  }
  return out
}

/** "1 week", "7 weeks", "2½ months" */
export function gapLabel(days: number): string {
  const weeks = Math.round(days / 7)
  if (weeks <= 8) return weeks === 1 ? '1 week' : `${weeks} weeks`
  const months = Math.round((days / 30.44) * 2) / 2
  const whole = Math.floor(months)
  return `${whole}${months > whole ? '½' : ''} months`
}
