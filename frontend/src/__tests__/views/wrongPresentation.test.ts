import { describe, expect, it } from 'vitest'
import { isOptionKey, redoWhen } from '@/views/wrong/wrongPresentation'

const today = new Date(2026, 9, 16, 23, 30)

describe('redoWhen', () => {
  it('names overdue, today, tomorrow and later dates by calendar day', () => {
    expect(redoWhen('2026-10-13', today)).toEqual({ text: '逾期 3 天', tone: 'due' })
    expect(redoWhen('2026-10-16', today)).toEqual({ text: '今天', tone: 'due' })
    expect(redoWhen('2026-10-17', today)).toEqual({ text: '明天', tone: 'later' })
    expect(redoWhen('2026-11-02', today)).toEqual({ text: '11 月 2 日', tone: 'later' })
    expect(redoWhen('2027-01-05', today)).toEqual({ text: '2027 年 1 月 5 日', tone: 'later' })
  })

  it('does not guess a date it was not given', () => {
    expect(redoWhen(null, today)).toEqual({ text: '未排期', tone: 'none' })
    expect(redoWhen('not a date', today)).toEqual({ text: '未排期', tone: 'none' })
  })
})

describe('isOptionKey', () => {
  it('accepts a single option letter only', () => {
    expect(isOptionKey('B')).toBe(true)
    expect(isOptionKey('AB')).toBe(false)
    expect(isOptionKey('rear == front')).toBe(false)
    expect(isOptionKey(null)).toBe(false)
  })
})
