import { onScopeDispose, ref, watch, type Ref } from 'vue'
import { useGamificationStore } from '@/stores/gamification'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'
import { errorMessage } from '@/utils/errors'
import {
  getCourseStageAssessmentDetail,
  getCourseStageAssessmentHistory,
  startCourseStageAssessment,
  submitCourseStageAssessment,
  type CourseStageAssessmentSummaryVO,
  type CourseStageAssessmentVO,
} from '@/api/course'

/** Owns the local, unsubmitted stage-assessment draft and all assessment request lifecycles. */
export function useCourseStageAssessment(courseId: Ref<number>, refreshOverview: () => Promise<void>) {
  const assessmentStarting = ref(false)
  const assessmentStartError = ref('')
  const assessmentSubmitting = ref(false)
  const assessmentSubmitError = ref('')
  const focusQuestionId = ref<number | null>(null)
  const assessmentSetupVisible = ref(false)
  const assessmentKnowledgePointId = ref(0)
  const assessmentDialogVisible = ref(false)
  const assessment = ref<CourseStageAssessmentVO | null>(null)
  const assessmentAnswers = ref<Record<number, string[]>>({})
  const assessmentHistoryVisible = ref(false)
  const assessmentHistoryKnowledgePointId = ref(0)
  const assessmentHistoryLoading = ref(false)
  const assessmentHistoryFailed = ref(false)
  const assessmentDetailError = ref('')
  const assessmentDetailLoading = ref(false)
  const assessmentHistory = ref<CourseStageAssessmentSummaryVO[]>([])
  const assessmentHistoryPage = ref(1)
  const assessmentHistoryPageSize = 10
  const assessmentHistoryTotal = ref(0)
  let requestVersion = 0
  let historyRequestVersion = 0
  let alive = true
  const drafts = new Map<number, Record<number, string[]>>()

  function current(version: number, session: number, requestedCourseId: number) {
    return (
      alive && version === requestVersion && session === getAuthSessionVersion() && requestedCourseId === courseId.value
    )
  }

  function syncAssessmentAnswers(value: CourseStageAssessmentVO) {
    const draft = value.status === 'IN_PROGRESS' ? drafts.get(value.id) : undefined
    assessmentAnswers.value = draft
      ? Object.fromEntries(Object.entries(draft).map(([id, answer]) => [Number(id), [...answer]]))
      : Object.fromEntries(
          value.questions.map((question) => [question.id, question.userAnswer ? question.userAnswer.split(',') : []]),
        )
  }

  function openAssessmentSetup() {
    assessmentKnowledgePointId.value = 0
    assessmentStartError.value = ''
    assessmentSetupVisible.value = true
  }

  async function startAssessment(knowledgePointId?: number) {
    if (assessmentStarting.value) return
    const version = ++requestVersion
    const session = getAuthSessionVersion()
    const requestedCourseId = courseId.value
    assessmentStarting.value = true
    assessmentStartError.value = ''
    try {
      const selected = knowledgePointId ?? assessmentKnowledgePointId.value
      const response = await startCourseStageAssessment(requestedCourseId, 5, selected === 0 ? null : selected, {
        errorDisplay: 'inline',
      })
      if (!current(version, session, requestedCourseId)) return
      assessment.value = response.data
      if (response.data.status === 'IN_PROGRESS') syncAssessmentAnswers(response.data)
      assessmentSetupVisible.value = false
      assessmentDialogVisible.value = true
    } catch (error) {
      if (current(version, session, requestedCourseId)) {
        assessmentStartError.value = errorMessage(error, '测评暂时无法开始，请重试。')
      }
    } finally {
      if (current(version, session, requestedCourseId)) assessmentStarting.value = false
    }
  }

  function handleAssessmentHistoryFilter(knowledgePointId: number) {
    if (assessmentHistoryLoading.value || assessmentDetailLoading.value) return
    assessmentHistoryKnowledgePointId.value = knowledgePointId
    void loadAssessmentHistory(1)
  }

  async function loadAssessmentHistory(page = 1) {
    const version = ++historyRequestVersion
    const session = getAuthSessionVersion()
    const requestedCourseId = courseId.value
    const historyCurrent = () =>
      alive &&
      version === historyRequestVersion &&
      session === getAuthSessionVersion() &&
      requestedCourseId === courseId.value
    assessmentHistoryLoading.value = true
    assessmentHistoryFailed.value = false
    try {
      const response = await getCourseStageAssessmentHistory(
        requestedCourseId,
        page,
        assessmentHistoryPageSize,
        assessmentHistoryKnowledgePointId.value === 0 ? null : assessmentHistoryKnowledgePointId.value,
        { errorDisplay: 'inline' },
      )
      if (!historyCurrent()) return
      assessmentHistory.value = response.data.records
      assessmentHistoryPage.value = response.data.current
      assessmentHistoryTotal.value = response.data.total
    } catch {
      if (historyCurrent()) assessmentHistoryFailed.value = true
    } finally {
      if (historyCurrent()) assessmentHistoryLoading.value = false
    }
  }

  async function openAssessmentHistory() {
    if (assessmentHistoryLoading.value) return
    assessmentHistoryVisible.value = true
    await loadAssessmentHistory(1)
  }

  async function openAssessmentDetail(assessmentId: number) {
    if (assessmentDetailLoading.value) return
    const version = ++requestVersion
    const session = getAuthSessionVersion()
    const requestedCourseId = courseId.value
    assessmentDetailError.value = ''
    assessmentDetailLoading.value = true
    try {
      const response = await getCourseStageAssessmentDetail(assessmentId, { errorDisplay: 'inline' })
      if (!current(version, session, requestedCourseId)) return
      assessment.value = response.data
      syncAssessmentAnswers(response.data)
      assessmentHistoryVisible.value = false
      assessmentDialogVisible.value = true
    } catch {
      if (current(version, session, requestedCourseId)) assessmentDetailError.value = '测评详情暂时无法读取，请重试。'
    } finally {
      if (current(version, session, requestedCourseId)) assessmentDetailLoading.value = false
    }
  }

  async function submitAssessment() {
    if (!assessment.value || assessmentSubmitting.value) return
    const version = ++requestVersion
    const session = getAuthSessionVersion()
    const requestedCourseId = courseId.value
    const firstIncomplete = assessment.value.questions.find((question) => !assessmentAnswers.value[question.id]?.length)
    if (firstIncomplete) {
      assessmentSubmitError.value = '请完成全部题目后再提交。'
      focusQuestionId.value = firstIncomplete.id
      return
    }
    assessmentSubmitting.value = true
    assessmentSubmitError.value = ''
    try {
      const answers = assessment.value.questions.map((question) => ({
        assessmentQuestionId: question.id,
        userAnswer: [...assessmentAnswers.value[question.id]].sort().join(','),
      }))
      const response = await submitCourseStageAssessment(assessment.value.id, answers, { errorDisplay: 'inline' })
      if (!current(version, session, requestedCourseId)) return
      assessment.value = response.data
      syncAssessmentAnswers(response.data)
      drafts.delete(response.data.id)
      void useGamificationStore().load()
      await refreshOverview()
    } catch {
      if (current(version, session, requestedCourseId)) assessmentSubmitError.value = '提交暂时失败，请检查网络后重试。'
    } finally {
      if (current(version, session, requestedCourseId)) assessmentSubmitting.value = false
    }
  }

  function reset() {
    requestVersion += 1
    historyRequestVersion += 1
    assessment.value = null
    assessmentAnswers.value = {}
    assessmentHistory.value = []
    assessmentHistoryTotal.value = 0
    assessmentHistoryPage.value = 1
    assessmentHistoryKnowledgePointId.value = 0
    assessmentHistoryFailed.value = false
    assessmentStarting.value = false
    assessmentSubmitting.value = false
    assessmentHistoryLoading.value = false
    assessmentDetailLoading.value = false
    assessmentSetupVisible.value = false
    assessmentDialogVisible.value = false
    assessmentHistoryVisible.value = false
    assessmentStartError.value = ''
    assessmentSubmitError.value = ''
    assessmentDetailError.value = ''
    focusQuestionId.value = null
    drafts.clear()
  }

  watch(
    assessmentAnswers,
    (answers) => {
      if (assessment.value?.status !== 'IN_PROGRESS') return
      drafts.set(
        assessment.value.id,
        Object.fromEntries(Object.entries(answers).map(([id, answer]) => [Number(id), [...answer]])),
      )
      assessmentSubmitError.value = ''
      focusQuestionId.value = null
    },
    { deep: true },
  )
  watch(courseId, reset)
  const unsubscribe = onAuthSessionChange(reset)
  onScopeDispose(() => {
    alive = false
    requestVersion += 1
    historyRequestVersion += 1
    unsubscribe()
  })

  return {
    assessmentStarting,
    assessmentStartError,
    assessmentSubmitting,
    assessmentSubmitError,
    focusQuestionId,
    assessmentSetupVisible,
    assessmentKnowledgePointId,
    assessmentDialogVisible,
    assessment,
    assessmentAnswers,
    assessmentHistoryVisible,
    assessmentHistoryKnowledgePointId,
    assessmentHistoryLoading,
    assessmentHistoryFailed,
    assessmentDetailError,
    assessmentDetailLoading,
    assessmentHistory,
    assessmentHistoryPage,
    assessmentHistoryPageSize,
    assessmentHistoryTotal,
    openAssessmentSetup,
    startAssessment,
    handleAssessmentHistoryFilter,
    loadAssessmentHistory,
    openAssessmentHistory,
    openAssessmentDetail,
    submitAssessment,
  }
}
