import service from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export type TutorCheckStatus = 'UNANSWERED' | 'CORRECT' | 'INCORRECT' | null

export interface TutorSessionNoteSource {
  available: boolean
  knowledgePointId: number | null
  title: string | null
  sessionStartedAt: string | null
  checkStatus: TutorCheckStatus
  checkAnsweredAt: string | null
}

export interface TutorSessionNoteVO {
  sessionKey: string
  revision: number
  note: string | null
  updatedAt: string | null
  source: TutorSessionNoteSource
}

export interface TutorSessionNotesPage {
  records: TutorSessionNoteVO[]
  total: number
  current: number
  size: number
}

export type TutorNotesRequestOptions = { errorDisplay?: 'inline' }

export function getTutorSessionNotes(courseId: number, page: number, options?: TutorNotesRequestOptions) {
  return service.get<unknown, ApiResponse<TutorSessionNotesPage>>(`/my-courses/${courseId}/tutor-notes`, {
    params: { page },
    ...(options || {}),
  })
}

export function getTutorSessionNote(courseId: number, sessionKey: string, options?: TutorNotesRequestOptions) {
  return options
    ? service.get<unknown, ApiResponse<TutorSessionNoteVO>>(
        `/my-courses/${courseId}/tutor-sessions/${sessionKey}/note`,
        options,
      )
    : service.get<unknown, ApiResponse<TutorSessionNoteVO>>(`/my-courses/${courseId}/tutor-sessions/${sessionKey}/note`)
}

export function saveTutorSessionNote(
  courseId: number,
  sessionKey: string,
  note: { revision: number; note: string },
  options?: TutorNotesRequestOptions,
) {
  return options
    ? service.put<unknown, ApiResponse<TutorSessionNoteVO>>(
        `/my-courses/${courseId}/tutor-sessions/${sessionKey}/note`,
        note,
        options,
      )
    : service.put<unknown, ApiResponse<TutorSessionNoteVO>>(
        `/my-courses/${courseId}/tutor-sessions/${sessionKey}/note`,
        note,
      )
}

export function deleteTutorSessionNote(
  courseId: number,
  sessionKey: string,
  revision: number,
  options?: TutorNotesRequestOptions,
) {
  return service.delete<unknown, ApiResponse<TutorSessionNoteVO>>(
    `/my-courses/${courseId}/tutor-sessions/${sessionKey}/note`,
    { params: { revision }, ...(options || {}) },
  )
}
