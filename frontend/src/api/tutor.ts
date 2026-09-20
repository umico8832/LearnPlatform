import { aiService } from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export interface TutorAgentMessageVO {
  sequence: number
  role: 'USER' | 'ASSISTANT'
  content: string
  createTime: string | null
}

export interface TutorAgentRunVO {
  runKey: string
  status: 'RUNNING' | 'WAITING_USER' | 'FAILED'
  messages: TutorAgentMessageVO[]
}

function agentPath(courseId: number, sessionKey: string) {
  return `/my-courses/${courseId}/tutor-sessions/${sessionKey}/agent-runs`
}

export function startTutorAgentRun(courseId: number, sessionKey: string, message: string) {
  return aiService
    .post<ApiResponse<TutorAgentRunVO>>(agentPath(courseId, sessionKey), { message })
    .then((response) => response.data)
}

export function resumeTutorAgentRun(courseId: number, sessionKey: string, runKey: string, message: string) {
  return aiService
    .post<ApiResponse<TutorAgentRunVO>>(`${agentPath(courseId, sessionKey)}/${runKey}/messages`, { message })
    .then((response) => response.data)
}

export function getTutorAgentRun(courseId: number, sessionKey: string, runKey: string) {
  return aiService
    .get<ApiResponse<TutorAgentRunVO>>(`${agentPath(courseId, sessionKey)}/${runKey}`)
    .then((response) => response.data)
}
