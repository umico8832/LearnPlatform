import { aiService } from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { PracticeResultVO } from '@/api/practice'
import type { TutorAgentPlanStepVO } from '@/api/tutorPlan'

export interface TutorAgentMessageVO {
  sequence: number
  role: 'USER' | 'ASSISTANT'
  content: string
  createTime: string | null
  actions: TutorAgentActionVO[]
}

export type TutorAgentActionVO =
  | { type: 'CHECK' }
  | { type: 'HINT'; level: 1 | 2 | 3 }
  | { type: 'PRACTICE'; questionId: number }
  | { type: 'PLAN'; steps: TutorAgentPlanStepVO[] }
export interface TutorAgentPracticeVO {
  question: {
    id: number
    content: string
    questionType: 'SINGLE_CHOICE'
    options: { label: string; content: string }[]
  }
  result: PracticeResultVO | null
}

export interface TutorAgentRunVO {
  runKey: string
  status: 'RUNNING' | 'WAITING_USER' | 'FAILED'
  messages: TutorAgentMessageVO[]
}

function agentPath(courseId: number, sessionKey: string) {
  return `/my-courses/${courseId}/tutor-sessions/${sessionKey}/agent-runs`
}
function practicePath(courseId: number, sessionKey: string, runKey: string, sequence: number) {
  return `${agentPath(courseId, sessionKey)}/${runKey}/messages/${sequence}/practice`
}
export type TutorRequestOptions = { errorDisplay?: 'inline' }

export function getTutorAgentPractice(
  courseId: number,
  sessionKey: string,
  runKey: string,
  sequence: number,
  options?: TutorRequestOptions,
) {
  return aiService
    .get<ApiResponse<TutorAgentPracticeVO>>(
      practicePath(courseId, sessionKey, runKey, sequence),
      ...(options ? [options] : []),
    )
    .then((response) => response.data)
}
export function submitTutorAgentPractice(
  courseId: number,
  sessionKey: string,
  runKey: string,
  sequence: number,
  userAnswer: string,
  answerTime?: number,
  options?: TutorRequestOptions,
) {
  return aiService
    .post<ApiResponse<TutorAgentPracticeVO>>(
      practicePath(courseId, sessionKey, runKey, sequence) + '/answer',
      {
        userAnswer,
        ...(answerTime === undefined ? {} : { answerTime }),
      },
      ...(options ? [options] : []),
    )
    .then((response) => response.data)
}

export function startTutorAgentRun(
  courseId: number,
  sessionKey: string,
  message: string,
  options?: TutorRequestOptions,
) {
  return aiService
    .post<ApiResponse<TutorAgentRunVO>>(agentPath(courseId, sessionKey), { message }, ...(options ? [options] : []))
    .then((response) => response.data)
}

export function resumeTutorAgentRun(
  courseId: number,
  sessionKey: string,
  runKey: string,
  message: string,
  options?: TutorRequestOptions,
) {
  return aiService
    .post<ApiResponse<TutorAgentRunVO>>(
      `${agentPath(courseId, sessionKey)}/${runKey}/messages`,
      { message },
      ...(options ? [options] : []),
    )
    .then((response) => response.data)
}

export function getTutorAgentRun(courseId: number, sessionKey: string, runKey: string, options?: TutorRequestOptions) {
  return aiService
    .get<ApiResponse<TutorAgentRunVO>>(`${agentPath(courseId, sessionKey)}/${runKey}`, ...(options ? [options] : []))
    .then((response) => response.data)
}

export function getLatestTutorAgentRun(courseId: number, sessionKey: string, options?: TutorRequestOptions) {
  return aiService
    .get<ApiResponse<TutorAgentRunVO | null>>(
      `${agentPath(courseId, sessionKey)}/latest`,
      ...(options ? [options] : []),
    )
    .then((response) => response.data)
}
