import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export type AiVariantReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface AiVariantReviewVO {
  id: number
  motherQuestionId: number
  motherQuestionContent: string
  courseId: number
  courseName: string
  questionContent: string
  questionType: string
  options: { label: string; content: string }[]
  correctAnswer: string
  analysis: string
  difficulty: number
  reviewStatus: AiVariantReviewStatus
  reviewNote: string | null
  reviewedBy: number | null
  reviewedTime: string | null
  publishedQuestionId: number | null
}

interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}

export function getAiVariantReviews(
  reviewStatus: AiVariantReviewStatus,
  pageNum = 1,
  pageSize = 10,
  options?: { errorDisplay?: 'inline' },
) {
  return request.get<unknown, ApiResponse<PageResult<AiVariantReviewVO>>>('/admin/ai-variant-reviews', {
    params: { reviewStatus, pageNum, pageSize },
    ...options,
  })
}

export function reviewAiVariant(
  variantId: number,
  decision: 'APPROVE' | 'REJECT',
  reviewNote: string,
  options?: { errorDisplay?: 'inline' },
) {
  if (!options)
    return request.post<unknown, ApiResponse<AiVariantReviewVO>>(`/admin/ai-variant-reviews/${variantId}`, {
      decision,
      reviewNote,
    })
  return request.post<unknown, ApiResponse<AiVariantReviewVO>>(
    `/admin/ai-variant-reviews/${variantId}`,
    { decision, reviewNote },
    options,
  )
}
