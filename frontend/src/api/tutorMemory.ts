import service from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export type TutorExplanationStyle = 'STEP_BY_STEP' | 'CONCISE' | 'EXAMPLES'
export interface TutorMemoryVO {
  revision: number
  explanationStyle: TutorExplanationStyle | null
  goal: string | null
}

export type TutorMemoryRequestOptions = { errorDisplay?: 'inline' }

export function getTutorMemory(courseId: number, options?: TutorMemoryRequestOptions) {
  return options
    ? service.get<unknown, ApiResponse<TutorMemoryVO>>(`/my-courses/${courseId}/tutor-memory`, options)
    : service.get<unknown, ApiResponse<TutorMemoryVO>>(`/my-courses/${courseId}/tutor-memory`)
}

export function saveTutorMemory(courseId: number, memory: TutorMemoryVO, options?: TutorMemoryRequestOptions) {
  return options
    ? service.put<unknown, ApiResponse<TutorMemoryVO>>(`/my-courses/${courseId}/tutor-memory`, memory, options)
    : service.put<unknown, ApiResponse<TutorMemoryVO>>(`/my-courses/${courseId}/tutor-memory`, memory)
}

export function deleteTutorMemory(courseId: number, revision: number, options?: TutorMemoryRequestOptions) {
  return service.delete<unknown, ApiResponse<TutorMemoryVO>>(`/my-courses/${courseId}/tutor-memory`, {
    params: { revision },
    ...(options || {}),
  })
}
