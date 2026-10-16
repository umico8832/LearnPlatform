import { beforeEach, describe, expect, it, vi } from 'vitest'
import { BusinessError } from '@/utils/errors'

const api = vi.hoisted(() => ({ previewUnderstandingQuestion: vi.fn() }))
vi.mock('@/api/understandingPreview', () => api)

import { previewTransport } from '@/views/question/understandingTransport'

const reviewed = { kind: 'CURATED', reviewStatus: 'REVIEWED' } as const
function preview(overrides: Record<string, unknown> = {}) {
  return {
    data: {
      preview: true,
      question: { questionId: 17 },
      attempt: null,
      hints: [],
      solution: null,
      variant: null,
      variantResult: null,
      next: null,
      ...overrides,
    },
  }
}
const state = { nextHintLevel: 2, hintsUsed: 1 }

beforeEach(() => vi.clearAllMocks())

describe('previewTransport', () => {
  it('sends the whole progress each time and keeps a step only once the server accepts it', async () => {
    const transport = previewTransport(() => 17)
    api.previewUnderstandingQuestion.mockResolvedValueOnce(preview())
    await transport.question(17)
    api.previewUnderstandingQuestion.mockResolvedValueOnce(
      preview({ attempt: { correct: false, answer: 'B', misconception: null, viz: null } }),
    )
    expect(await transport.submitAttempt(17, 'B', 3)).toMatchObject({ attemptId: 0, answer: 'B' })
    api.previewUnderstandingQuestion.mockRejectedValueOnce(new BusinessError('先看完提示', 1005))
    await expect(transport.revealSolution(0)).rejects.toThrow('先看完提示')
    api.previewUnderstandingQuestion.mockResolvedValueOnce(
      preview({
        hints: [{ level: 1, text: '想一想', source: reviewed }],
        next: { action: { type: 'HINT', level: 2 }, reason: '', reasonSource: reviewed, state },
      }),
    )
    expect(await transport.nextHint(0, 'ignored')).toEqual({ level: 1, text: '想一想', source: reviewed, state })
    expect(api.previewUnderstandingQuestion.mock.calls.map((call) => call[1])).toEqual([
      {},
      { answer: 'B' },
      { answer: 'B', revealSolution: true },
      { answer: 'B', hintLevel: 1 },
    ])
  })

  it('reports a missing variant as 1004 and forgets everything on reset', async () => {
    const transport = previewTransport(() => 17)
    api.previewUnderstandingQuestion.mockResolvedValue(preview())
    await expect(transport.variant(0)).rejects.toMatchObject({ code: 1004 })
    await transport.vizStarted(0, 'MISCONCEPTION')
    transport.reset()
    await transport.tutorNext(0).catch(() => undefined)
    expect(api.previewUnderstandingQuestion.mock.calls.at(-1)?.[1]).toEqual({})
  })
})
