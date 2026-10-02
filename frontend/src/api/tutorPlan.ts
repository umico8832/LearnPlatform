import { aiService } from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export interface TutorAgentPlanStepVO {
  type: 'TUTOR' | 'COURSE_SEQUENCE' | 'DUE_REVIEW' | 'WRONG_QUESTION'
  title: string
  reason: string
  knowledgePointId?: number | null
  questionId?: number | null
}

export interface TutorAgentPlanVO {
  steps: TutorAgentPlanStepVO[]
  confirmed: boolean
  confirmedAt: string | null
  available: boolean
}

function planPath(courseId: number, sessionKey: string, runKey: string, sequence: number) {
  return `/my-courses/${courseId}/tutor-sessions/${sessionKey}/agent-runs/${runKey}/messages/${sequence}/plan`
}

export type TutorPlanRequestOptions = { errorDisplay?: 'inline' }

export function getTutorAgentPlan(
  courseId: number,
  sessionKey: string,
  runKey: string,
  sequence: number,
  options?: TutorPlanRequestOptions,
) {
  return aiService
    .get<ApiResponse<TutorAgentPlanVO>>(planPath(courseId, sessionKey, runKey, sequence), ...(options ? [options] : []))
    .then((response) => response.data)
}

export function confirmTutorAgentPlan(
  courseId: number,
  sessionKey: string,
  runKey: string,
  sequence: number,
  options?: TutorPlanRequestOptions,
) {
  return aiService
    .post<ApiResponse<TutorAgentPlanVO>>(
      planPath(courseId, sessionKey, runKey, sequence) + '/confirm',
      undefined,
      ...(options ? [options] : []),
    )
    .then((response) => response.data)
}
