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
  latestStageAssessment: CourseStageAssessmentSummaryVO | null
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

export interface CourseStageAssessmentVO {
  id: number
  courseId: number
  status: 'IN_PROGRESS' | 'COMPLETED'
  selectionStrategy: 'LEARNING_STATE_PRIORITY' | 'COURSE_SEQUENCE_FALLBACK'
  targetKnowledgePointId: number | null
  targetKnowledgePointName: string | null
  questionCount: number
  correctCount: number | null
  startTime: string
  completeTime: string | null
  sourceComposition: CourseStageAssessmentSourceCompositionVO
  knowledgePointSummary?: CourseStageAssessmentKnowledgePointSummaryVO[]
  questions: CourseStageAssessmentQuestion[]
}

export interface CourseStageAssessmentSourceCompositionVO {
  officialExamCount: number
  manualCount: number
  userPrivateCount: number
  aiGeneratedCount: number
}

export interface CourseStageAssessmentKnowledgePointVO {
  id: number
  name: string
}

export interface CourseStageAssessmentKnowledgePointSummaryVO {
  id: number
  name: string
  questionCount: number
  correctCount: number
}

export interface CourseStageAssessmentQuestion {
  id: number
  questionId: number
  sortOrder: number
  questionType: 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE'
  sourceType: string
  sourceCategory: 'OFFICIAL_EXAM' | 'MANUAL' | 'USER_PRIVATE' | 'AI_GENERATED'
  originQuestionId: number | null
  content: string
  options: { label: string; content: string }[]
  score: number
  userAnswer: string | null
  correct: boolean | null
  correctAnswer: string | null
  analysis: string | null
  knowledgePoints?: CourseStageAssessmentKnowledgePointVO[]
}

export interface CourseStageAssessmentSummaryVO {
  id: number
  selectionStrategy: 'LEARNING_STATE_PRIORITY' | 'COURSE_SEQUENCE_FALLBACK'
  targetKnowledgePointId: number | null
  targetKnowledgePointName: string | null
  questionCount: number
  correctCount: number
  sourceComposition: CourseStageAssessmentSourceCompositionVO
  knowledgePointSummary?: CourseStageAssessmentKnowledgePointSummaryVO[]
  startTime: string
  completeTime: string
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
export interface DualArrayDequeRepresentationCourseware {
  kind: 'DUAL_ARRAY_DEQUE_REPRESENTATION'
  version: 1
  front: string[]
  back: string[]
  accessIndex: number
}
export interface DualArrayDequeBalanceCourseware {
  kind: 'DUAL_ARRAY_DEQUE_BALANCE'
  version: 1
  front: string[]
  back: string[]
}
export interface RootishArrayStackLayoutCourseware {
  kind: 'ROOTISH_ARRAY_STACK_LAYOUT'
  version: 1
  blocks: string[][]
}
export interface SequentialListStorageCourseware {
  kind: 'SEQUENTIAL_LIST_STORAGE'
  version: 1
  baseAddress: number
  elementWidth: number
  elements: string[]
  accessIndex: number
}
export interface LinkedListReversalCourseware {
  kind: 'LINKED_LIST_REVERSAL'
  version: 1
  elements: string[]
}
export interface FactorialCallStackCourseware {
  kind: 'FACTORIAL_CALL_STACK'
  version: 1
  startValue: number
}
export interface TutorLearningPathItem {
  contentKey: string
  title: string
  description: string
}
export interface TutorLearningContextVO {
  paperAnswerCount: number
  paperIncorrectCount: number
  paperAiAssistanceCount: number
  unresolvedWrongCount: number
  dueReviewCount: number
  reviewAnswerCount: number
  latestEvidenceAt: string | null
}
export interface TutorSessionVO {
  sessionKey: string
  title: string
  lesson: {
    summary: string
    steps: string[]
    visualizationId: string
    visualization?:
      | ArrayStackInsertionCourseware
      | ArrayStackResizeCourseware
      | ArrayQueueRepresentationCourseware
      | ArrayQueueEnqueueCourseware
      | ArrayQueueDequeueCourseware
      | ArrayQueueResizeCourseware
      | ArrayDequeRepresentationCourseware
      | ArrayDequeFrontShiftInsertCourseware
      | DualArrayDequeRepresentationCourseware
      | DualArrayDequeBalanceCourseware
      | RootishArrayStackLayoutCourseware
      | SequentialListStorageCourseware
      | LinkedListReversalCourseware
      | FactorialCallStackCourseware
    prerequisite?: TutorLearningPathItem
    nextStep?: TutorLearningPathItem
  }
  check: { id: string; prompt: string; options: { id: string; text: string }[] }
  learningContext: TutorLearningContextVO
}
export interface TutorCheckResultVO {
  correct: boolean
  explanation: string
  guidanceType: 'PREREQUISITE' | 'NEXT_TARGET' | null
  guidanceTitle: string | null
  guidanceDescription: string | null
  guidanceKnowledgePointId: number | null
}

/** 仅接受当前已审查、无可执行字段的 ArrayStack 课件参数。 */
export function isArrayStackInsertionCourseware(value: unknown): value is ArrayStackInsertionCourseware {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Record<string, unknown>
  return (
    candidate.kind === 'ARRAY_STACK_INSERTION' &&
    candidate.version === 1 &&
    Number.isInteger(candidate.capacity) &&
    (candidate.capacity as number) >= 1 &&
    (candidate.capacity as number) <= 12 &&
    Array.isArray(candidate.initialElements) &&
    candidate.initialElements.length < (candidate.capacity as number) &&
    candidate.initialElements.every((item) => typeof item === 'string' && item.length > 0 && item.length <= 32) &&
    Number.isInteger(candidate.insertIndex) &&
    (candidate.insertIndex as number) >= 0 &&
    (candidate.insertIndex as number) <= candidate.initialElements.length &&
    typeof candidate.insertValue === 'string' &&
    candidate.insertValue.length > 0 &&
    candidate.insertValue.length <= 32
  )
}

/** 仅接受当前已审查、无可执行字段的 ArrayStack 容量调整课件参数。 */
export function isArrayStackResizeCourseware(value: unknown): value is ArrayStackResizeCourseware {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Record<string, unknown>
  return (
    candidate.kind === 'ARRAY_STACK_RESIZE' &&
    candidate.version === 1 &&
    Number.isInteger(candidate.previousCapacity) &&
    (candidate.previousCapacity as number) >= 1 &&
    (candidate.previousCapacity as number) <= 12 &&
    Array.isArray(candidate.initialElements) &&
    candidate.initialElements.length > 0 &&
    candidate.initialElements.length === candidate.previousCapacity &&
    candidate.initialElements.every((item) => typeof item === 'string' && item.length > 0 && item.length <= 32)
  )
}

/** 仅接受当前已审查、无可执行字段的 ArrayQueue 循环数组课件参数。 */
export function isArrayQueueRepresentationCourseware(value: unknown): value is ArrayQueueRepresentationCourseware {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Record<string, unknown>
  return (
    Object.keys(candidate).every((key) => ['kind', 'version', 'capacity', 'headIndex', 'elements'].includes(key)) &&
    candidate.kind === 'ARRAY_QUEUE_REPRESENTATION' &&
    candidate.version === 1 &&
    Number.isInteger(candidate.capacity) &&
    (candidate.capacity as number) >= 2 &&
    (candidate.capacity as number) <= 12 &&
    Number.isInteger(candidate.headIndex) &&
    (candidate.headIndex as number) >= 0 &&
    (candidate.headIndex as number) < (candidate.capacity as number) &&
    Array.isArray(candidate.elements) &&
    candidate.elements.length > 0 &&
    candidate.elements.length < (candidate.capacity as number) &&
    candidate.elements.every((item) => typeof item === 'string' && item.length > 0 && item.length <= 32)
  )
}

/** 仅接受容量充足、无可执行字段的 ArrayQueue 入队课件参数。 */
export function isArrayQueueEnqueueCourseware(value: unknown): value is ArrayQueueEnqueueCourseware {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Record<string, unknown>
  return (
    Object.keys(candidate).every((key) =>
      ['kind', 'version', 'capacity', 'headIndex', 'elements', 'enqueueValue'].includes(key),
    ) &&
    candidate.kind === 'ARRAY_QUEUE_ENQUEUE' &&
    candidate.version === 1 &&
    Number.isInteger(candidate.capacity) &&
    (candidate.capacity as number) >= 2 &&
    (candidate.capacity as number) <= 12 &&
    Number.isInteger(candidate.headIndex) &&
    (candidate.headIndex as number) >= 0 &&
    (candidate.headIndex as number) < (candidate.capacity as number) &&
    Array.isArray(candidate.elements) &&
    candidate.elements.length > 0 &&
    candidate.elements.length < (candidate.capacity as number) &&
    candidate.elements.every((item) => typeof item === 'string' && item.length > 0 && item.length <= 32) &&
    typeof candidate.enqueueValue === 'string' &&
    candidate.enqueueValue.length > 0 &&
    candidate.enqueueValue.length <= 32
  )
}

/** 仅接受非空、无可执行字段的 ArrayQueue 出队课件参数。 */
export function isArrayQueueDequeueCourseware(value: unknown): value is ArrayQueueDequeueCourseware {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Record<string, unknown>
  return (
    Object.keys(candidate).every((key) => ['kind', 'version', 'capacity', 'headIndex', 'elements'].includes(key)) &&
    candidate.kind === 'ARRAY_QUEUE_DEQUEUE' &&
    candidate.version === 1 &&
    Number.isInteger(candidate.capacity) &&
    (candidate.capacity as number) >= 2 &&
    (candidate.capacity as number) <= 12 &&
    Number.isInteger(candidate.headIndex) &&
    (candidate.headIndex as number) >= 0 &&
    (candidate.headIndex as number) < (candidate.capacity as number) &&
    Array.isArray(candidate.elements) &&
    candidate.elements.length > 0 &&
    candidate.elements.length <= (candidate.capacity as number) &&
    candidate.elements.every((item) => typeof item === 'string' && item.length > 0 && item.length <= 32)
  )
}

/** 仅接受跨界、无可执行字段的 ArrayQueue 线性化复制课件参数。 */
export function isArrayQueueResizeCourseware(value: unknown): value is ArrayQueueResizeCourseware {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Record<string, unknown>
  return (
    Object.keys(candidate).every((key) =>
      ['kind', 'version', 'previousCapacity', 'headIndex', 'elements'].includes(key),
    ) &&
    candidate.kind === 'ARRAY_QUEUE_RESIZE' &&
    candidate.version === 1 &&
    Number.isInteger(candidate.previousCapacity) &&
    (candidate.previousCapacity as number) >= 2 &&
    (candidate.previousCapacity as number) <= 12 &&
    Number.isInteger(candidate.headIndex) &&
    (candidate.headIndex as number) >= 0 &&
    (candidate.headIndex as number) < (candidate.previousCapacity as number) &&
    Array.isArray(candidate.elements) &&
    candidate.elements.length > 0 &&
    candidate.elements.length <= (candidate.previousCapacity as number) &&
    (candidate.headIndex as number) + candidate.elements.length > (candidate.previousCapacity as number) &&
    candidate.elements.every((item) => typeof item === 'string' && item.length > 0 && item.length <= 32)
  )
}

/** 仅接受已审查、无可执行字段的 ArrayDeque 逻辑访问课件参数。 */
export function isArrayDequeRepresentationCourseware(value: unknown): value is ArrayDequeRepresentationCourseware {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Record<string, unknown>
  return (
    Object.keys(candidate).every((key) =>
      ['kind', 'version', 'capacity', 'headIndex', 'elements', 'accessIndex'].includes(key),
    ) &&
    candidate.kind === 'ARRAY_DEQUE_REPRESENTATION' &&
    candidate.version === 1 &&
    Number.isInteger(candidate.capacity) &&
    (candidate.capacity as number) >= 2 &&
    (candidate.capacity as number) <= 12 &&
    Number.isInteger(candidate.headIndex) &&
    (candidate.headIndex as number) >= 0 &&
    (candidate.headIndex as number) < (candidate.capacity as number) &&
    Array.isArray(candidate.elements) &&
    candidate.elements.length > 0 &&
    candidate.elements.length <= (candidate.capacity as number) &&
    candidate.elements.every((item) => typeof item === 'string' && item.length > 0 && item.length <= 32) &&
    Number.isInteger(candidate.accessIndex) &&
    (candidate.accessIndex as number) >= 0 &&
    (candidate.accessIndex as number) < candidate.elements.length
  )
}

/** 仅接受已审查、靠近逻辑前端插入的 ArrayDeque 课件参数。 */
export function isArrayDequeFrontShiftInsertCourseware(value: unknown): value is ArrayDequeFrontShiftInsertCourseware {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Record<string, unknown>
  return (
    Object.keys(candidate).every((key) =>
      ['kind', 'version', 'capacity', 'headIndex', 'elements', 'insertIndex', 'insertValue'].includes(key),
    ) &&
    candidate.kind === 'ARRAY_DEQUE_FRONT_SHIFT_INSERT' &&
    candidate.version === 1 &&
    Number.isInteger(candidate.capacity) &&
    (candidate.capacity as number) >= 3 &&
    (candidate.capacity as number) <= 12 &&
    Number.isInteger(candidate.headIndex) &&
    (candidate.headIndex as number) >= 0 &&
    (candidate.headIndex as number) < (candidate.capacity as number) &&
    Array.isArray(candidate.elements) &&
    candidate.elements.length >= 2 &&
    candidate.elements.length < (candidate.capacity as number) &&
    candidate.elements.every((item) => typeof item === 'string' && item.length > 0 && item.length <= 32) &&
    Number.isInteger(candidate.insertIndex) &&
    (candidate.insertIndex as number) > 0 &&
    (candidate.insertIndex as number) < candidate.elements.length / 2 &&
    typeof candidate.insertValue === 'string' &&
    candidate.insertValue.length > 0 &&
    candidate.insertValue.length <= 32
  )
}

/** 仅接受已审查、无可执行字段的 DualArrayDeque 双栈表示参数。 */
export function isDualArrayDequeRepresentationCourseware(
  value: unknown,
): value is DualArrayDequeRepresentationCourseware {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Record<string, unknown>
  const hasValidStack = (stack: unknown) =>
    Array.isArray(stack) &&
    stack.length <= 6 &&
    stack.every((item) => typeof item === 'string' && item.length > 0 && item.length <= 32)
  return (
    Object.keys(candidate).every((key) => ['kind', 'version', 'front', 'back', 'accessIndex'].includes(key)) &&
    candidate.kind === 'DUAL_ARRAY_DEQUE_REPRESENTATION' &&
    candidate.version === 1 &&
    hasValidStack(candidate.front) &&
    hasValidStack(candidate.back) &&
    (candidate.front as unknown[]).length + (candidate.back as unknown[]).length > 0 &&
    Number.isInteger(candidate.accessIndex) &&
    (candidate.accessIndex as number) >= 0 &&
    (candidate.accessIndex as number) < (candidate.front as unknown[]).length + (candidate.back as unknown[]).length
  )
}

/** 仅接受已审查、满足三倍失衡条件的 DualArrayDeque 再平衡参数。 */
export function isDualArrayDequeBalanceCourseware(value: unknown): value is DualArrayDequeBalanceCourseware {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Record<string, unknown>
  const hasValidStack = (stack: unknown) =>
    Array.isArray(stack) &&
    stack.length <= 6 &&
    stack.every((item) => typeof item === 'string' && item.length > 0 && item.length <= 32)
  if (!hasValidStack(candidate.front) || !hasValidStack(candidate.back)) return false
  const frontSize = (candidate.front as unknown[]).length
  const backSize = (candidate.back as unknown[]).length
  return (
    Object.keys(candidate).every((key) => ['kind', 'version', 'front', 'back'].includes(key)) &&
    candidate.kind === 'DUAL_ARRAY_DEQUE_BALANCE' &&
    candidate.version === 1 &&
    frontSize + backSize >= 2 &&
    (frontSize > 3 * backSize || backSize > 3 * frontSize)
  )
}

/** 仅接受已审查、容量依次递增的 RootishArrayStack 块布局参数。 */
export function isRootishArrayStackLayoutCourseware(value: unknown): value is RootishArrayStackLayoutCourseware {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Record<string, unknown>
  return (
    Object.keys(candidate).every((key) => ['kind', 'version', 'blocks'].includes(key)) &&
    candidate.kind === 'ROOTISH_ARRAY_STACK_LAYOUT' &&
    candidate.version === 1 &&
    Array.isArray(candidate.blocks) &&
    candidate.blocks.length > 0 &&
    candidate.blocks.length <= 5 &&
    candidate.blocks.every(
      (block, index) =>
        Array.isArray(block) &&
        block.length === index + 1 &&
        block.every((item) => typeof item === 'string' && item.length > 0 && item.length <= 32),
    )
  )
}

/** 仅接受已审查、固定地址参数的顺序表连续存储课件。 */
export function isSequentialListStorageCourseware(value: unknown): value is SequentialListStorageCourseware {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Record<string, unknown>
  return (
    Object.keys(candidate).every((key) =>
      ['kind', 'version', 'baseAddress', 'elementWidth', 'elements', 'accessIndex'].includes(key),
    ) &&
    candidate.kind === 'SEQUENTIAL_LIST_STORAGE' &&
    candidate.version === 1 &&
    Number.isInteger(candidate.baseAddress) &&
    (candidate.baseAddress as number) >= 0 &&
    (candidate.baseAddress as number) <= 1_000_000 &&
    Number.isInteger(candidate.elementWidth) &&
    (candidate.elementWidth as number) >= 1 &&
    (candidate.elementWidth as number) <= 64 &&
    Array.isArray(candidate.elements) &&
    candidate.elements.length > 0 &&
    candidate.elements.length <= 8 &&
    candidate.elements.every((item) => typeof item === 'string' && item.length > 0 && item.length <= 32) &&
    Number.isInteger(candidate.accessIndex) &&
    (candidate.accessIndex as number) >= 0 &&
    (candidate.accessIndex as number) < candidate.elements.length
  )
}

/** 仅接受已审查、无可执行字段的单链表逆置课件参数。 */
export function isLinkedListReversalCourseware(value: unknown): value is LinkedListReversalCourseware {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Record<string, unknown>
  return (
    Object.keys(candidate).every((key) => ['kind', 'version', 'elements'].includes(key)) &&
    candidate.kind === 'LINKED_LIST_REVERSAL' &&
    candidate.version === 1 &&
    Array.isArray(candidate.elements) &&
    candidate.elements.length >= 2 &&
    candidate.elements.length <= 6 &&
    candidate.elements.every((item) => typeof item === 'string' && item.length > 0 && item.length <= 32)
  )
}

/** 仅接受已审查、小规模固定阶乘参数的调用栈课件。 */
export function isFactorialCallStackCourseware(value: unknown): value is FactorialCallStackCourseware {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Record<string, unknown>
  return (
    Object.keys(candidate).every((key) => ['kind', 'version', 'startValue'].includes(key)) &&
    candidate.kind === 'FACTORIAL_CALL_STACK' &&
    candidate.version === 1 &&
    Number.isInteger(candidate.startValue) &&
    (candidate.startValue as number) >= 2 &&
    (candidate.startValue as number) <= 6
  )
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

/** 不由客户端预选知识点，按当前统一课程状态取得下一学习目标。 */
export function startCourseLearning(courseId: number) {
  return request.post<unknown, ApiResponse<LearningTargetVO>>(`/my-courses/${courseId}/start-learning`)
}

export function startCourseStageAssessment(courseId: number, questionCount = 5, knowledgePointId?: number | null) {
  return request.post<unknown, ApiResponse<CourseStageAssessmentVO>>(`/my-courses/${courseId}/stage-assessments`, {
    questionCount,
    knowledgePointId: knowledgePointId ?? null,
  })
}

export function submitCourseStageAssessment(
  assessmentId: number,
  answers: { assessmentQuestionId: number; userAnswer: string }[],
) {
  return request.post<unknown, ApiResponse<CourseStageAssessmentVO>>(
    `/my-courses/stage-assessments/${assessmentId}/submit`,
    { answers },
  )
}

export function getCourseStageAssessmentHistory(
  courseId: number,
  pageNum = 1,
  pageSize = 10,
  knowledgePointId?: number | null,
) {
  return request.get<unknown, ApiResponse<PageResult<CourseStageAssessmentSummaryVO>>>(
    `/my-courses/${courseId}/stage-assessments`,
    { params: { pageNum, pageSize, knowledgePointId: knowledgePointId ?? undefined } },
  )
}

export function getCourseStageAssessmentDetail(assessmentId: number) {
  return request.get<unknown, ApiResponse<CourseStageAssessmentVO>>(`/my-courses/stage-assessments/${assessmentId}`)
}

export function startTutorSession(courseId: number, knowledgePointId: number) {
  return request.post<unknown, ApiResponse<TutorSessionVO>>(`/my-courses/${courseId}/tutor-sessions`, undefined, {
    params: { knowledgePointId },
  })
}
export function submitTutorCheck(courseId: number, sessionKey: string, optionId: string) {
  return request.post<unknown, ApiResponse<TutorCheckResultVO>>(
    `/my-courses/${courseId}/tutor-sessions/${sessionKey}/check`,
    { optionId },
  )
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
