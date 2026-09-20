import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({
  aiService: {
    get: vi.fn(),
    post: vi.fn(),
  },
}))

import { aiService } from '@/utils/request'
import { getTutorAgentRun, resumeTutorAgentRun, startTutorAgentRun } from '@/api/tutor'

const mockedAiService = vi.mocked(aiService)

describe('Tutor Agent API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedAiService.post.mockResolvedValue({ data: { code: 0, data: {}, message: 'success' } })
    mockedAiService.get.mockResolvedValue({ data: { code: 0, data: {}, message: 'success' } })
  })

  it('creates, resumes, and loads a run under its owned Tutor session', async () => {
    await startTutorAgentRun(10, 'session', '解释一下')
    await resumeTutorAgentRun(10, 'session', 'run', '换个例子')
    await getTutorAgentRun(10, 'session', 'run')

    expect(mockedAiService.post).toHaveBeenNthCalledWith(1, '/my-courses/10/tutor-sessions/session/agent-runs', {
      message: '解释一下',
    })
    expect(mockedAiService.post).toHaveBeenNthCalledWith(
      2,
      '/my-courses/10/tutor-sessions/session/agent-runs/run/messages',
      { message: '换个例子' },
    )
    expect(mockedAiService.get).toHaveBeenCalledWith('/my-courses/10/tutor-sessions/session/agent-runs/run')
  })
})
