<template>
  <div class="practice-container page-container">
    <LpPageHeader title="练习" description="选择条件后开始一组练习；推荐仅帮助你理解当前难度。" />

    <div class="practice-grid">
      <section class="config-section">
        <LpSectionHeading title="开始练习" description="选择范围后开始一组练习。" />
        <el-card class="config-card" ref="configCardRef" shadow="never">
          <el-form :model="form" label-position="top">
            <el-form-item label="出题方式">
              <el-radio-group v-model="mode">
                <el-radio-button label="adaptive">智能推荐</el-radio-button>
                <el-radio-button label="custom">自选条件</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <div class="config-fields">
              <el-form-item label="选择课程"
                ><el-select
                  v-model="form.courseId"
                  placeholder="全部课程"
                  clearable
                  style="width: 100%"
                  @change="handleCourseChange"
                  ><el-option
                    v-for="course in courseList"
                    :key="course.id"
                    :label="course.name"
                    :value="course.id" /></el-select
              ></el-form-item>
              <el-form-item v-if="form.courseId" label="知识点"
                ><el-select v-model="form.knowledgePointId" placeholder="全部知识点" clearable style="width: 100%"
                  ><el-option
                    v-for="point in knowledgePoints"
                    :key="point.id"
                    :label="point.name"
                    :value="point.id" /></el-select
              ></el-form-item>
              <el-form-item v-if="mode === 'custom'" label="题型"
                ><el-select v-model="form.questionType" placeholder="全部题型" clearable style="width: 100%"
                  ><el-option label="单选题" value="SINGLE_CHOICE" /><el-option
                    label="多选题"
                    value="MULTIPLE_CHOICE" /><el-option label="判断题" value="TRUE_FALSE" /><el-option
                    label="填空题"
                    value="FILL_BLANK" /><el-option label="简答题" value="SHORT_ANSWER" /></el-select
              ></el-form-item>
              <el-form-item v-if="mode === 'custom'" label="难度"
                ><el-rate v-model="form.difficulty" :max="5" allow-half
              /></el-form-item>
              <el-form-item label="题目数量"><el-input-number v-model="form.count" :min="1" :max="50" /></el-form-item>
            </div>
            <el-form-item
              ><el-button type="primary" size="large" :icon="Promotion" @click="startPractice" :loading="loading"
                >开始刷题</el-button
              ></el-form-item
            >
          </el-form>
        </el-card>
      </section>

      <section class="adaptive-section" aria-label="推荐依据">
        <LpSectionHeading title="智能推荐" description="根据你已完成的练习记录调整难度。" />
        <div class="adaptive-panel" v-loading="adaptiveLoading">
          <div v-if="adaptiveSummary" class="adaptive-content">
            <p class="recommend-label">当前推荐难度</p>
            <strong class="recommend-value">{{ adaptiveSummary.recommendedDifficulty.toFixed(1) }} / 5</strong>
            <details class="recommend-details">
              <summary>查看推荐依据</summary>
              <dl>
                <div v-for="item in adaptiveSummary.difficultyDetails" :key="item.difficulty">
                  <dt>{{ item.label }}</dt>
                  <dd>{{ item.total }} 题 · {{ item.total > 0 ? `正确率 ${item.correctRate}%` : '暂无数据' }}</dd>
                </div>
              </dl>
            </details>
          </div>
          <LpStatePanel
            v-else-if="adaptiveError"
            state="error"
            title="推荐概览暂时无法加载"
            description="可重试，或直接选择练习条件。"
            @retry="loadAdaptiveSummary"
          />
          <LpEmptyState v-else-if="!adaptiveLoading" title="暂无答题记录" description="完成练习后会显示推荐难度。"
            ><template #actions
              ><el-button type="primary" :icon="Promotion" @click="scrollToConfig">查看练习设置</el-button></template
            ></LpEmptyState
          >
        </div>
      </section>
    </div>

    <section class="stats-section" aria-label="全课程练习统计">
      <LpSectionHeading title="全课程练习记录" />
      <div v-if="statsLoading" class="stats-grid"><LpSkeleton v-for="n in 4" :key="n" :rows="1" /></div>
      <div v-else-if="stats" class="stats-grid">
        <LpStat v-for="item in statCards" :key="item.label" :label="item.label" :value="item.value" />
      </div>
      <LpStatePanel
        v-else-if="statsError"
        state="error"
        title="练习统计暂时无法加载"
        description="请重试后再查看真实学习数据。"
        @retry="loadStats"
      />
    </section>
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from '@/stores/user'
import { savePracticeSession } from '@/utils/practiceSession'
import { ref, reactive, onBeforeUnmount, onMounted, computed } from 'vue'
import { getCoursePage, type CourseVO } from '@/api/course'
import { getKnowledgeTree, type KnowledgePointVO } from '@/api/knowledgePoint'
import { useRoute, useRouter } from 'vue-router'
import { getAuthSessionVersion, isAuthenticated, onAuthSessionChange } from '@/utils/auth'
import { ElMessage } from 'element-plus'
import { Promotion } from '@element-plus/icons-vue'
import { getAdaptiveQuestions, getPracticeQuestions, getPracticeStats, getAdaptiveSummary } from '@/api/practice'
import type { PracticeStatsVO, AdaptiveSummaryVO } from '@/api/practice'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const mode = ref<'adaptive' | 'custom'>('adaptive')
const statsLoading = ref(true)
const adaptiveLoading = ref(true)
const stats = ref<PracticeStatsVO | null>(null)
const adaptiveSummary = ref<AdaptiveSummaryVO | null>(null)
const statsError = ref(false)
const adaptiveError = ref(false)
const courseList = ref<CourseVO[]>([])
const knowledgePoints = ref<KnowledgePointVO[]>([])
const configCardRef = ref<{ $el: HTMLElement } | null>(null)

const form = reactive({
  courseId: undefined as number | undefined,
  knowledgePointId: undefined as number | undefined,
  questionType: '' as string,
  difficulty: undefined as number | undefined,
  count: 10,
})

const statCards = computed(() => [
  { label: '总答题数', value: stats.value?.totalAnswered ?? 0, tone: 'emphasis' as const },
  { label: '答对数', value: stats.value?.correctCount ?? 0, tone: 'default' as const },
  { label: '答错数', value: stats.value?.wrongCount ?? 0, tone: 'danger' as const },
  { label: '正确率', value: `${stats.value?.correctRate ?? 0}%`, tone: 'warning' as const },
])

function positiveQueryNumber(value: unknown) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : undefined
}

let alive = true
let generation = 0

function requestIsCurrent(requestGeneration: number, session: number) {
  return alive && requestGeneration === generation && session === getAuthSessionVersion()
}

function flattenKnowledgePoints(points: KnowledgePointVO[]): KnowledgePointVO[] {
  return points.flatMap((point) => [point, ...flattenKnowledgePoints(point.children || [])])
}

onMounted(() => {
  const presetCourseId = positiveQueryNumber(route.query.courseId)
  const presetKnowledgePointId = positiveQueryNumber(route.query.knowledgePointId)
  if (presetCourseId !== undefined) form.courseId = presetCourseId
  if (presetKnowledgePointId !== undefined) form.knowledgePointId = presetKnowledgePointId
  void loadStats()
  void loadCourses()
  void loadAdaptiveSummary()
  if (form.courseId) void loadKnowledgePoints(form.courseId)
})

onBeforeUnmount(() => {
  alive = false
  generation++
  unsubscribeSession()
})

const unsubscribeSession = onAuthSessionChange(() => {
  generation++
  stats.value = null
  adaptiveSummary.value = null
  courseList.value = []
  knowledgePoints.value = []
  statsError.value = false
  adaptiveError.value = false
  loading.value = false
  if (!isAuthenticated()) {
    statsLoading.value = false
    adaptiveLoading.value = false
    return
  }
  statsLoading.value = true
  adaptiveLoading.value = true
  void loadStats()
  void loadCourses()
  void loadAdaptiveSummary()
  if (form.courseId) void loadKnowledgePoints(form.courseId)
})

const loadStats = async () => {
  const requestGeneration = generation
  const session = getAuthSessionVersion()
  statsLoading.value = true
  statsError.value = false
  try {
    const res = await getPracticeStats({ errorDisplay: 'inline' })
    if (!requestIsCurrent(requestGeneration, session)) return
    if (res.code === 0 && res.data) stats.value = res.data
    else statsError.value = true
  } catch {
    if (requestIsCurrent(requestGeneration, session)) statsError.value = true
  } finally {
    if (requestIsCurrent(requestGeneration, session)) statsLoading.value = false
  }
}

const loadCourses = async () => {
  const requestGeneration = generation
  const session = getAuthSessionVersion()
  try {
    const res = await getCoursePage({ pageNum: 1, pageSize: 100 })
    if (requestIsCurrent(requestGeneration, session)) courseList.value = res.data?.records ?? []
  } catch {
    if (requestIsCurrent(requestGeneration, session)) courseList.value = []
  }
}

const loadKnowledgePoints = async (courseId: number) => {
  const requestGeneration = generation
  const session = getAuthSessionVersion()
  knowledgePoints.value = []
  try {
    const res = await getKnowledgeTree(courseId)
    if (!requestIsCurrent(requestGeneration, session) || form.courseId !== courseId) return
    knowledgePoints.value = res.code === 0 && res.data ? flattenKnowledgePoints(res.data) : []
  } catch {
    if (requestIsCurrent(requestGeneration, session) && form.courseId === courseId) knowledgePoints.value = []
  }
}

const handleCourseChange = (courseId: number | undefined) => {
  form.knowledgePointId = undefined
  knowledgePoints.value = []
  if (courseId) void loadKnowledgePoints(courseId)
}

const loadAdaptiveSummary = async () => {
  const requestGeneration = generation
  const session = getAuthSessionVersion()
  adaptiveLoading.value = true
  adaptiveError.value = false
  try {
    const res = await getAdaptiveSummary({ errorDisplay: 'inline' })
    if (!requestIsCurrent(requestGeneration, session)) return
    if (res.code === 0 && res.data && res.data.totalAnswered > 0) adaptiveSummary.value = res.data
    else if (res.code !== 0) adaptiveError.value = true
  } catch {
    if (requestIsCurrent(requestGeneration, session)) adaptiveError.value = true
  } finally {
    if (requestIsCurrent(requestGeneration, session)) adaptiveLoading.value = false
  }
}

const startPractice = async () => {
  const userId = useUserStore().userInfo?.id
  const requestGeneration = generation
  const session = getAuthSessionVersion()
  loading.value = true
  try {
    const params: Parameters<typeof getPracticeQuestions>[0] = { count: form.count }
    if (form.courseId) params.courseId = form.courseId
    if (form.knowledgePointId) params.knowledgePointId = form.knowledgePointId
    if (mode.value === 'custom') {
      if (form.questionType) params.questionType = form.questionType
      if (form.difficulty) params.difficulty = form.difficulty
    }

    const res = mode.value === 'adaptive' ? await getAdaptiveQuestions(params) : await getPracticeQuestions(params)
    if (!requestIsCurrent(requestGeneration, session)) return
    if (res.code === 0 && res.data && res.data.length > 0) {
      if (!savePracticeSession(userId, res.data, mode.value)) return
      await router.push({ name: 'PracticeSession' })
    } else {
      ElMessage.warning('未找到符合条件的题目，请调整筛选条件')
    }
  } catch {
    if (requestIsCurrent(requestGeneration, session)) ElMessage.error('获取题目失败')
  } finally {
    if (requestIsCurrent(requestGeneration, session)) loading.value = false
  }
}

const scrollToConfig = () => {
  configCardRef.value?.$el?.scrollIntoView({ behavior: 'smooth' })
}
</script>

<style scoped>
.practice-container {
  display: flex;
  flex-direction: column;
  gap: var(--lp-space-8);
}
.stats-section,
.adaptive-section,
.config-section {
  display: grid;
  gap: var(--lp-space-4);
}
.practice-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(300px, 0.55fr);
  gap: var(--lp-space-6);
  align-items: start;
}
.config-card,
.adaptive-panel {
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
  background: var(--lp-surface);
}
.config-card :deep(.el-card__body),
.adaptive-panel {
  padding: var(--lp-space-5);
}
.config-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 var(--lp-space-4);
}
.adaptive-content {
  display: grid;
  gap: var(--lp-space-3);
}
.recommend-label {
  margin: 0;
  color: var(--lp-text-muted);
  font-size: var(--lp-text-sm);
}
.recommend-value {
  color: var(--lp-text);
  font-size: var(--lp-text-2xl);
  font-variant-numeric: tabular-nums;
}
.recommend-details {
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}
.recommend-details summary {
  cursor: pointer;
  color: var(--lp-primary);
  font-weight: var(--lp-weight-semibold);
}
.recommend-details dl {
  display: grid;
  gap: var(--lp-space-2);
  margin: var(--lp-space-3) 0 0;
}
.recommend-details dl div {
  display: flex;
  justify-content: space-between;
  gap: var(--lp-space-3);
}
.recommend-details dt,
.recommend-details dd {
  margin: 0;
}
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--lp-space-3);
}
@media (max-width: 960px) {
  .practice-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 640px) {
  .config-card :deep(.el-card__body),
  .adaptive-panel {
    padding: var(--lp-space-4);
  }
  .config-fields,
  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>
