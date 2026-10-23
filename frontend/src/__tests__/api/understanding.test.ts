import { expect, it, vi } from 'vitest'
vi.mock('@/utils/request', () => ({ default: { post: vi.fn(), get: vi.fn() } }))
import request from '@/utils/request'
import { submitAttempt } from '@/api/understanding'

it('原题作答携带调用方给定的幂等键', async () => {
  vi.mocked(request.post).mockResolvedValue({} as never)
  await submitAttempt(7, 'B', 12, 'request-key')
  expect(request.post).toHaveBeenCalledWith('/understanding/questions/7/attempts', { answer: 'B', answerTime: 12 }, {
    errorDisplay: 'inline', headers: { 'Idempotency-Key': 'request-key' },
  })
})
