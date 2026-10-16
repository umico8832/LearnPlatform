import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type {
  AttemptResult,
  Solution,
  UnderstandingContentSource,
  UnderstandingQuestion,
  UnderstandingViz,
  Variant,
  VariantResult,
} from './understanding'

export interface PreviewProgress {
  answer?: string
  hintLevel?: number
  misconceptionVizViewed?: boolean
  solutionVizViewed?: boolean
  revealSolution?: boolean
  variantAnswer?: string
}

export interface TutorState {
  nextHintLevel: number | null
  hintsUsed: number
  misconceptionVizViewed: boolean
  solutionVizViewed: boolean
  solutionRevealed: boolean
  variantAttempted: boolean
}

export type TutorAction =
  | { type: 'HINT'; level: number }
  | { type: 'PLAY_VIZ'; context: 'MISCONCEPTION' | 'SOLUTION'; viz: UnderstandingViz }
  | { type: 'REVEAL_SOLUTION' }
  | { type: 'TRY_VARIANT'; variantId: number }
  | { type: 'DONE' }

export interface TutorNext {
  action: TutorAction
  reason: string
  reasonSource: UnderstandingContentSource
  state: TutorState
}

export interface UnderstandingPreview {
  preview: true
  question: UnderstandingQuestion
  attempt: Omit<AttemptResult, 'attemptId'> | null
  hints: { level: number; text: string; source: UnderstandingContentSource }[]
  solution: Solution | null
  variant: Variant | null
  variantResult: Omit<VariantResult, 'variantAttemptId'> | null
  next: TutorNext | null
}

export function previewUnderstandingQuestion(questionId: number, progress: PreviewProgress = {}) {
  return request.post<unknown, ApiResponse<UnderstandingPreview>>(
    `/understanding/preview/questions/${questionId}`,
    progress,
    { errorDisplay: 'inline' },
  )
}
