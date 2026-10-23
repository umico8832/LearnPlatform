import { afterEach, expect, it, vi } from 'vitest'
import { createSubmissionIntent } from '@/utils/submissionIntent'

afterEach(() => vi.unstubAllGlobals())

it('未知结果重试复用 UUID 和首次冻结的作答时间', () => {
  vi.stubGlobal('crypto', { randomUUID: vi.fn().mockReturnValue('first-key') })
  const intent = createSubmissionIntent<{ answer: string; answerTime: number }>()
  const first = intent.prepare({ answer: 'A', answerTime: 12 }, { answer: 'A' })
  const retry = intent.prepare({ answer: 'A', answerTime: 35 }, { answer: 'A' })
  expect(retry).toEqual(first)
  expect(retry.body.answerTime).toBe(12)
})

it('改变答案或 reset 都创建新的 UUID', () => {
  vi.stubGlobal('crypto', { randomUUID: vi.fn().mockReturnValueOnce('first').mockReturnValueOnce('second').mockReturnValueOnce('third') })
  const intent = createSubmissionIntent<{ answer: string; answerTime: number }>()
  expect(intent.prepare({ answer: 'A', answerTime: 1 }, { answer: 'A' }).key).toBe('first')
  expect(intent.prepare({ answer: 'B', answerTime: 2 }, { answer: 'B' }).key).toBe('second')
  intent.reset()
  expect(intent.prepare({ answer: 'B', answerTime: 3 }, { answer: 'B' }).key).toBe('third')
})
