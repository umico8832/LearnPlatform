import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: {
    get: vi.fn(),
  },
}))

import request from '@/utils/request'
import { getAiUsageOverview, getAiUsageReport } from '@/api/aiUsage'

const mockedRequest = vi.mocked(request)

describe('AI usage API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('uses the admin endpoint relative to the shared /api base URL', async () => {
    const overview = { totalCalls: 12 }
    mockedRequest.get.mockResolvedValue({ code: 0, data: overview, message: 'success' })

    const result = await getAiUsageOverview(30)

    expect(mockedRequest.get).toHaveBeenCalledWith('/admin/ai-usage/overview', {
      params: { days: 30 },
    })
    expect(result).toEqual({ code: 0, data: overview, message: 'success' })
  })

  it('omits the days parameter when no time range is specified', async () => {
    mockedRequest.get.mockResolvedValue({ code: 0, data: {}, message: 'success' })

    await getAiUsageOverview()

    expect(mockedRequest.get).toHaveBeenCalledWith('/admin/ai-usage/overview', {
      params: {},
    })
  })

  it('uses the report endpoint for operational reporting', async () => {
    mockedRequest.get.mockResolvedValue({ code: 0, data: { days: 7 }, message: 'success' })

    await getAiUsageReport(7)

    expect(mockedRequest.get).toHaveBeenCalledWith('/admin/ai-usage/report', {
      params: { days: 7 },
    })
  })
})
