import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({ default: { get: vi.fn(), post: vi.fn() } }))

import request from '@/utils/request'
import { getAiVariantReviews, reviewAiVariant } from '@/api/aiVariantReview'

const mockedRequest = vi.mocked(request)

describe('AI 变式题审查 API', () => {
  beforeEach(() => vi.clearAllMocks())

  it('按状态分页并提交管理员审查结论', async () => {
    mockedRequest.get.mockResolvedValue({ code: 0, data: { records: [] }, message: 'success' })
    mockedRequest.post.mockResolvedValue({ code: 0, data: { reviewStatus: 'APPROVED' }, message: 'success' })

    await getAiVariantReviews('PENDING', 2, 10)
    await reviewAiVariant(12, 'APPROVE', '核验通过')

    expect(mockedRequest.get).toHaveBeenCalledWith('/admin/ai-variant-reviews', {
      params: { reviewStatus: 'PENDING', pageNum: 2, pageSize: 10 },
    })
    expect(mockedRequest.post).toHaveBeenCalledWith('/admin/ai-variant-reviews/12', {
      decision: 'APPROVE',
      reviewNote: '核验通过',
    })
  })
})
