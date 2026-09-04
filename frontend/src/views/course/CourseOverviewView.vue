<template>
  <CourseOverviewContent
    :overview="overview"
    :loading="loading"
    :failed="loadFailed"
    :starting="starting"
    :primary-action-label="primaryActionLabel"
    @back="router.push({ name: 'MyCourses' })"
    @retry="fetchOverview"
    @primary-action="handlePrimaryAction"
    @open-content="openCourseContent"
    @more-command="handleMoreCommand"
    @open-target="openTarget"
    @open-tool="openTool"
    @open-tutor="openTutor"
    @open-assessment-detail="openAssessmentDetail"
    @refresh="fetchOverview"
  />

  <AssessmentSetupDialog
    v-model:visible="assessmentSetupVisible"
    :starting="assessmentStarting"
    :knowledge-points="setupKnowledgePointOptions"
    @start="startAssessment"
  />
  <StageAssessmentDialog
    v-model:visible="assessmentDialogVisible"
    v-model:answers="assessmentAnswers"
    :assessment="assessment"
    :submitting="assessmentSubmitting"
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
    @filter-change="handleAssessmentHistoryFilter"
    @load="loadAssessmentHistory"
    @open-detail="openAssessmentDetail"
  />
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getCourseOverview,
  getCourseStageAssessmentDetail,
  getCourseStageAssessmentHistory,
  startCourseLearning,
  startCourseStageAssessment,
  submitCourseStageAssessment,
  type CourseOverviewVO,
  type CourseStageAssessmentSummaryVO,
  type CourseStageAssessmentVO,
  type LearningTargetVO,
} from '@/api/course'
import { openLearningTarget } from '@/utils/learningTarget'
import AssessmentHistoryDialog from '@/components/course/AssessmentHistoryDialog.vue'
import AssessmentSetupDialog from '@/components/course/AssessmentSetupDialog.vue'
import CourseOverviewContent from '@/components/course/CourseOverviewContent.vue'
import StageAssessmentDialog from '@/components/course/StageAssessmentDialog.vue'

const route = useRoute()
const router = useRouter()
const overview = ref<CourseOverviewVO | null>(null)
const loading = ref(false)
const starting = ref(false)
const loadFailed = ref(false)
const assessmentStarting = ref(false)
const assessmentSubmitting = ref(false)
const assessmentSetupVisible = ref(false)
const assessmentKnowledgePointId = ref<number>(0)
const assessmentDialogVisible = ref(false)
const assessment = ref<CourseStageAssessmentVO | null>(null)
const assessmentAnswers = ref<Record<number, string[]>>({})
const assessmentHistoryVisible = ref(false)
const assessmentHistoryKnowledgePointId = ref<number>(0)
const assessmentHistoryLoading = ref(false)
const assessmentHistoryFailed = ref(false)
const assessmentHistory = ref<CourseStageAssessmentSummaryVO[]>([])
const assessmentHistoryPage = ref(1)
const assessmentHistoryPageSize = 10
const assessmentHistoryTotal = ref(0)

const courseId = computed(() => Number(route.params.id))
const setupKnowledgePointOptions = computed(() =>
  (overview.value?.tutorProgress ?? []).map((item) => ({ id: item.knowledgePointId, title: item.title })),
)
const reviewedKnowledgePointIds = computed(() =>
  (overview.value?.tutorProgress ?? []).map((item) => item.knowledgePointId),
)
const primaryActionLabel = computed(() => (overview.value?.recommendedTargets.length ? '继续学习' : '开始学习'))

function handlePrimaryAction() {
  if (overview.value?.recommendedTargets.length) openTarget(overview.value.recommendedTargets[0])
  else void startLearning()
}

function handleMoreCommand(command: string) {
  if (command === 'papers') openCoursePapers()
  else if (command === 'assessment') openAssessmentSetup()
  else if (command === 'history') void openAssessmentHistory()
}

async function fetchOverview() {
  loading.value = true
  loadFailed.value = false
  try {
    const response = await getCourseOverview(courseId.value)
    overview.value = response.data
  } catch {
    overview.value = null
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

function openCourseContent() {
  router.push({ name: 'CourseDetail', params: { id: courseId.value } })
}
function openCoursePapers() {
  router.push({ name: 'ExamList', query: { courseId: String(courseId.value) } })
}
function openTool(routeName: string) {
  router.push({ name: routeName, query: { courseId: String(courseId.value) } })
}

async function startLearning() {
  starting.value = true
  try {
    const response = await startCourseLearning(courseId.value)
    openTarget(response.data)
  } finally {
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

function syncAssessmentAnswers(value: CourseStageAssessmentVO) {
  assessmentAnswers.value = Object.fromEntries(
    value.questions.map((question) => [question.id, question.userAnswer ? question.userAnswer.split(',') : []]),
  )
}

function openAssessmentSetup() {
  assessmentKnowledgePointId.value = 0
  assessmentSetupVisible.value = true
}

async function startAssessment(knowledgePointId?: number) {
  assessmentStarting.value = true
  try {
    const selected = knowledgePointId ?? assessmentKnowledgePointId.value
    const response = await startCourseStageAssessment(courseId.value, 5, selected === 0 ? null : selected)
    assessment.value = response.data
    syncAssessmentAnswers(response.data)
    assessmentSetupVisible.value = false
    assessmentDialogVisible.value = true
  } finally {
    assessmentStarting.value = false
  }
}

function handleAssessmentHistoryFilter(knowledgePointId: number) {
  assessmentHistoryKnowledgePointId.value = knowledgePointId
  void loadAssessmentHistory(1)
}

async function loadAssessmentHistory(page = 1) {
  assessmentHistoryLoading.value = true
  assessmentHistoryFailed.value = false
  try {
    const response = await getCourseStageAssessmentHistory(
      courseId.value,
      page,
      assessmentHistoryPageSize,
      assessmentHistoryKnowledgePointId.value === 0 ? null : assessmentHistoryKnowledgePointId.value,
    )
    assessmentHistory.value = response.data.records
    assessmentHistoryPage.value = response.data.current
    assessmentHistoryTotal.value = response.data.total
  } catch {
    assessmentHistoryFailed.value = true
  } finally {
    assessmentHistoryLoading.value = false
  }
}

async function openAssessmentHistory() {
  assessmentHistoryVisible.value = true
  await loadAssessmentHistory(1)
}

async function openAssessmentDetail(assessmentId: number) {
  const response = await getCourseStageAssessmentDetail(assessmentId)
  assessment.value = response.data
  syncAssessmentAnswers(response.data)
  assessmentHistoryVisible.value = false
  assessmentDialogVisible.value = true
}

async function submitAssessment() {
  if (!assessment.value) return
  const incomplete = assessment.value.questions.some((question) => !assessmentAnswers.value[question.id]?.length)
  if (incomplete) {
    ElMessage.warning('请完成全部题目后再提交')
    return
  }
  assessmentSubmitting.value = true
  try {
    const answers = assessment.value.questions.map((question) => ({
      assessmentQuestionId: question.id,
      userAnswer: [...assessmentAnswers.value[question.id]].sort().join(','),
    }))
    const response = await submitCourseStageAssessment(assessment.value.id, answers)
    assessment.value = response.data
    syncAssessmentAnswers(response.data)
    await fetchOverview()
  } finally {
    assessmentSubmitting.value = false
  }
}

onMounted(fetchOverview)
</script>
