import { describe, it, expect } from 'vitest'
import { formatTime, formatStorage, formatDateTime, formatRelativeTime } from '@/utils/format'

describe('format utils', () => {
  describe('formatTime', () => {
    it('replaces the T separator and truncates to seconds', () => {
      expect(formatTime('2026-08-15T16:30:00')).toBe('2026-08-15 16:30:00')
    })

    it('returns a placeholder for null/undefined/empty values', () => {
      expect(formatTime(null)).toBe('-')
      expect(formatTime(undefined)).toBe('-')
      expect(formatTime('')).toBe('-')
    })
  })

  describe('formatStorage', () => {
    it('formats bytes below 1MB as KB', () => {
      expect(formatStorage(1024)).toBe('1 KB')
      expect(formatStorage(2048)).toBe('2 KB')
    })

    it('formats an exact megabyte without decimals', () => {
      expect(formatStorage(1024 * 1024)).toBe('1 MB')
      expect(formatStorage(2 * 1024 * 1024)).toBe('2 MB')
    })

    it('formats fractional megabytes with one decimal', () => {
      expect(formatStorage(1.5 * 1024 * 1024)).toBe('1.5 MB')
    })
  })

  describe('formatDateTime', () => {
    it('formats an ISO timestamp to local yyyy-MM-dd HH:mm', () => {
      expect(formatDateTime('2026-08-15T16:30:00')).toMatch(/^2026-08-15 \d{2}:\d{2}$/)
    })

    it('returns a placeholder for null/undefined', () => {
      expect(formatDateTime(null)).toBe('-')
      expect(formatDateTime(undefined)).toBe('-')
    })
  })

  describe('formatRelativeTime', () => {
    it('returns 刚刚 for very recent timestamps', () => {
      expect(formatRelativeTime(new Date(Date.now() - 10_000).toISOString())).toBe('刚刚')
    })

    it('returns minutes / hours / days for older timestamps', () => {
      expect(formatRelativeTime(new Date(Date.now() - 5 * 60_000).toISOString())).toBe('5 分钟前')
      expect(formatRelativeTime(new Date(Date.now() - 3 * 3_600_000).toISOString())).toBe('3 小时前')
      expect(formatRelativeTime(new Date(Date.now() - 2 * 86_400_000).toISOString())).toBe('2 天前')
    })

    it('returns a placeholder for null/undefined', () => {
      expect(formatRelativeTime(null)).toBe('-')
      expect(formatRelativeTime(undefined)).toBe('-')
    })
  })
})
