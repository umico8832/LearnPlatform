import { describe, expect, it } from 'vitest'
import { validEmail, validLength } from '@/views/auth/authValidation'

describe('auth validation compatibility', () => {
  it.each(['learner@example.com', 'a+b@sub.example.com', '学员@学校.中国', '"a b"@example.com', 'a@[127.0.0.1]'])(
    'accepts the existing email format %s',
    (email) => {
      expect(validEmail(email)).toBe(true)
    },
  )
  it.each([
    '',
    'a',
    'a@localhost',
    'a..b@example.com',
    ' a@example.com',
    'a@example.com ',
    'a@b.c',
    `${'a'.repeat(309)}@example.com`,
  ])('rejects malformed or overlong email %s', (email) => {
    expect(validEmail(email)).toBe(false)
  })
  it('preserves username and password length boundaries, counting supplementary-plane characters once', () => {
    expect(validLength('𠮷𠮷', 3, 50)).toBe(false)
    expect(validLength('𠮷𠮷𠮷', 3, 50)).toBe(true)
    expect(validLength('a'.repeat(50), 3, 50)).toBe(true)
    expect(validLength('a'.repeat(51), 3, 50)).toBe(false)
    expect(validLength('a'.repeat(7), 8, 64)).toBe(false)
    expect(validLength('a'.repeat(8), 8, 64)).toBe(true)
    expect(validLength('𠮷'.repeat(64), 8, 64)).toBe(true)
    expect(validLength('a'.repeat(65), 8, 64)).toBe(false)
  })
})
