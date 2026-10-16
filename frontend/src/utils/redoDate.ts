/** When to redo a wrong question, from the review card's date (`YYYY-MM-DD`, server calendar). */
export interface RedoWhen {
  text: string
  /** `due`: today or overdue; `later`: scheduled; `none`: no card yet. */
  tone: 'due' | 'later' | 'none'
}

const DAY = 86_400_000

function dayNumber(year: number, month: number, day: number) {
  return Date.UTC(year, month - 1, day) / DAY
}

export function redoWhen(date: string | null, today = new Date()): RedoWhen {
  const match = date ? /^(\d{4})-(\d{2})-(\d{2})/.exec(date) : null
  if (!match) return { text: '未排期', tone: 'none' }
  const [year, month, day] = match.slice(1).map(Number)
  const diff = dayNumber(year, month, day) - dayNumber(today.getFullYear(), today.getMonth() + 1, today.getDate())
  if (diff < 0) return { text: `逾期 ${-diff} 天`, tone: 'due' }
  if (diff === 0) return { text: '今天', tone: 'due' }
  if (diff === 1) return { text: '明天', tone: 'later' }
  const sameYear = year === today.getFullYear()
  return { text: `${sameYear ? '' : `${year} 年 `}${month} 月 ${day} 日`, tone: 'later' }
}
