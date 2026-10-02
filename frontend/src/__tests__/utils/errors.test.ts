import { describe, expect, it } from 'vitest'
import { errorMessage } from '@/utils/errors'

describe('readable request failures', () => {
  const fallback = '暂时无法提交理解检查，请重试'
  it.each([
    'Network Error',
    'Failed to fetch',
    'Load failed',
    'timeout of 15000ms exceeded',
    'Request failed with status code 503',
  ])('uses the action context for %s', (message) => {
    expect(errorMessage(new Error(message), fallback)).toBe(fallback)
  })
  it('prefers the business explanation over a transport code', () => {
    expect(errorMessage({ code: 'ECONNABORTED', response: { data: { message: '本轮学习已完成' } } }, fallback)).toBe(
      '本轮学习已完成',
    )
    expect(errorMessage(new Error('今日 AI 调用次数已达上限'), fallback)).toBe('今日 AI 调用次数已达上限')
  })
  it('keeps structured network errors and unknown failures within the provided action context', () => {
    expect(errorMessage({ code: 'ERR_NETWORK' }, fallback)).toBe(fallback)
    expect(errorMessage(undefined, fallback)).toBe(fallback)
  })
})
