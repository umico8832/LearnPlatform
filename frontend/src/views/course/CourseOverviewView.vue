<template>
  <div class="course-overview page-container">
    <section class="overview-hero">
      <div>
        <el-button :icon="ArrowLeft" text @click="router.push({ name: 'MyCourses' })">返回课程库</el-button>
        <span class="section-kicker">课程学习中枢</span>
        <h2>{{ overview?.courseName || '课程总览' }}</h2>
        <p>这里呈现已发生的作答、复习与错题事实；它们不会被浏览时长或自评替代。</p>
      </div>
      <div class="hero-actions">
        <el-button :icon="Collection" @click="openCourseContent">查看课程目录</el-button>
        <el-button :icon="Document" @click="openCoursePapers">学习课程试卷</el-button>
        <el-button :icon="DataAnalysis" @click="openAssessmentSetup">阶段测评</el-button>
        <el-button :icon="DataAnalysis" :loading="assessmentHistoryLoading" @click="openAssessmentHistory">
          测评历史
        </el-button>
        <el-button
          type="primary"
          :icon="ArrowRight"
          :loading="starting"
          aria-label="按统一课程状态开始学习"
          @click="startLearning"
        >
          开始学习
        </el-button>
      </div>
    </section>

    <section v-loading="loading" class="overview-content" element-loading-text="正在汇总学习记录...">
      <template v-if="overview">
        <div class="stats-grid" aria-label="课程学习统计">
          <article class="stat-card">
            <span>已作答</span>
            <strong>{{ overview.answeredCount }}</strong>
            <small>来自课程内真实判分</small>
          </article>
          <article class="stat-card">
            <span>答对</span>
            <strong>{{ overview.correctCount }}</strong>
            <small>不等同于课程掌握度</small>
          </article>
          <article class="stat-card emphasis">
            <span>待复习</span>
            <strong>{{ overview.dueReviewCount }}</strong>
            <small>已到间隔复习时间</small>
          </article>
          <article class="stat-card warning">
            <span>待处理错题</span>
            <strong>{{ overview.unresolvedWrongCount }}</strong>
            <small>尚未标记为已掌握</small>
          </article>
        </div>

        <section
          v-if="overview.tutorProgress.length"
          class="tutor-progress-panel"
          aria-labelledby="tutor-progress-heading"
        >
          <div class="panel-header">
            <div>
              <span class="section-kicker">课程目录</span>
              <h3 id="tutor-progress-heading">已迁入教学内容</h3>
            </div>
            <el-tag type="info" effect="plain">仅显示已审查内容</el-tag>
          </div>
          <p class="tutor-progress-note">状态仅来自服务端保存的理解检查；其余目录内容尚未迁入时不会被推断为未完成。</p>
          <div class="tutor-progress-list">
            <article v-for="item in overview.tutorProgress" :key="item.knowledgePointId" class="tutor-progress-item">
              <div>
                <h4>{{ item.title }}</h4>
                <span class="progress-status">{{ tutorStatusLabel(item.status) }}</span>
              </div>
              <el-button
                :type="item.status === 'IN_PROGRESS' ? 'primary' : 'default'"
                @click="openTutor(item.knowledgePointId)"
              >
                {{ tutorActionLabel(item.status) }}
              </el-button>
            </article>
          </div>
        </section>

        <div class="overview-grid">
          <section class="target-panel" aria-labelledby="next-target-heading">
            <div class="panel-header">
              <div>
                <span class="section-kicker">开始学习</span>
                <h3 id="next-target-heading">选择下一步</h3>
              </div>
              <el-tag type="info" effect="plain">按现有学习事实排序</el-tag>
            </div>
            <div class="target-list">
              <article v-for="(target, index) in overview.recommendedTargets" :key="target.type" class="target-item">
                <div class="target-index" aria-hidden="true">{{ index + 1 }}</div>
                <div class="target-copy">
                  <h4>{{ target.title }}</h4>
                  <p>{{ target.reason }}</p>
                </div>
                <el-button :type="index === 0 ? 'primary' : 'default'" :icon="ArrowRight" @click="openTarget(target)">
                  开始
                </el-button>
              </article>
            </div>
          </section>

          <aside class="activity-panel" aria-labelledby="activity-heading">
            <span class="section-kicker">最近学习</span>
            <h3 id="activity-heading">学习记录</h3>
            <p v-if="overview.lastLearningTime">最近一次课程内判分：{{ formatDateTime(overview.lastLearningTime) }}</p>
            <p v-else>还没有课程内学习记录。可从课程目录或题目开始。</p>
            <div v-if="overview.latestStageAssessment" class="latest-assessment">
              <strong>最近阶段测评</strong>
              <span>
                答对 {{ overview.latestStageAssessment.correctCount }} /
                {{ overview.latestStageAssessment.questionCount }} 题
              </span>
              <small>范围：{{ overview.latestStageAssessment.targetKnowledgePointName || '课程整体' }}</small>
              <small>题源：{{ sourceCompositionText(overview.latestStageAssessment.sourceComposition) }}</small>
              <small>{{ formatDateTime(overview.latestStageAssessment.completeTime) }}</small>
              <el-button text @click="openAssessmentDetail(overview.latestStageAssessment.id)">查看逐题复盘</el-button>
            </div>
            <el-button text :icon="Refresh" :loading="loading" @click="fetchOverview">刷新记录</el-button>
          </aside>
        </div>
      </template>

      <el-result
        v-else-if="!loading && loadFailed"
        icon="error"
        title="暂时无法读取课程总览"
        sub-title="请刷新重试；如果课程尚未加入课程库，请先从课程中心加入。"
      >
        <template #extra><el-button type="primary" @click="fetchOverview">重新加载</el-button></template>
      </el-result>
    </section>

    <el-dialog v-model="assessmentHistoryVisible" title="阶段测评历史" width="min(680px, 94vw)">
      <el-result v-if="assessmentHistoryFailed" icon="error" title="暂时无法读取测评历史" sub-title="请稍后重试。">
        <template #extra
          ><el-button type="primary" @click="loadAssessmentHistory(assessmentHistoryPage)"
            >重新加载</el-button
          ></template
        >
      </el-result>
      <p v-else-if="!assessmentHistoryLoading && !assessmentHistory.length" class="history-empty">
        还没有已完成的阶段测评。
      </p>
      <div v-else v-loading="assessmentHistoryLoading" class="assessment-history-list">
        <article v-for="item in assessmentHistory" :key="item.id" class="assessment-history-item">
          <div>
            <strong>答对 {{ item.correctCount }} / {{ item.questionCount }} 题</strong>
            <p>{{ formatDateTime(item.completeTime) }}</p>
            <small>{{ assessmentStrategyText(item.selectionStrategy) }}</small>
            <small>范围：{{ item.targetKnowledgePointName || '课程整体' }}</small>
            <small>题源：{{ sourceCompositionText(item.sourceComposition) }}</small>
          </div>
          <el-button @click="openAssessmentDetail(item.id)">查看复盘</el-button>
        </article>
      </div>
      <el-pagination
        v-if="assessmentHistoryTotal > assessmentHistoryPageSize"
        layout="prev, pager, next"
        :current-page="assessmentHistoryPage"
        :page-size="assessmentHistoryPageSize"
        :total="assessmentHistoryTotal"
        @current-change="loadAssessmentHistory"
      />
    </el-dialog>

    <el-dialog v-model="assessmentSetupVisible" title="开始阶段测评" width="min(480px, 94vw)">
      <p class="assessment-setup-note">
        默认从整门课程选题；也可以限定在单个已审查知识点内，只从该知识点关联的可见已发布客观题选题。
      </p>
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="知识点范围">
          <el-select v-model="assessmentKnowledgePointId" placeholder="选择知识点范围" class="assessment-scope-select">
            <el-option :value="0" label="课程整体测评" />
            <el-option
              v-for="item in overview?.tutorProgress ?? []"
              :key="item.knowledgePointId"
              :value="item.knowledgePointId"
              :label="item.title"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assessmentSetupVisible = false">取消</el-button>
        <el-button type="primary" :loading="assessmentStarting" @click="startAssessment">开始测评</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="assessmentDialogVisible" title="课程阶段测评" width="min(780px, 94vw)">
      <template v-if="assessment">
        <el-alert
          :title="assessmentStrategyLabel"
          :type="assessment.selectionStrategy === 'LEARNING_STATE_PRIORITY' ? 'info' : 'warning'"
          :closable="false"
          show-icon
        />
        <p class="assessment-source-composition">
          范围：{{ assessment.targetKnowledgePointName || '课程整体' }} · 题源构成：{{
            sourceCompositionText(assessment.sourceComposition)
          }}
        </p>
        <p v-if="assessment.status === 'COMPLETED'" class="assessment-summary">
          答对 {{ assessment.correctCount }} / {{ assessment.questionCount }} 题
        </p>
        <div class="assessment-list">
          <article v-for="question in assessment.questions" :key="question.id" class="assessment-question">
            <div class="assessment-question-header">
              <strong>{{ question.sortOrder }}. {{ question.content }}</strong>
              <div class="assessment-question-tags">
                <el-tag v-if="question.sourceType === 'AI_GENERATED'" type="warning" effect="plain">
                  AI 审查生成题<span v-if="question.originQuestionId"> · 母题 #{{ question.originQuestionId }}</span>
                </el-tag>
                <el-tag v-if="question.correct != null" :type="question.correct ? 'success' : 'danger'">
                  {{ question.correct ? '正确' : '错误' }}
                </el-tag>
              </div>
            </div>
            <el-checkbox-group
              v-if="question.questionType === 'MULTIPLE_CHOICE'"
              v-model="assessmentAnswers[question.id]"
              :disabled="assessment.status === 'COMPLETED'"
              class="assessment-options"
            >
              <el-checkbox v-for="option in question.options" :key="option.label" :value="option.label">
                {{ option.label }}. {{ option.content }}
              </el-checkbox>
            </el-checkbox-group>
            <el-radio-group
              v-else
              v-model="assessmentAnswers[question.id][0]"
              :disabled="assessment.status === 'COMPLETED'"
              class="assessment-options"
            >
              <el-radio v-for="option in question.options" :key="option.label" :value="option.label">
                {{ option.content }}
              </el-radio>
            </el-radio-group>
            <div v-if="assessment.status === 'COMPLETED'" class="assessment-result">
              <p>参考答案：{{ question.correctAnswer }}</p>
              <p>{{ question.analysis || '暂无解析' }}</p>
            </div>
          </article>
        </div>
      </template>
      <template #footer>
        <el-button @click="assessmentDialogVisible = false">关闭</el-button>
        <el-button
          v-if="assessment?.status === 'IN_PROGRESS'"
          type="primary"
          :loading="assessmentSubmitting"
          @click="submitAssessment"
          >提交测评</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowRight, Collection, DataAnalysis, Document, Refresh } from '@element-plus/icons-vue'
import {
  getCourseOverview,
  getCourseStageAssessmentDetail,
  getCourseStageAssessmentHistory,
  startCourseLearning,
  startCourseStageAssessment,
  submitCourseStageAssessment,
  type CourseOverviewVO,
  type CourseStageAssessmentVO,
  type CourseStageAssessmentSummaryVO,
  type LearningTargetVO,
} from '@/api/course'

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
const assessmentHistoryLoading = ref(false)
const assessmentHistoryFailed = ref(false)
const assessmentHistory = ref<CourseStageAssessmentSummaryVO[]>([])
const assessmentHistoryPage = ref(1)
const assessmentHistoryPageSize = 10
const assessmentHistoryTotal = ref(0)
const courseId = computed(() => Number(route.params.id))
const assessmentStrategyLabel = computed(() =>
  assessment.value?.selectionStrategy === 'LEARNING_STATE_PRIORITY'
    ? '按当前错题、到期复习和近期错误记录优先选题'
    : '学习数据不足，采用确定性课程题序；本次不标记为 AI 个性化',
)

function assessmentStrategyText(strategy: CourseStageAssessmentSummaryVO['selectionStrategy']) {
  return strategy === 'LEARNING_STATE_PRIORITY' ? '按当前学习事实优先选题' : '确定性课程题序'
}

function sourceCompositionText(composition: CourseStageAssessmentSummaryVO['sourceComposition']) {
  if (!composition) return '暂无题源快照'
  return [
    ['官方原题', composition.officialExamCount],
    ['平台人工题', composition.manualCount],
    ['用户私有题', composition.userPrivateCount],
    ['AI 生成题', composition.aiGeneratedCount],
  ]
    .filter(([, count]) => Number(count) > 0)
    .map(([label, count]) => `${label} ${count}`)
    .join(' · ')
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

async function startLearning() {
  starting.value = true
  try {
    const response = await startCourseLearning(courseId.value)
    openTarget(response.data)
  } finally {
    starting.value = false
  }
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

async function startAssessment() {
  assessmentStarting.value = true
  try {
    const response = await startCourseStageAssessment(
      courseId.value,
      5,
      assessmentKnowledgePointId.value === 0 ? null : assessmentKnowledgePointId.value,
    )
    assessment.value = response.data
    syncAssessmentAnswers(response.data)
    assessmentSetupVisible.value = false
    assessmentDialogVisible.value = true
  } finally {
    assessmentStarting.value = false
  }
}

async function loadAssessmentHistory(page = 1) {
  assessmentHistoryLoading.value = true
  assessmentHistoryFailed.value = false
  try {
    const response = await getCourseStageAssessmentHistory(courseId.value, page, assessmentHistoryPageSize)
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

function openTutor(knowledgePointId: number) {
  router.push({
    name: 'TutorSession',
    params: { id: courseId.value },
    query: { knowledgePointId: String(knowledgePointId) },
  })
}

function tutorStatusLabel(status: string) {
  if (status === 'COMPLETED') return '已完成理解检查'
  if (status === 'IN_PROGRESS') return '已尝试'
  return '未开始'
}

function tutorActionLabel(status: string) {
  if (status === 'IN_PROGRESS') return '继续学习'
  if (status === 'COMPLETED') return '再次学习'
  return '开始学习'
}

function openTarget(target: LearningTargetVO) {
  if (target.type === 'TUTOR' && target.knowledgePointId) {
    router.push({
      name: 'TutorSession',
      params: { id: courseId.value },
      query: { knowledgePointId: String(target.knowledgePointId) },
    })
    return
  }
  const query = {
    courseId: String(courseId.value),
    ...(target.questionId ? { questionId: String(target.questionId) } : {}),
    ...(target.knowledgePointId ? { knowledgePointId: String(target.knowledgePointId) } : {}),
  }
  if (target.type === 'DUE_REVIEW') {
    router.push({ name: 'Review', query })
    return
  }
  if (target.type === 'WRONG_QUESTION') {
    router.push({ name: 'WrongQuestions', query })
    return
  }
  router.push({ name: 'QuestionList', query })
}

function formatDateTime(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN')
}

onMounted(fetchOverview)
</script>

<style scoped>
.course-overview {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.overview-hero,
.overview-content,
.target-panel,
.activity-panel,
.stat-card {
  background: var(--lp-surface);
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  box-shadow: var(--lp-shadow-sm);
}
.overview-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  padding: 22px;
}
.hero-actions {
  display: flex;
  flex: none;
  gap: 10px;
}
.overview-hero h2,
.panel-header h3,
.activity-panel h3,
.target-copy h4 {
  margin: 0;
  color: var(--lp-text);
}
.overview-hero h2 {
  margin-top: 4px;
  font-size: 24px;
  line-height: 1.25;
}
.overview-hero p,
.target-copy p,
.activity-panel p {
  max-width: 720px;
  margin: 8px 0 0;
  color: var(--lp-text-secondary);
  font-size: 14px;
  line-height: 1.7;
}
.section-kicker {
  display: block;
  margin-top: 10px;
  color: var(--lp-primary);
  font-size: 12px;
  font-weight: 800;
}
.overview-content {
  min-height: 320px;
  padding: 18px;
}
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
.stat-card {
  min-height: 122px;
  padding: 16px;
}
.stat-card span,
.stat-card small {
  display: block;
  color: var(--lp-text-muted);
  font-size: 13px;
  line-height: 1.5;
}
.stat-card strong {
  display: block;
  margin: 10px 0 5px;
  color: var(--lp-primary);
  font-size: 30px;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}
.stat-card.warning strong {
  color: var(--el-color-warning);
}
.stat-card.emphasis {
  background: var(--lp-surface-soft);
}
.tutor-progress-panel {
  margin-top: 16px;
  padding: 18px;
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  background: var(--lp-surface-soft);
}
.tutor-progress-note {
  margin: 8px 0 0;
  color: var(--lp-text-secondary);
  font-size: 14px;
  line-height: 1.7;
}
.tutor-progress-list {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}
.tutor-progress-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px;
  border: 1px solid var(--lp-border);
  border-radius: 8px;
  background: var(--lp-surface);
}
.tutor-progress-item h4 {
  margin: 0;
  color: var(--lp-text);
  font-size: 15px;
}
.progress-status {
  display: inline-block;
  margin-top: 6px;
  color: var(--lp-text-secondary);
  font-size: 13px;
  line-height: 1.5;
}
.overview-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(260px, 0.8fr);
  gap: 14px;
  margin-top: 16px;
}
.target-panel,
.activity-panel {
  padding: 18px;
}
.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.panel-header .section-kicker,
.activity-panel .section-kicker {
  margin-top: 0;
}
.panel-header h3,
.activity-panel h3 {
  margin-top: 4px;
  font-size: 18px;
}
.target-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.target-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--lp-border);
  border-radius: 8px;
  background: var(--lp-surface-soft);
}
.target-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--lp-primary-soft);
  color: var(--lp-primary);
  font-size: 14px;
  font-weight: 800;
}
.target-copy h4 {
  font-size: 15px;
  line-height: 1.45;
}
.target-copy p {
  margin-top: 3px;
}
.activity-panel .el-button {
  margin-top: 10px;
}
.latest-assessment {
  display: grid;
  gap: 4px;
  margin-top: 14px;
  padding: 12px;
  border: 1px solid var(--lp-border);
  border-radius: 8px;
  background: var(--lp-surface-soft);
}
.latest-assessment small,
.assessment-history-item small {
  color: var(--lp-text-muted);
}
.assessment-history-list {
  display: grid;
  gap: 10px;
  min-height: 80px;
}
.assessment-history-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px;
  border: 1px solid var(--lp-border);
  border-radius: 8px;
  background: var(--lp-surface-soft);
}
.assessment-history-item p,
.history-empty {
  margin: 4px 0;
  color: var(--lp-text-secondary);
}
.assessment-summary {
  margin: 16px 0 0;
  color: var(--lp-text);
  font-size: 20px;
  font-weight: 700;
}
.assessment-list {
  display: grid;
  gap: 14px;
  margin-top: 16px;
}
.assessment-question {
  padding: 16px;
  border: 1px solid var(--lp-border);
  border-radius: 8px;
  background: var(--lp-surface-soft);
}
.assessment-question-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  color: var(--lp-text);
  line-height: 1.6;
}
.assessment-question-tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}
.assessment-source-composition {
  margin: 10px 0 16px;
  color: var(--lp-text-muted);
  font-size: 13px;
}
.assessment-options {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}
.assessment-result {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 6px;
  background: var(--lp-surface);
  color: var(--lp-text-secondary);
}
.assessment-result p {
  margin: 0;
  line-height: 1.6;
}
.assessment-result p + p {
  margin-top: 4px;
}
@media (max-width: 900px) {
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .overview-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 767px) {
  .overview-hero {
    align-items: stretch;
    flex-direction: column;
  }
  .hero-actions {
    align-items: stretch;
    flex-direction: column;
  }
  .hero-actions .el-button {
    width: 100%;
    margin-left: 0;
  }
  .stats-grid {
    grid-template-columns: 1fr;
  }
  .target-item {
    grid-template-columns: auto minmax(0, 1fr);
  }
  .target-item .el-button {
    grid-column: 2;
    justify-self: start;
  }
  .tutor-progress-panel {
    padding: 16px;
  }
  .tutor-progress-item {
    align-items: flex-start;
    flex-direction: column;
  }
  .tutor-progress-item .el-button {
    width: 100%;
  }
  .assessment-question-header {
    flex-direction: column;
  }
  .assessment-history-item {
    align-items: stretch;
    flex-direction: column;
  }
  .assessment-history-item .el-button {
    width: 100%;
  }
}
</style>
