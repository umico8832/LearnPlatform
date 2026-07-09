<template>
  <div class="learning-path page-container">
    <section class="page-hero">
      <div>
        <span class="section-kicker">学习路线</span>
        <h2>学习路径推荐</h2>
        <p>把练习正确率、错题数和掌握状态串成下一步路线，先处理最影响提分的知识点。</p>
      </div>
      <div class="hero-actions">
        <el-select v-model="selectedCourseId" placeholder="全部课程" clearable @change="fetchData">
          <el-option label="全部课程" :value="0" />
          <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-button type="primary" :icon="Refresh" :loading="loading" @click="fetchData">
          刷新
        </el-button>
      </div>
    </section>

    <div v-if="loading && !data" class="loading-container">
      <el-icon class="is-loading" :size="32"><Loading /></el-icon>
      <span>正在分析学习数据...</span>
    </div>

    <template v-else-if="data">
      <section class="path-summary-grid">
        <el-card v-for="item in summaryCards" :key="item.label" shadow="never" class="path-summary-card">
          <div class="summary-copy">
            <span>{{ item.label }}</span>
            <strong :class="item.tone">{{ item.value }}</strong>
            <small>{{ item.note }}</small>
          </div>
        </el-card>
      </section>

      <section class="path-layout">
        <el-card shadow="never" class="focus-card">
          <div class="card-heading">
            <div>
              <span class="section-kicker">NEXT STEP</span>
              <h3>优先处理</h3>
            </div>
            <el-tag :type="focusStep ? getStatusTagType(focusStep.masteryStatus) : 'info'" effect="light">
              {{ focusStep ? getStatusLabel(focusStep.masteryStatus) : '暂无建议' }}
            </el-tag>
          </div>

          <template v-if="focusStep">
            <div class="focus-title">{{ focusStep.knowledgePointName }}</div>
            <div class="focus-course">{{ focusStep.courseName }}</div>
            <p class="focus-recommendation">{{ focusStep.recommendation }}</p>
            <div class="focus-metrics">
              <span>正确率 {{ displayRate(focusStep.correctRate) }}</span>
              <span>练习 {{ focusStep.totalAttempts }} 次</span>
              <span>错题 {{ focusStep.wrongCount }} 道</span>
            </div>
            <div class="focus-actions">
              <el-button type="primary" :icon="Promotion" @click="startPractice(focusStep)">
                针对练习
              </el-button>
              <el-button :icon="Guide" @click="goKnowledgeGraph(focusStep)">
                看知识图谱
              </el-button>
            </div>
          </template>

          <el-empty v-else description="暂无需要排序的知识点">
            <el-button type="primary" :icon="Promotion" @click="router.push('/practice')">
              去刷题积累数据
            </el-button>
          </el-empty>
        </el-card>

        <el-card shadow="never" class="status-card">
          <div class="card-heading">
            <div>
              <span class="section-kicker">STATUS</span>
              <h3>路径分布</h3>
            </div>
          </div>
          <div class="status-stack">
            <button
              v-for="item in statusOptions"
              :key="item.value"
              class="status-pill"
              :class="{ active: statusFilter === item.value }"
              type="button"
              @click="statusFilter = item.value"
            >
              <span>{{ item.label }}</span>
              <strong>{{ statusCounts[item.value] }}</strong>
            </button>
          </div>
        </el-card>
      </section>

      <el-card v-if="data.courseOverviews.length > 0" shadow="never" class="course-card">
        <div class="card-heading">
          <div>
            <span class="section-kicker">COURSES</span>
            <h3>课程掌握概况</h3>
          </div>
          <span class="card-note">{{ courseScopeLabel }}</span>
        </div>
        <div class="course-overviews">
          <div v-for="co in data.courseOverviews" :key="co.courseId" class="course-item">
            <div class="course-header">
              <span class="course-name">{{ co.courseName }}</span>
              <span class="course-rate" :class="getRateClass(co.correctRate)">{{ co.correctRate }}%</span>
            </div>
            <el-progress :percentage="clampPercent(co.correctRate)" :color="getProgressColor(co.correctRate)" :stroke-width="10" />
            <div class="course-meta">
              <span>知识点 {{ co.masteredPointCount }}/{{ co.knowledgePointCount }} 已掌握</span>
              <span>练习 {{ co.totalAttempts }} 次</span>
            </div>
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="path-table-card">
        <div class="path-toolbar">
          <div>
            <span class="section-kicker">ROADMAP</span>
            <strong>推荐学习路径</strong>
          </div>
          <span>当前显示 {{ filteredSteps.length }} / {{ data.steps.length }} 个知识点</span>
        </div>

        <el-table class="desktop-path-table" :data="filteredSteps" stripe style="width: 100%" :row-class-name="getRowClass">
          <el-table-column label="#" width="64" align="center">
            <template #default="{ row }">
              <span class="order-badge" :class="getStatusBadgeClass(row.masteryStatus)">{{ row.order }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="knowledgePointName" label="知识点" min-width="190">
            <template #default="{ row }">
              <div class="kp-name">{{ row.knowledgePointName }}</div>
              <div class="kp-course">{{ row.courseName }}</div>
            </template>
          </el-table-column>
          <el-table-column label="掌握状态" width="116" align="center">
            <template #default="{ row }">
              <el-tag :type="getStatusTagType(row.masteryStatus)" size="small">
                {{ getStatusLabel(row.masteryStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="正确率" width="104" align="center">
            <template #default="{ row }">
              <span :class="getRateClass(row.correctRate)">{{ displayRate(row.correctRate) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="练习/错题" width="112" align="center">
            <template #default="{ row }">
              <span>{{ row.totalAttempts }}/{{ row.wrongCount }}</span>
            </template>
          </el-table-column>
          <el-table-column label="学习建议" min-width="280">
            <template #default="{ row }">
              <span class="recommendation">{{ row.recommendation }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="104" align="center">
            <template #default="{ row }">
              <el-button size="small" :icon="Promotion" @click="startPracticeFromTable(row)">
                练习
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="当前筛选下暂无知识点" />
          </template>
        </el-table>

        <div class="mobile-path-list">
          <article v-for="row in filteredSteps" :key="row.knowledgePointId" class="path-step-card">
            <div class="step-card-head">
              <span class="order-badge" :class="getStatusBadgeClass(row.masteryStatus)">{{ row.order }}</span>
              <div>
                <strong>{{ row.knowledgePointName }}</strong>
                <small>{{ row.courseName }}</small>
              </div>
              <el-tag :type="getStatusTagType(row.masteryStatus)" size="small">
                {{ getStatusLabel(row.masteryStatus) }}
              </el-tag>
            </div>
            <p>{{ row.recommendation }}</p>
            <div class="step-card-meta">
              <span>正确率 {{ displayRate(row.correctRate) }}</span>
              <span>练习 {{ row.totalAttempts }} 次</span>
              <span>错题 {{ row.wrongCount }} 道</span>
            </div>
            <el-button type="primary" :icon="Promotion" @click="startPractice(row)">
              针对练习
            </el-button>
          </article>
          <el-empty v-if="filteredSteps.length === 0" description="当前筛选下暂无知识点" />
        </div>
      </el-card>

      <el-empty v-if="data.steps.length === 0" description="暂无知识点数据，请先添加课程和知识点" />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Guide, Loading, Promotion, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getLearningPath, type LearningPath, type LearningPathStep } from '@/api/statistics'
import { getAllCourses, type CourseVO } from '@/api/course'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const data = ref<LearningPath | null>(null)
const selectedCourseId = ref<number>(0)
const statusFilter = ref('ALL')
const courses = ref<CourseVO[]>([])

const statusOptions = [
  { value: 'ALL', label: '全部' },
  { value: 'WEAK', label: '薄弱' },
  { value: 'NEEDS_REVIEW', label: '需复习' },
  { value: 'NOT_STARTED', label: '未开始' },
  { value: 'MASTERED', label: '已掌握' },
]

const filteredSteps = computed(() => {
  if (!data.value) return []
  if (statusFilter.value === 'ALL') return data.value.steps
  return data.value.steps.filter((step) => step.masteryStatus === statusFilter.value)
})

const focusStep = computed(() => {
  if (!data.value || data.value.steps.length === 0) return null
  return data.value.steps[0]
})

const statusCounts = computed<Record<string, number>>(() => {
  const counts: Record<string, number> = {
    ALL: data.value?.steps.length || 0,
    WEAK: 0,
    NEEDS_REVIEW: 0,
    NOT_STARTED: 0,
    MASTERED: 0,
  }
  data.value?.steps.forEach((step) => {
    counts[step.masteryStatus] += 1
  })
  return counts
})

const courseScopeLabel = computed(() => {
  if (!data.value?.courseName || data.value.courseName === '全部课程') return '全部课程'
  return data.value.courseName
})

const summaryCards = computed(() => {
  const current = data.value
  return [
    {
      label: '总体掌握率',
      value: current ? `${current.overallMastery}%` : '-',
      note: current ? `已掌握 ${current.masteredCount} / ${current.totalKnowledgePoints} 个知识点` : '等待分析',
      tone: 'tone-primary',
    },
    {
      label: '优先薄弱点',
      value: current?.weakCount || 0,
      note: '建议先进入路径前列处理',
      tone: 'tone-danger',
    },
    {
      label: '需复习',
      value: statusCounts.value.NEEDS_REVIEW,
      note: '适合安排间隔复习',
      tone: 'tone-warning',
    },
    {
      label: '推荐步骤',
      value: current?.steps.length || 0,
      note: `${courseScopeLabel.value}范围`,
      tone: 'tone-success',
    },
  ]
})

async function fetchData() {
  loading.value = true
  try {
    const res = await getLearningPath(selectedCourseId.value || undefined)
    data.value = res.data
  } catch (e: any) {
    ElMessage.error(e.message || '获取学习路径失败')
  } finally {
    loading.value = false
  }
}

async function fetchCourses() {
  try {
    const res = await getAllCourses()
    courses.value = (res as any).data || res || []
  } catch {
    // 课程筛选加载失败不影响学习路径主体展示
  }
}

function startPractice(step: LearningPathStep) {
  router.push({ path: '/practice', query: { knowledgePointId: String(step.knowledgePointId) } })
}

function startPracticeFromTable(step: any) {
  startPractice(step as LearningPathStep)
}

function goKnowledgeGraph(step: LearningPathStep) {
  router.push({ path: '/knowledge-graph', query: { courseId: String(step.courseId) } })
}

function getStatusTagType(status: string) {
  switch (status) {
    case 'MASTERED': return 'success'
    case 'NEEDS_REVIEW': return 'warning'
    case 'WEAK': return 'danger'
    case 'NOT_STARTED': return 'info'
    default: return 'info'
  }
}

function getStatusLabel(status: string) {
  switch (status) {
    case 'MASTERED': return '已掌握'
    case 'NEEDS_REVIEW': return '需复习'
    case 'WEAK': return '薄弱'
    case 'NOT_STARTED': return '未开始'
    default: return status
  }
}

function getStatusBadgeClass(status: string) {
  switch (status) {
    case 'MASTERED': return 'badge-mastered'
    case 'NEEDS_REVIEW': return 'badge-review'
    case 'WEAK': return 'badge-weak'
    case 'NOT_STARTED': return 'badge-not-started'
    default: return ''
  }
}

function getRowClass({ row }: { row: LearningPathStep }) {
  if (row.masteryStatus === 'WEAK') return 'row-weak'
  return ''
}

function getRateClass(rate: number) {
  if (rate < 0) return 'text-muted'
  if (rate >= 70) return 'rate-good'
  if (rate >= 50) return 'rate-medium'
  return 'rate-bad'
}

function getProgressColor(rate: number) {
  if (rate >= 70) return '#2f855a'
  if (rate >= 50) return '#b7791f'
  return '#c2413b'
}

function clampPercent(value: number) {
  return Math.max(0, Math.min(100, value))
}

function displayRate(rate: number) {
  return rate >= 0 ? `${rate}%` : '未练习'
}

onMounted(() => {
  const courseId = Number(route.query.courseId)
  if (Number.isFinite(courseId) && courseId > 0) {
    selectedCourseId.value = courseId
  }
  fetchCourses()
  fetchData()
})
</script>

<style scoped>
.learning-path {
  padding: 24px;
}

.page-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 18px;
  padding: 24px;
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  background:
    linear-gradient(135deg, rgba(23, 105, 170, 0.08), rgba(216, 168, 63, 0.11)),
    var(--lp-surface);
}

.section-kicker {
  display: inline-block;
  margin-bottom: 8px;
  color: var(--lp-primary);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.page-hero h2,
.card-heading h3 {
  margin: 0;
  color: var(--lp-text);
  font-weight: 850;
}

.page-hero h2 {
  font-size: 24px;
}

.page-hero p {
  margin: 8px 0 0;
  max-width: 650px;
  color: var(--lp-text-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.hero-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.hero-actions .el-select {
  width: 240px;
}

.path-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}

.path-summary-card :deep(.el-card__body) {
  min-height: 108px;
}

.summary-copy span,
.summary-copy small {
  display: block;
  color: var(--lp-text-muted);
  font-size: 12px;
}

.summary-copy strong {
  display: block;
  margin: 8px 0 6px;
  color: var(--lp-text);
  font-size: 28px;
  font-weight: 850;
  line-height: 1.1;
}

.tone-primary {
  color: var(--lp-primary) !important;
}

.tone-success {
  color: var(--lp-success) !important;
}

.tone-danger {
  color: var(--lp-danger) !important;
}

.tone-warning {
  color: var(--lp-warning) !important;
}

.path-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(280px, 0.7fr);
  gap: 16px;
  margin-bottom: 16px;
}

.card-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.card-heading h3 {
  font-size: 18px;
}

.card-note {
  color: var(--lp-text-muted);
  font-size: 13px;
}

.focus-title {
  color: var(--lp-text);
  font-size: 24px;
  font-weight: 850;
  line-height: 1.25;
}

.focus-course {
  margin-top: 6px;
  color: var(--lp-text-muted);
  font-size: 13px;
}

.focus-recommendation {
  margin: 16px 0;
  color: var(--lp-text-secondary);
  font-size: 14px;
  line-height: 1.8;
}

.focus-metrics,
.focus-actions,
.step-card-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.focus-metrics span,
.step-card-meta span {
  padding: 5px 8px;
  border-radius: 6px;
  background: var(--lp-surface-soft);
  color: var(--lp-text-secondary);
  font-size: 12px;
}

.focus-actions {
  margin-top: 18px;
}

.status-stack {
  display: grid;
  gap: 10px;
}

.status-pill {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 11px 12px;
  border: 1px solid var(--lp-border);
  border-radius: 8px;
  background: var(--lp-surface);
  color: var(--lp-text-secondary);
  cursor: pointer;
  text-align: left;
}

.status-pill.active {
  border-color: var(--lp-primary);
  background: var(--lp-primary-soft);
  color: var(--lp-primary);
}

.status-pill strong {
  color: var(--lp-text);
  font-size: 16px;
}

.course-card,
.path-table-card {
  margin-bottom: 16px;
}

.course-overviews {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 14px;
}

.course-item {
  padding: 14px;
  border: 1px solid var(--lp-border);
  border-radius: 8px;
  background: var(--lp-surface-soft);
}

.course-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.course-name {
  min-width: 0;
  color: var(--lp-text);
  font-weight: 750;
}

.course-rate {
  flex: 0 0 auto;
  font-size: 16px;
  font-weight: 850;
}

.course-meta {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin-top: 7px;
  color: var(--lp-text-muted);
  font-size: 12px;
}

.path-table-card :deep(.el-card__body) {
  padding: 0 !important;
}

.path-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px;
  border-bottom: 1px solid var(--lp-border);
  background: var(--lp-surface-soft);
}

.path-toolbar strong {
  display: block;
  color: var(--lp-text);
  font-size: 16px;
}

.path-toolbar span:last-child {
  color: var(--lp-text-muted);
  font-size: 13px;
}

.order-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: #7a8999;
  color: #fff;
  font-size: 12px;
  font-weight: 800;
}

.badge-mastered {
  background: var(--lp-success);
}

.badge-review {
  background: var(--lp-warning);
}

.badge-weak {
  background: var(--lp-danger);
}

.badge-not-started {
  background: var(--lp-text-muted);
}

.kp-name {
  color: var(--lp-text);
  font-weight: 650;
}

.kp-course {
  margin-top: 3px;
  color: var(--lp-text-muted);
  font-size: 12px;
}

.recommendation {
  color: var(--lp-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.rate-good {
  color: var(--lp-success);
  font-weight: 750;
}

.rate-medium {
  color: var(--lp-warning);
  font-weight: 750;
}

.rate-bad {
  color: var(--lp-danger);
  font-weight: 750;
}

.text-muted {
  color: var(--lp-text-muted);
}

:deep(.row-weak) {
  background-color: #fff7f6 !important;
}

.mobile-path-list {
  display: none;
  padding: 14px;
}

.path-step-card {
  padding: 14px;
  border: 1px solid var(--lp-border);
  border-radius: 8px;
  background: var(--lp-surface);
}

.path-step-card + .path-step-card {
  margin-top: 12px;
}

.step-card-head {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
}

.step-card-head strong,
.step-card-head small {
  display: block;
}

.step-card-head strong {
  color: var(--lp-text);
}

.step-card-head small {
  margin-top: 3px;
  color: var(--lp-text-muted);
  font-size: 12px;
}

.path-step-card p {
  margin: 12px 0;
  color: var(--lp-text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.path-step-card .el-button {
  margin-top: 12px;
  width: 100%;
}

.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 72px 0;
  color: var(--lp-text-muted);
}

@media (max-width: 980px) {
  .path-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .path-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 767px) {
  .learning-path {
    padding: 16px;
  }

  .page-hero {
    align-items: stretch;
    flex-direction: column;
    padding: 18px;
  }

  .page-hero h2 {
    font-size: 21px;
  }

  .hero-actions,
  .hero-actions .el-select,
  .hero-actions .el-button,
  .focus-actions .el-button {
    width: 100%;
  }

  .path-summary-grid {
    grid-template-columns: 1fr;
  }

  .course-meta,
  .path-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .desktop-path-table {
    display: none;
  }

  .mobile-path-list {
    display: block;
  }

  .step-card-head {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .step-card-head .el-tag {
    grid-column: 2;
    justify-self: flex-start;
  }
}
</style>
