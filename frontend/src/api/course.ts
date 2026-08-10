import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

/** 课程 VO */
export interface CourseVO {
  id: number
  name: string
  description: string
  coverImage: string | null
  sortOrder: number
  status: number
  createTime: string
}

/** 当前用户课程库中的课程 */
export interface UserCourseVO {
  id: number
  courseId: number
  courseName: string
  description: string | null
  coverImage: string | null
  contentKey: string | null
  contentSource: string | null
  addedAt: string
}

/** 已加入课程的真实学习事实聚合，不包含推断掌握度。 */
export interface CourseOverviewVO {
  courseId: number
  courseName: string
  answeredCount: number
  correctCount: number
  dueReviewCount: number
  unresolvedWrongCount: number
  lastLearningTime: string | null
  recommendedTargets: LearningTargetVO[]
  tutorProgress: TutorProgressVO[]
}

export interface LearningTargetVO {
  type: 'TUTOR' | 'DUE_REVIEW' | 'WRONG_QUESTION' | 'COURSE_SEQUENCE'
  title: string
  reason: string
  questionId: number | null
  knowledgePointId: number | null
}
export interface TutorProgressVO {
  knowledgePointId: number
  title: string
  status: 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED'
}
export interface ArrayStackInsertionCourseware {
  kind: 'ARRAY_STACK_INSERTION'
  version: 1
  capacity: number
  initialElements: string[]
  insertIndex: number
  insertValue: string
}
export interface ArrayStackResizeCourseware {
  kind: 'ARRAY_STACK_RESIZE'
  version: 1
  previousCapacity: number
  initialElements: string[]
}
export interface ArrayQueueRepresentationCourseware {
  kind: 'ARRAY_QUEUE_REPRESENTATION'
  version: 1
  capacity: number
  headIndex: number
  elements: string[]
}
export interface ArrayQueueEnqueueCourseware {
  kind: 'ARRAY_QUEUE_ENQUEUE'
  version: 1
  capacity: number
  headIndex: number
  elements: string[]
  enqueueValue: string
}
export interface ArrayQueueDequeueCourseware {
  kind: 'ARRAY_QUEUE_DEQUEUE'
  version: 1
  capacity: number
  headIndex: number
  elements: string[]
}
export interface ArrayQueueResizeCourseware {
  kind: 'ARRAY_QUEUE_RESIZE'
  version: 1
  previousCapacity: number
  headIndex: number
  elements: string[]
}
export interface ArrayDequeRepresentationCourseware {
  kind: 'ARRAY_DEQUE_REPRESENTATION'
  version: 1
  capacity: number
  headIndex: number
  elements: string[]
  accessIndex: number
}
export interface ArrayDequeFrontShiftInsertCourseware {
  kind: 'ARRAY_DEQUE_FRONT_SHIFT_INSERT'
  version: 1
  capacity: number
  headIndex: number
  elements: string[]
  insertIndex: number
  insertValue: string
}
export interface TutorLearningPathItem { contentKey: string; title: string; description: string }
export interface TutorSessionVO { sessionKey: string; title: string; lesson: { summary: string; steps: string[]; visualizationId: string; visualization?: ArrayStackInsertionCourseware | ArrayStackResizeCourseware | ArrayQueueRepresentationCourseware | ArrayQueueEnqueueCourseware | ArrayQueueDequeueCourseware | ArrayQueueResizeCourseware | ArrayDequeRepresentationCourseware | ArrayDequeFrontShiftInsertCourseware; prerequisite?: TutorLearningPathItem; nextStep?: TutorLearningPathItem }; check: { id: string; prompt: string; options: { id: string; text: string }[] } }
export interface TutorCheckResultVO { correct: boolean; explanation: string; guidanceType: 'PREREQUISITE' | 'NEXT_TARGET' | null; guidanceTitle: string | null; guidanceDescription: string | null; guidanceKnowledgePointId: number | null }

/** 仅接受当前已审查、无可执行字段的 ArrayStack 课件参数。 */
export function isArrayStackInsertionCourseware(value: unknown): value is ArrayStackInsertionCourseware {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Record<string, unknown>
  return candidate.kind === 'ARRAY_STACK_INSERTION'
    && candidate.version === 1
    && Number.isInteger(candidate.capacity) && (candidate.capacity as number) >= 1 && (candidate.capacity as number) <= 12
    && Array.isArray(candidate.initialElements) && candidate.initialElements.length < (candidate.capacity as number)
    && candidate.initialElements.every((item) => typeof item === 'string' && item.length > 0 && item.length <= 32)
    && Number.isInteger(candidate.insertIndex) && (candidate.insertIndex as number) >= 0 && (candidate.insertIndex as number) <= candidate.initialElements.length
    && typeof candidate.insertValue === 'string' && candidate.insertValue.length > 0 && candidate.insertValue.length <= 32
}

/** 仅接受当前已审查、无可执行字段的 ArrayStack 容量调整课件参数。 */
export function isArrayStackResizeCourseware(value: unknown): value is ArrayStackResizeCourseware {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Record<string, unknown>
  return candidate.kind === 'ARRAY_STACK_RESIZE'
    && candidate.version === 1
    && Number.isInteger(candidate.previousCapacity) && (candidate.previousCapacity as number) >= 1 && (candidate.previousCapacity as number) <= 12
    && Array.isArray(candidate.initialElements) && candidate.initialElements.length > 0
    && candidate.initialElements.length === candidate.previousCapacity
    && candidate.initialElements.every((item) => typeof item === 'string' && item.length > 0 && item.length <= 32)
}

/** 仅接受当前已审查、无可执行字段的 ArrayQueue 循环数组课件参数。 */
export function isArrayQueueRepresentationCourseware(value: unknown): value is ArrayQueueRepresentationCourseware {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Record<string, unknown>
  return Object.keys(candidate).every((key) => ['kind', 'version', 'capacity', 'headIndex', 'elements'].includes(key))
    && candidate.kind === 'ARRAY_QUEUE_REPRESENTATION'
    && candidate.version === 1
    && Number.isInteger(candidate.capacity) && (candidate.capacity as number) >= 2 && (candidate.capacity as number) <= 12
    && Number.isInteger(candidate.headIndex) && (candidate.headIndex as number) >= 0 && (candidate.headIndex as number) < (candidate.capacity as number)
    && Array.isArray(candidate.elements) && candidate.elements.length > 0 && candidate.elements.length < (candidate.capacity as number)
    && candidate.elements.every((item) => typeof item === 'string' && item.length > 0 && item.length <= 32)
}

/** 仅接受容量充足、无可执行字段的 ArrayQueue 入队课件参数。 */
export function isArrayQueueEnqueueCourseware(value: unknown): value is ArrayQueueEnqueueCourseware {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Record<string, unknown>
  return Object.keys(candidate).every((key) => ['kind', 'version', 'capacity', 'headIndex', 'elements', 'enqueueValue'].includes(key))
    && candidate.kind === 'ARRAY_QUEUE_ENQUEUE'
    && candidate.version === 1
    && Number.isInteger(candidate.capacity) && (candidate.capacity as number) >= 2 && (candidate.capacity as number) <= 12
    && Number.isInteger(candidate.headIndex) && (candidate.headIndex as number) >= 0 && (candidate.headIndex as number) < (candidate.capacity as number)
    && Array.isArray(candidate.elements) && candidate.elements.length > 0 && candidate.elements.length < (candidate.capacity as number)
    && candidate.elements.every((item) => typeof item === 'string' && item.length > 0 && item.length <= 32)
    && typeof candidate.enqueueValue === 'string' && candidate.enqueueValue.length > 0 && candidate.enqueueValue.length <= 32
}

/** 仅接受非空、无可执行字段的 ArrayQueue 出队课件参数。 */
export function isArrayQueueDequeueCourseware(value: unknown): value is ArrayQueueDequeueCourseware {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Record<string, unknown>
  return Object.keys(candidate).every((key) => ['kind', 'version', 'capacity', 'headIndex', 'elements'].includes(key))
    && candidate.kind === 'ARRAY_QUEUE_DEQUEUE'
    && candidate.version === 1
    && Number.isInteger(candidate.capacity) && (candidate.capacity as number) >= 2 && (candidate.capacity as number) <= 12
    && Number.isInteger(candidate.headIndex) && (candidate.headIndex as number) >= 0 && (candidate.headIndex as number) < (candidate.capacity as number)
    && Array.isArray(candidate.elements) && candidate.elements.length > 0 && candidate.elements.length <= (candidate.capacity as number)
    && candidate.elements.every((item) => typeof item === 'string' && item.length > 0 && item.length <= 32)
}

/** 仅接受跨界、无可执行字段的 ArrayQueue 线性化复制课件参数。 */
export function isArrayQueueResizeCourseware(value: unknown): value is ArrayQueueResizeCourseware {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Record<string, unknown>
  return Object.keys(candidate).every((key) => ['kind', 'version', 'previousCapacity', 'headIndex', 'elements'].includes(key))
    && candidate.kind === 'ARRAY_QUEUE_RESIZE'
    && candidate.version === 1
    && Number.isInteger(candidate.previousCapacity) && (candidate.previousCapacity as number) >= 2 && (candidate.previousCapacity as number) <= 12
    && Number.isInteger(candidate.headIndex) && (candidate.headIndex as number) >= 0 && (candidate.headIndex as number) < (candidate.previousCapacity as number)
    && Array.isArray(candidate.elements) && candidate.elements.length > 0 && candidate.elements.length <= (candidate.previousCapacity as number)
    && (candidate.headIndex as number) + candidate.elements.length > (candidate.previousCapacity as number)
    && candidate.elements.every((item) => typeof item === 'string' && item.length > 0 && item.length <= 32)
}

/** 仅接受已审查、无可执行字段的 ArrayDeque 逻辑访问课件参数。 */
export function isArrayDequeRepresentationCourseware(value: unknown): value is ArrayDequeRepresentationCourseware {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Record<string, unknown>
  return Object.keys(candidate).every((key) => ['kind', 'version', 'capacity', 'headIndex', 'elements', 'accessIndex'].includes(key))
    && candidate.kind === 'ARRAY_DEQUE_REPRESENTATION'
    && candidate.version === 1
    && Number.isInteger(candidate.capacity) && (candidate.capacity as number) >= 2 && (candidate.capacity as number) <= 12
    && Number.isInteger(candidate.headIndex) && (candidate.headIndex as number) >= 0 && (candidate.headIndex as number) < (candidate.capacity as number)
    && Array.isArray(candidate.elements) && candidate.elements.length > 0 && candidate.elements.length <= (candidate.capacity as number)
    && candidate.elements.every((item) => typeof item === 'string' && item.length > 0 && item.length <= 32)
    && Number.isInteger(candidate.accessIndex) && (candidate.accessIndex as number) >= 0 && (candidate.accessIndex as number) < candidate.elements.length
}

/** 仅接受已审查、靠近逻辑前端插入的 ArrayDeque 课件参数。 */
export function isArrayDequeFrontShiftInsertCourseware(value: unknown): value is ArrayDequeFrontShiftInsertCourseware {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Record<string, unknown>
  return Object.keys(candidate).every((key) => ['kind', 'version', 'capacity', 'headIndex', 'elements', 'insertIndex', 'insertValue'].includes(key))
    && candidate.kind === 'ARRAY_DEQUE_FRONT_SHIFT_INSERT'
    && candidate.version === 1
    && Number.isInteger(candidate.capacity) && (candidate.capacity as number) >= 3 && (candidate.capacity as number) <= 12
    && Number.isInteger(candidate.headIndex) && (candidate.headIndex as number) >= 0 && (candidate.headIndex as number) < (candidate.capacity as number)
    && Array.isArray(candidate.elements) && candidate.elements.length >= 2 && candidate.elements.length < (candidate.capacity as number)
    && candidate.elements.every((item) => typeof item === 'string' && item.length > 0 && item.length <= 32)
    && Number.isInteger(candidate.insertIndex) && (candidate.insertIndex as number) > 0 && (candidate.insertIndex as number) < candidate.elements.length / 2
    && typeof candidate.insertValue === 'string' && candidate.insertValue.length > 0 && candidate.insertValue.length <= 32
}

/** 创建/更新课程请求 */
export interface CourseForm {
  name: string
  description?: string
  sortOrder?: number
}

/** 分页结果 */
interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

/** 获取所有启用课程（不分页） */
export function getAllCourses() {
  return request.get<unknown, ApiResponse<CourseVO[]>>('/courses/list')
}

/** 获取课程分页 */
export function getCoursePage(params: { pageNum?: number; pageSize?: number; keyword?: string }) {
  return request.get<unknown, ApiResponse<PageResult<CourseVO>>>('/courses', { params })
}

/** 获取课程详情 */
export function getCourseById(id: number) {
  return request.get<unknown, ApiResponse<CourseVO>>(`/courses/${id}`)
}

/** 获取当前用户的个人课程库 */
export function getMyCourses() {
  return request.get<unknown, ApiResponse<UserCourseVO[]>>('/my-courses')
}

/** 幂等地将课程加入当前用户的个人课程库 */
export function addCourseToLibrary(courseId: number) {
  return request.post<unknown, ApiResponse<UserCourseVO>>(`/my-courses/${courseId}`)
}

/** 获取当前用户已加入课程的学习总览。 */
export function getCourseOverview(courseId: number) {
  return request.get<unknown, ApiResponse<CourseOverviewVO>>(`/my-courses/${courseId}/overview`)
}
export function startTutorSession(courseId: number, knowledgePointId: number) {
  return request.post<unknown, ApiResponse<TutorSessionVO>>(`/my-courses/${courseId}/tutor-sessions`, undefined, { params: { knowledgePointId } })
}
export function submitTutorCheck(courseId: number, sessionKey: string, optionId: string) {
  return request.post<unknown, ApiResponse<TutorCheckResultVO>>(`/my-courses/${courseId}/tutor-sessions/${sessionKey}/check`, { optionId })
}

/** 创建课程（管理端） */
export function createCourse(data: CourseForm) {
  return request.post<unknown, ApiResponse<CourseVO>>('/admin/courses', data)
}

/** 更新课程（管理端） */
export function updateCourse(id: number, data: CourseForm) {
  return request.put<unknown, ApiResponse<CourseVO>>(`/admin/courses/${id}`, data)
}

/** 删除课程（管理端） */
export function deleteCourse(id: number) {
  return request.delete<unknown, ApiResponse<void>>(`/admin/courses/${id}`)
}
