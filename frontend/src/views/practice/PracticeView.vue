<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Play, RotateCcw } from '@lucide/vue'
import { getAdaptiveQuestions, getAdaptiveSummary, getPracticeQuestions, getPracticeStats } from '@/api/practice'
import type { AdaptiveSummaryVO, PracticeStatsVO } from '@/api/practice'
import { getCoursePage, type CourseVO } from '@/api/course'
import { getKnowledgeTree, type KnowledgePointVO } from '@/api/knowledgePoint'
import { LtButton, LtCard, LtSegmented } from '@/components/living'
import { useLivingTheme } from '@/composables/useLivingTheme'
import { useUserStore } from '@/stores/user'
import { getAuthSessionVersion, isAuthenticated, onAuthSessionChange } from '@/utils/auth'
import { savePracticeSession } from '@/utils/practiceSession'

defineOptions({ name: 'PracticeView' })
useLivingTheme({ dark: true })
const router = useRouter()
const route = useRoute()
const loading = ref(false),
  statsLoading = ref(true),
  adaptiveLoading = ref(true),
  statsError = ref(false),
  adaptiveError = ref(false),
  startError = ref('')
const coursesLoading = ref(true),
  coursesError = ref(''),
  pointsLoading = ref(false),
  pointsError = ref('')
const mode = ref<'adaptive' | 'custom'>('adaptive')
const stats = ref<PracticeStatsVO | null>(null),
  adaptiveSummary = ref<AdaptiveSummaryVO | null>(null)
const courseList = ref<CourseVO[]>([]),
  knowledgePoints = ref<KnowledgePointVO[]>([])
const form = reactive({
  courseId: undefined as number | undefined,
  knowledgePointId: undefined as number | undefined,
  questionType: '',
  difficulty: undefined as number | undefined,
  count: 10,
})
const modeOptions: { value: 'adaptive' | 'custom'; label: string }[] = [
  { value: 'adaptive', label: '智能推荐' },
  { value: 'custom', label: '自选条件' },
]
const statCards = computed(() =>
  stats.value
    ? [
        { label: '总答题数', value: stats.value.totalAnswered },
        { label: '答对数', value: stats.value.correctCount },
        { label: '答错数', value: stats.value.wrongCount },
        { label: '正确率', value: `${stats.value.correctRate}%` },
      ]
    : [],
)
function positiveQueryNumber(value: unknown) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : undefined
}
let alive = true,
  generation = 0
function requestIsCurrent(requestGeneration: number, session: number) {
  return alive && requestGeneration === generation && session === getAuthSessionVersion()
}
function flattenKnowledgePoints(points: KnowledgePointVO[]): KnowledgePointVO[] {
  return points.flatMap((point) => [point, ...flattenKnowledgePoints(point.children || [])])
}
onMounted(() => {
  const courseId = positiveQueryNumber(route.query.courseId),
    pointId = positiveQueryNumber(route.query.knowledgePointId)
  if (courseId !== undefined) form.courseId = courseId
  if (pointId !== undefined) form.knowledgePointId = pointId
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
async function loadStats() {
  const requestGeneration = generation,
    session = getAuthSessionVersion()
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
async function loadCourses() {
  const requestGeneration = generation,
    session = getAuthSessionVersion()
  coursesLoading.value = true
  coursesError.value = ''
  try {
    const res = await getCoursePage({ pageNum: 1, pageSize: 100 }, { errorDisplay: 'inline' })
    if (requestIsCurrent(requestGeneration, session)) courseList.value = res.data?.records ?? []
  } catch {
    if (requestIsCurrent(requestGeneration, session)) {
      courseList.value = []
      coursesError.value = '课程暂时无法加载。'
    }
  } finally {
    if (requestIsCurrent(requestGeneration, session)) coursesLoading.value = false
  }
}
async function loadKnowledgePoints(courseId: number) {
  const requestGeneration = generation,
    session = getAuthSessionVersion()
  knowledgePoints.value = []
  pointsLoading.value = true
  pointsError.value = ''
  try {
    const res = await getKnowledgeTree(courseId, { errorDisplay: 'inline' })
    if (!requestIsCurrent(requestGeneration, session) || form.courseId !== courseId) return
    knowledgePoints.value = res.code === 0 && res.data ? flattenKnowledgePoints(res.data) : []
  } catch {
    if (requestIsCurrent(requestGeneration, session) && form.courseId === courseId) {
      knowledgePoints.value = []
      pointsError.value = '知识点暂时无法加载。'
    }
  } finally {
    if (requestIsCurrent(requestGeneration, session) && form.courseId === courseId) pointsLoading.value = false
  }
}
function handleCourseChange() {
  pointsError.value = ''
  pointsLoading.value = false
  form.knowledgePointId = undefined
  knowledgePoints.value = []
  if (form.courseId) void loadKnowledgePoints(form.courseId)
}
async function loadAdaptiveSummary() {
  const requestGeneration = generation,
    session = getAuthSessionVersion()
  adaptiveLoading.value = true
  adaptiveSummary.value = null
  adaptiveError.value = false
  try {
    const res = await getAdaptiveSummary({ errorDisplay: 'inline' })
    if (!requestIsCurrent(requestGeneration, session)) return
    if (res.code === 0 && res.data) adaptiveSummary.value = res.data
    else adaptiveError.value = true
  } catch {
    if (requestIsCurrent(requestGeneration, session)) adaptiveError.value = true
  } finally {
    if (requestIsCurrent(requestGeneration, session)) adaptiveLoading.value = false
  }
}
async function startPractice() {
  const userId = useUserStore().userInfo?.id,
    requestGeneration = generation,
    session = getAuthSessionVersion()
  if (loading.value) return
  startError.value = ''
  if (!Number.isInteger(form.count) || form.count < 1 || form.count > 50) {
    startError.value = '请输入 1 到 50 之间的题目数。'
    return
  }
  loading.value = true
  try {
    const params: Parameters<typeof getPracticeQuestions>[0] = { count: form.count }
    if (form.courseId) params.courseId = form.courseId
    if (form.knowledgePointId) params.knowledgePointId = form.knowledgePointId
    if (mode.value === 'custom') {
      if (form.questionType) params.questionType = form.questionType
      if (form.difficulty) params.difficulty = form.difficulty
    }
    const res =
      mode.value === 'adaptive'
        ? await getAdaptiveQuestions(params, { errorDisplay: 'inline' })
        : await getPracticeQuestions(params, { errorDisplay: 'inline' })
    if (!requestIsCurrent(requestGeneration, session)) return
    if (res.code === 0 && res.data && res.data.length > 0) {
      if (!savePracticeSession(userId, res.data, mode.value)) return
      await router.push({ name: 'PracticeSession' })
    } else startError.value = '未找到符合条件的题目，请调整筛选条件。'
  } catch {
    if (requestIsCurrent(requestGeneration, session)) startError.value = '获取题目失败，请重试。'
  } finally {
    if (requestIsCurrent(requestGeneration, session)) loading.value = false
  }
}
</script>

<template>
  <main class="lt page" data-appearance="dark">
    <header class="head">
      <h1>练习</h1>
      <p>选好范围，开始一组专注练习。推荐只根据已经完成的练习调整难度。</p>
    </header>
    <div class="grid">
      <LtCard as="section" radius="xl" class="configuration" aria-labelledby="config-title"
        ><h2 id="config-title">开始练习</h2>
        <p class="section-lead">每次只设置需要的条件，其他条件会交给题库选择。</p>
        <LtSegmented v-model="mode" :options="modeOptions" label="出题方式" />
        <div class="fields">
          <label
            >选择课程<select
              v-model="form.courseId"
              aria-label="选择课程"
              :disabled="coursesLoading || !!coursesError"
              @change="handleCourseChange"
            >
              <option :value="undefined">{{ coursesLoading ? '正在加载课程…' : '全部课程' }}</option>
              <option v-for="course in courseList" :key="course.id" :value="course.id">{{ course.name }}</option>
            </select></label
          ><label v-if="form.courseId"
            >知识点<select
              v-model="form.knowledgePointId"
              aria-label="选择知识点"
              :disabled="pointsLoading || !!pointsError"
            >
              <option :value="undefined">{{ pointsLoading ? '正在加载知识点…' : '全部知识点' }}</option>
              <option v-for="point in knowledgePoints" :key="point.id" :value="point.id">{{ point.name }}</option>
            </select></label
          ><template v-if="mode === 'custom'"
            ><label
              >题型<select v-model="form.questionType">
                <option value="">全部题型</option>
                <option value="SINGLE_CHOICE">单选题</option>
                <option value="MULTIPLE_CHOICE">多选题</option>
                <option value="TRUE_FALSE">判断题</option>
                <option value="FILL_BLANK">填空题</option>
                <option value="SHORT_ANSWER">简答题</option>
              </select></label
            ><label
              >难度<select v-model="form.difficulty">
                <option :value="undefined">全部难度</option>
                <option v-for="n in 10" :key="n" :value="n / 2">{{ n / 2 }} / 5</option>
              </select></label
            ></template
          ><label>题目数量<input v-model.number="form.count" type="number" min="1" max="50" step="1" /></label>
        </div>
        <div v-if="coursesError" role="alert" class="error">
          {{ coursesError }}<LtButton @click="loadCourses">重试课程</LtButton>
        </div>
        <div v-if="pointsError" role="alert" class="error">
          {{ pointsError
          }}<LtButton v-if="form.courseId" @click="loadKnowledgePoints(form.courseId)">重试知识点</LtButton>
        </div>
        <p v-if="startError" class="error" role="alert">{{ startError }}</p>
        <LtButton variant="primary" size="lg" :disabled="loading" @click="startPractice"
          ><template #icon><Play aria-hidden="true" /></template>{{ loading ? '正在准备…' : '开始刷题' }}</LtButton
        ></LtCard
      >
      <LtCard as="aside" radius="xl" class="recommendation" aria-labelledby="recommendation-title"
        ><h2 id="recommendation-title">智能推荐</h2>
        <template v-if="adaptiveLoading"
          ><div class="skeleton" />
          <div class="skeleton short" />
          <span class="sr-only">正在读取推荐</span></template
        ><template v-else-if="adaptiveSummary"
          ><p class="eyebrow">{{ adaptiveSummary.totalAnswered ? '推荐难度偏好' : '初次练习偏好' }}</p>
          <strong>{{ adaptiveSummary.recommendedDifficulty.toFixed(1) }} <small>/ 5</small></strong>
          <p>
            {{
              adaptiveSummary.totalAnswered
                ? '按你已经完成的练习记录计算。'
                : '优先从当前范围内较易的题目开始，再根据作答调整。'
            }}
          </p>
          <details v-if="adaptiveSummary.totalAnswered">
            <summary>查看推荐依据</summary>
            <dl>
              <div v-for="item in adaptiveSummary.difficultyDetails" :key="item.difficulty">
                <dt>{{ item.label }}</dt>
                <dd>{{ item.total }} 题 · {{ item.total ? `正确率 ${item.correctRate}%` : '暂无数据' }}</dd>
              </div>
            </dl>
          </details></template
        >
        <div v-else class="state" data-testid="state-panel">
          <p>推荐概览暂时无法加载。</p>
          <LtButton size="sm" @click="loadAdaptiveSummary"
            ><template #icon><RotateCcw aria-hidden="true" /></template>重试</LtButton
          >
        </div></LtCard
      >
    </div>
    <section class="records" aria-labelledby="records-title">
      <div>
        <h2 id="records-title">全课程练习记录</h2>
        <p>真实完成记录会在这里累计。</p>
      </div>
      <div v-if="statsLoading" class="stats loading" aria-busy="true">
        <span v-for="n in 4" :key="n" class="skeleton" />
      </div>
      <div v-else-if="stats" class="stats">
        <dl v-for="item in statCards" :key="item.label" data-testid="stat">
          <dt>{{ item.label }}</dt>
          <dd>{{ item.value }}</dd>
        </dl>
      </div>
      <div v-else class="state" data-testid="state-panel">
        <p>练习统计暂时无法加载。</p>
        <LtButton size="sm" @click="loadStats"
          ><template #icon><RotateCcw aria-hidden="true" /></template>重试</LtButton
        >
      </div>
    </section>
  </main>
</template>

<style scoped>
.page {
  max-width: calc(var(--lt-space-10) * 15 + var(--lt-space-5));
  margin: 0 auto;
  padding: 0 var(--lt-space-7) calc(var(--lt-space-10) + var(--lt-space-8));
}
.head {
  padding: var(--lt-space-7) 0 var(--lt-space-6);
}
.head h1 {
  margin: var(--lt-space-4) 0 0;
  font-size: var(--lt-text-title1);
}
.head > p,
.section-lead,
.recommendation > p,
.records > div > p {
  margin: var(--lt-space-2) 0 0;
  color: var(--lt-label-2);
  line-height: var(--lt-leading-reading);
}
.head > p {
  max-width: 60ch;
}
.grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(calc(var(--lt-space-10) * 4), 0.55fr);
  gap: var(--lt-space-6);
  align-items: start;
}
.configuration {
  display: grid;
  gap: var(--lt-space-5);
}
.configuration h2,
.recommendation h2,
.records h2 {
  margin: 0;
  font-size: var(--lt-text-title3);
}
.section-lead {
  margin-top: calc(var(--lt-space-2) * -1);
}
.fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--lt-space-4);
}
.fields label {
  display: grid;
  gap: var(--lt-space-2);
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
select,
input {
  width: 100%;
  height: var(--lt-control-lg);
  padding: 0 var(--lt-space-3);
  border: solid var(--lt-hairline);
  border-radius: var(--lt-radius-md);
  background: var(--lt-fill-2);
  color: var(--lt-label);
  font: inherit;
}
.error {
  margin: 0;
  color: var(--lt-red-text);
  font-size: var(--lt-text-footnote);
}
.recommendation {
  display: grid;
  gap: var(--lt-space-4);
}
.recommendation .eyebrow {
  margin: 0;
  font-size: var(--lt-text-footnote);
}
.recommendation strong {
  font-family: var(--lt-font-rounded);
  font-size: var(--lt-text-display);
  font-variant-numeric: tabular-nums;
}
.recommendation small {
  color: var(--lt-label-2);
  font-size: var(--lt-text-body);
  font-weight: 500;
}
.recommendation details {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.recommendation summary {
  color: var(--lt-accent);
  cursor: pointer;
}
.recommendation dl {
  display: grid;
  gap: var(--lt-space-2);
  margin: var(--lt-space-3) 0 0;
}
.recommendation dl div {
  display: flex;
  justify-content: space-between;
  gap: var(--lt-space-3);
}
.recommendation dt,
.recommendation dd {
  margin: 0;
}
.records {
  display: grid;
  gap: var(--lt-space-4);
  margin-top: var(--lt-space-8);
}
.stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--lt-space-3);
}
.stats dl {
  min-height: calc(var(--lt-space-10) + var(--lt-space-6));
  margin: 0;
  padding: var(--lt-space-5);
  border-radius: var(--lt-radius-lg);
  background: var(--lt-surface);
  box-shadow: var(--lt-shadow-card);
}
.stats dt {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.stats dd {
  margin: var(--lt-space-2) 0 0;
  font-family: var(--lt-font-rounded);
  font-size: var(--lt-text-title2);
  font-variant-numeric: tabular-nums;
}
.skeleton {
  display: block;
  min-height: var(--lt-space-4);
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill);
}
.recommendation .skeleton {
  width: calc(100% - var(--lt-space-5));
}
.recommendation .short {
  width: calc(50% - var(--lt-space-2));
}
.loading .skeleton {
  min-height: calc(var(--lt-space-10) + var(--lt-space-6));
}
.state {
  display: flex;
  align-items: center;
  gap: var(--lt-space-4);
  color: var(--lt-label-2);
}
.state p {
  margin: 0;
}
@media (max-width: 800px) {
  .grid,
  .fields,
  .stats {
    grid-template-columns: 1fr;
  }
}
</style>
