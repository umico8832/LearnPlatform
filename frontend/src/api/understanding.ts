import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { SceneSpec } from '@/viz'

/** 题目理解：一道题从错到懂。契约见 docs/reference/api/question-understanding.md。 */

export interface UnderstandingOption {
  label: string
  content: string
}

export interface UnderstandingPosition {
  courseId: number
  courseName: string
  chapterName: string
  knowledgePointId: number
  knowledgePointName: string
}

export type SourceKind = 'OFFICIAL_EXAM' | 'AI_GENERATED' | 'MANUAL'
export type SourceVerification = 'VERIFIED' | 'REVIEWED' | 'UNREVIEWED'

export interface QuestionSource {
  kind: SourceKind
  title: string | null
  url: string | null
  verification: SourceVerification
}

export interface UnderstandingViz {
  title: string
  spec: SceneSpec
}

export interface UnderstandingQuestion {
  questionId: number
  stem: string
  questionType: 'SINGLE_CHOICE'
  options: UnderstandingOption[]
  position: UnderstandingPosition
  source: QuestionSource
}

export interface Misconception {
  title: string
  explanation: string
  source: { kind: 'CURATED' | 'AI_GENERATED'; reviewStatus: 'REVIEWED' }
}

export interface AttemptResult {
  attemptId: number
  correct: boolean
  answer: string
  misconception: Misconception | null
  viz: UnderstandingViz | null
}

export interface Solution {
  correctAnswer: string
  explanation: string
  viz: UnderstandingViz | null
}

export interface Variant {
  variantId: number
  stem: string
  options: UnderstandingOption[]
  changedCondition: string
  source: QuestionSource
}

export interface VariantResult {
  variantAttemptId: number
  correct: boolean
  correctAnswer: string
  explanation: string
  viz: UnderstandingViz | null
  assistance: 'UNAIDED'
}

const inline = { errorDisplay: 'inline' as const }

export function getUnderstandingQuestion(questionId: number) {
  return request.get<unknown, ApiResponse<UnderstandingQuestion>>(`/understanding/questions/${questionId}`, inline)
}

export function submitAttempt(questionId: number, answer: string, answerTime?: number) {
  return request.post<unknown, ApiResponse<AttemptResult>>(
    `/understanding/questions/${questionId}/attempts`,
    { answer, answerTime },
    inline,
  )
}

export function revealSolution(attemptId: number) {
  return request.post<unknown, ApiResponse<Solution>>(
    `/understanding/attempts/${attemptId}/solution/reveal`,
    null,
    inline,
  )
}

export function getVariant(attemptId: number) {
  return request.get<unknown, ApiResponse<Variant>>(`/understanding/attempts/${attemptId}/variant`, inline)
}

export function submitVariantAttempt(variantId: number, originAttemptId: number, answer: string, answerTime?: number) {
  return request.post<unknown, ApiResponse<VariantResult>>(
    `/understanding/variants/${variantId}/attempts`,
    { answer, answerTime, originAttemptId },
    inline,
  )
}
