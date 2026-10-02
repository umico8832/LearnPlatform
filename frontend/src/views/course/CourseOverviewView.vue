<template>
  <div>
    <CourseOverviewContent
      :overview="overview"
      :loading="loading"
      :failed="loadFailed"
      :starting="starting"
      :primary-action-label="primaryActionLabel"
      :primary-action-error="startLearningError"
      :recommended-action-label="recommendedActionLabel"
      :recommended-target-title="recommendedTargetTitle"
      :assessment-detail-error="assessmentDetailError"
      :has-recommended-target="Boolean(overview?.recommendedTargets.length)"
      :course-id="courseId"
      :facts-refresh-key="factsRefreshKey"
      @back="router.push({ name: 'MyCourses' })"
      @retry="fetchOverview"
      @primary-action="handlePrimaryAction"
      @open-content="openCourseContent"
      @more-command="handleMoreCommand"
      @open-target="openTarget"
      @open-tool="openTool"
      @open-tutor="openTutor"
      @open-knowledge-point-review="openKnowledgePointReview"
      @open-knowledge-point-wrong-questions="openKnowledgePointWrongQuestions"
      @open-assessment-detail="openAssessmentDetail"
      @refresh="fetchOverview"
    />

    <AssessmentSetupDialog
      v-model:visible="assessmentSetupVisible"
      :starting="assessmentStarting"
      :error="assessmentStartError"
      :knowledge-points="setupKnowledgePointOptions"
      @start="startAssessment"
    />
    <StageAssessmentDialog
      v-model:visible="assessmentDialogVisible"
      v-model:answers="assessmentAnswers"
      :assessment="assessment"
      :submitting="assessmentSubmitting"
      :submit-error="assessmentSubmitError"
      :focus-question-id="focusQuestionId"
      :reviewed-knowledge-point-ids="reviewedKnowledgePointIds"
      @submit="submitAssessment"
      @review-wrong="reviewWrongQuestion"
      @review-wrong-by-kp="reviewWrongQuestionByKnowledgePoint"
      @open-kp-tutor="openKnowledgePointTutor"
    />
    <AssessmentHistoryDialog
      v-model:visible="assessmentHistoryVisible"
      :loading="assessmentHistoryLoading"
      :failed="assessmentHistoryFailed"
      :records="assessmentHistory"
      :page="assessmentHistoryPage"
      :page-size="assessmentHistoryPageSize"
      :total="assessmentHistoryTotal"
      :filter-knowledge-point-id="assessmentHistoryKnowledgePointId"
      :knowledge-point-options="setupKnowledgePointOptions"
      :detail-error="assessmentDetailError"
      :detail-loading="assessmentDetailLoading"
      @filter-change="handleAssessmentHistoryFilter"
      @load="loadAssessmentHistory"
      @open-detail="openAssessmentDetail"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getAuthSessionVersion, isAuthenticated, onAuthSessionChange } from '@/utils/auth'
import { getCourseOverview, startCourseLearning, type CourseOverviewVO, type LearningTargetVO } from '@/api/course'
import { openLearningTarget } from '@/utils/learningTarget'
import { useCourseStageAssessment } from '@/composables/useCourseStageAssessment'
import AssessmentHistoryDialog from '@/components/course/AssessmentHistoryDialog.vue'
import AssessmentSetupDialog from '@/components/course/AssessmentSetupDialog.vue'
import CourseOverviewContent from '@/components/course/CourseOverviewContent.vue'
import StageAssessmentDialog from '@/components/course/StageAssessmentDialog.vue'
const route = useRoute()
const router = useRouter()
const overview = ref<CourseOverviewVO | null>(null)
const loading = ref(false)
const loadFailed = ref(false)
const starting = ref(false)
const startLearningError = ref('')
const factsRefreshKey = ref(0)
let overviewRequestVersion = 0
let startLearningRequestVersion = 0
let alive = true
const courseId = computed(() => Number(route.params.id))
const setupKnowledgePointOptions = computed(() =>
  (overview.value?.tutorProgress ?? []).map((item) => ({ id: item.knowledgePointId, title: item.title })),
)
const reviewedKnowledgePointIds = computed(() =>
  (overview.value?.tutorProgress ?? []).map((item) => item.knowledgePointId),
)
const primaryActionLabel = computed(() => (overview.value?.recommendedTargets.length ? '继续学习' : '开始学习'))
const recommendedTarget = computed(() => overview.value?.recommendedTargets[0] ?? null)
const recommendedActionLabel = computed(() => {
  const target = recommendedTarget.value
  if (!target) return '开始学习'
  if (target.type === 'DUE_REVIEW') return '开始复习'
  if (target.type === 'WRONG_QUESTION') return '处理错题'
  const status = overview.value?.tutorProgress.find((item) => item.knowledgePointId === target.knowledgePointId)?.status
  return status === 'IN_PROGRESS' ? '继续学习' : status === 'COMPLETED' ? '再次学习' : '开始学习'
})
const recommendedTargetTitle = computed(() => {
  const target = recommendedTarget.value
  if (!target) return ''
  if (target.type !== 'TUTOR') return target.title
  const title = overview.value?.tutorProgress.find((item) => item.knowledgePointId === target.knowledgePointId)?.title
  return title ? `学习：${title}` : target.title
})
async function fetchOverview() {
  const requestedCourseId = courseId.value,
    version = ++overviewRequestVersion,
    session = getAuthSessionVersion()
  const current = () =>
    alive &&
    version === overviewRequestVersion &&
    requestedCourseId === courseId.value &&
    session === getAuthSessionVersion()
  loading.value = true
  loadFailed.value = false
  try {
    const response = await getCourseOverview(requestedCourseId, { errorDisplay: 'inline' })
    if (!current()) return
    overview.value = response.data
    factsRefreshKey.value += 1
  } catch {
    if (!current()) return
    overview.value = null
    loadFailed.value = true
  } finally {
    if (current()) loading.value = false
  }
}
const {
  assessmentStarting,
  assessmentStartError,
  assessmentSubmitting,
  assessmentSubmitError,
  focusQuestionId,
  assessmentSetupVisible,
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
} = useCourseStageAssessment(courseId, fetchOverview)
function handlePrimaryAction() {
  if (overview.value?.recommendedTargets.length) openTarget(overview.value.recommendedTargets[0])
  else void startLearning()
}
function handleMoreCommand(command: string) {
  if (command === 'papers') openCoursePapers()
  else if (command === 'assessment') openAssessmentSetup()
  else if (command === 'history') void openAssessmentHistory()
}
function openCourseContent() {
  router.push({ name: 'CourseDetail', params: { id: courseId.value }, query: { from: 'learning-space' } })
}
function openCoursePapers() {
  router.push({ name: 'ExamList', query: { courseId: String(courseId.value) } })
}
function openTool(routeName: string) {
  router.push({ name: routeName, query: { courseId: String(courseId.value) } })
}
async function startLearning() {
  if (starting.value) return
  const session = getAuthSessionVersion(),
    requestedCourseId = courseId.value,
    version = ++startLearningRequestVersion
  starting.value = true
  startLearningError.value = ''
  try {
    const response = await startCourseLearning(requestedCourseId, { errorDisplay: 'inline' })
    if (
      !alive ||
      version !== startLearningRequestVersion ||
      session !== getAuthSessionVersion() ||
      requestedCourseId !== courseId.value
    )
      return
    openTarget(response.data)
  } catch {
    if (
      alive &&
      version === startLearningRequestVersion &&
      session === getAuthSessionVersion() &&
      requestedCourseId === courseId.value
    )
      startLearningError.value = '暂时无法开始学习，请重试。'
  } finally {
    if (
      alive &&
      version === startLearningRequestVersion &&
      session === getAuthSessionVersion() &&
      requestedCourseId === courseId.value
    )
      starting.value = false
  }
}
function openTarget(target: LearningTargetVO) {
  openLearningTarget(router, courseId.value, target)
}
function openTutor(knowledgePointId: number) {
  router.push({
    name: 'TutorSession',
    params: { id: courseId.value },
    query: { knowledgePointId: String(knowledgePointId) },
  })
}
function openKnowledgePointTutor(knowledgePointId: number) {
  openTutor(knowledgePointId)
}
function reviewWrongQuestion(questionId: number) {
  router.push({ name: 'WrongQuestions', query: { courseId: String(courseId.value), questionId: String(questionId) } })
}
function reviewWrongQuestionByKnowledgePoint(point: { id: number; name: string }) {
  router.push({
    name: 'WrongQuestions',
    query: { courseId: String(courseId.value), knowledgePointId: String(point.id), knowledgePointName: point.name },
  })
}
function openKnowledgePointReview(knowledgePointId: number, knowledgePointName: string) {
  router.push({
    name: 'Review',
    query: { courseId: String(courseId.value), knowledgePointId: String(knowledgePointId), knowledgePointName },
  })
}
function openKnowledgePointWrongQuestions(knowledgePointId: number, knowledgePointName: string) {
  reviewWrongQuestionByKnowledgePoint({ id: knowledgePointId, name: knowledgePointName })
}
const unsubscribeAuth = onAuthSessionChange(() => {
  overviewRequestVersion += 1
  startLearningRequestVersion += 1
  overview.value = null
  loading.value = false
  starting.value = false
  startLearningError.value = ''
  if (isAuthenticated()) void fetchOverview()
})
watch(
  courseId,
  () => {
    startLearningRequestVersion += 1
    starting.value = false
    startLearningError.value = ''
    void fetchOverview()
  },
  { immediate: true },
)
onBeforeUnmount(() => {
  alive = false
  overviewRequestVersion += 1
  startLearningRequestVersion += 1
  unsubscribeAuth()
})
</script>
