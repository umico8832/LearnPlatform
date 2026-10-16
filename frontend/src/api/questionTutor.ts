import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { UnderstandingContentSource, UnderstandingViz } from './understanding'

// 题目导师（M3）：契约见 docs/reference/api/question-tutor.md

export type HintLevel = 1 | 2 | 3
export type VizContext = 'MISCONCEPTION' | 'SOLUTION'

export interface TutorState {
  nextHintLevel: HintLevel | null
  hintsUsed: 0 | HintLevel
  misconceptionVizViewed: boolean
  solutionVizViewed: boolean
  solutionRevealed: boolean
  variantAttempted: boolean
}

export type TutorAction =
  | { type: 'HINT'; level: HintLevel }
  | { type: 'PLAY_VIZ'; context: VizContext; viz: UnderstandingViz }
  | { type: 'REVEAL_SOLUTION' }
  | { type: 'TRY_VARIANT'; variantId: number }
  | { type: 'DONE' }

export interface TutorNext {
  action: TutorAction
  reason: string
  reasonSource: UnderstandingContentSource
  state: TutorState
}

export interface TutorHint {
  level: HintLevel
  text: string
  source: UnderstandingContentSource
  state: TutorState
}

export interface TutorVizView {
  context: VizContext
  viewed: true
  state: TutorState
}

const inline = { errorDisplay: 'inline' as const }

export function getTutorNext(attemptId: number) {
  return request.get<unknown, ApiResponse<TutorNext>>(`/understanding/attempts/${attemptId}/tutor/next`, inline)
}

/** The server picks the level; retries must reuse the same `requestId` so one click advances at most one level. */
export function requestNextHint(attemptId: number, requestId: string) {
  return request.post<unknown, ApiResponse<TutorHint>>(
    `/understanding/attempts/${attemptId}/tutor/hints/next`,
    { requestId },
    inline,
  )
}

export function recordVizView(attemptId: number, context: VizContext) {
  return request.post<unknown, ApiResponse<TutorVizView>>(
    `/understanding/attempts/${attemptId}/tutor/viz-views`,
    { context },
    inline,
  )
}
