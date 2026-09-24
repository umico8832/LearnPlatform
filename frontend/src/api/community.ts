import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { SubmissionForm, QuestionSubmissionVO } from './submission'

export interface CommunityCategory {
  id: string
  parentId: string | null
  kind: 'EXAM' | 'SUBJECT' | 'SCHOOL'
  name: string
  description: string
}
export interface CommunityAttachment {
  id: number
  name: string
  sizeBytes: number
}
export interface CommunityPost {
  id: number
  userId: number
  authorName: string
  title: string
  body: string
  contentType: string
  subjectId: string
  subjectName: string
  examName: string
  schoolId: string | null
  schoolName: string | null
  courseId: number | null
  courseName: string | null
  knowledgePointId: number | null
  conceptName: string
  sourceNote: string
  status: string
  reviewNote: string
  createdAt: string
  likeCount: number
  commentCount: number
  liked: boolean
  attachments: CommunityAttachment[]
  questionLinks: { submissionId: number; status: number; questionId: number | null }[]
}
export interface CommunityDraft {
  title: string
  body: string
  contentType: string
  subjectId: string
  schoolId?: string
  courseId?: number
  knowledgePointId?: number
  conceptName: string
  sourceNote: string
}
export interface CommunityComment {
  id: number
  postId: number
  userId: number
  authorName: string
  parentId: number | null
  replyToName: string | null
  body: string
  deleted: boolean
  createdAt: string
  likeCount: number
  liked: boolean
}
export interface CommunityPage<T> {
  records: T[]
  total: number
  current: number
  size: number
}
export interface CommunityQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string
  contentType?: string
  examId?: string
  subjectId?: string
  schoolId?: string
  courseId?: number
  mine?: boolean
  status?: string
}
export interface CommunityReview {
  id: number
  reviewerName: string
  decision: string
  note: string
  createdAt: string
}
export const communityTypes: Record<string, string> = {
  TOPIC: '知识讨论',
  question_bank: '题库投稿',
  explanation: '知识解释',
  analogy: '生活类比',
  common_mistake: '易错点',
  exam_method: '做题方法',
  syllabus: '考试大纲',
}
export const communityStatuses: Record<string, string> = {
  PENDING: '待审核',
  APPROVED: '已公开',
  REJECTED: '已驳回',
  HIDDEN: '已隐藏',
}
export const communityFormats = '.pdf,.doc,.docx,.wps,.xls,.xlsx,.et,.csv,.json,.txt,.md,.zip'
export const getCommunityCategories = () =>
  request.get<unknown, ApiResponse<CommunityCategory[]>>('/community/categories')
export const getCommunityPosts = (params: CommunityQuery, admin = false) =>
  request.get<unknown, ApiResponse<CommunityPage<CommunityPost>>>(
    admin ? '/admin/community/posts' : '/community/posts',
    { params },
  )
export const getCommunityPost = (id: number) =>
  request.get<unknown, ApiResponse<CommunityPost>>(`/community/posts/${id}`)
export function createCommunityPost(post: CommunityDraft, files: File[]) {
  const form = new FormData()
  form.append('post', new Blob([JSON.stringify(post)], { type: 'application/json' }))
  files.forEach((file) => form.append('files', file))
  return request.post<unknown, ApiResponse<number>>('/community/posts', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 180000,
  })
}
export const deleteCommunityPost = (id: number) => request.delete(`/community/posts/${id}`)
export const getCommunityComments = (id: number, pageNum = 1) =>
  request.get<unknown, ApiResponse<CommunityPage<CommunityComment>>>(`/community/posts/${id}/comments`, {
    params: { pageNum, pageSize: 20 },
  })
export const addCommunityComment = (id: number, body: string, parentId?: number) =>
  request.post<unknown, ApiResponse<number>>(`/community/posts/${id}/comments`, { body, parentId })
export const deleteCommunityComment = (id: number) => request.delete(`/community/comments/${id}`)
export function setCommunityLike(id: number, liked: boolean, commentId?: number) {
  const url = `/community/posts/${id}${commentId ? `/comments/${commentId}` : ''}/like`
  return liked ? request.put(url) : request.delete(url)
}
export async function downloadCommunityAttachment(file: CommunityAttachment) {
  const response = await request.get<Blob>(`/community/attachments/${file.id}`, {
    responseType: 'blob',
    timeout: 60000,
  })
  const url = URL.createObjectURL(response.data)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = file.name
  anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
export const reviewCommunityPost = (id: number, decision: string, note: string) =>
  request.post(`/admin/community/posts/${id}/review`, { decision, note })
export const getCommunityReviews = (id: number) =>
  request.get<unknown, ApiResponse<CommunityReview[]>>(`/community/posts/${id}/reviews`)
export const createCommunitySchool = (name: string, description: string) =>
  request.post('/admin/community/schools', { name, description })
export const prepareCommunityQuestion = (id: number, form: SubmissionForm, requestKey: string) =>
  request.post<unknown, ApiResponse<QuestionSubmissionVO>>(`/admin/community/posts/${id}/questions`, form, {
    headers: { 'Idempotency-Key': requestKey },
  })
