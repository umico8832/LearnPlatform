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

export function getTutorAgentPlan(courseId: number, sessionKey: string, runKey: string, sequence: number) {
  return aiService
    .get<ApiResponse<TutorAgentPlanVO>>(planPath(courseId, sessionKey, runKey, sequence))
    .then((response) => response.data)
}

export function confirmTutorAgentPlan(courseId: number, sessionKey: string, runKey: string, sequence: number) {
  return aiService
    .post<ApiResponse<TutorAgentPlanVO>>(planPath(courseId, sessionKey, runKey, sequence) + '/confirm')
    .then((response) => response.data)
}
