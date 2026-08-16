import { describe, it, expect } from 'vitest'
import { formatTime, formatStorage } from '@/utils/format'

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
})
