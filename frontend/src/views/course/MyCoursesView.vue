<template>
  <div class="lt course-page" data-appearance="dark">
    <header class="course-head">
      <LtIconButton label="返回课程库" @click="router.push({ name: 'CourseList' })"
        ><ChevronLeft aria-hidden="true"
      /></LtIconButton>
      <div class="my-heading">
        <h1>我的课程</h1>
        <LtButton v-if="courses.length" @click="router.push({ name: 'CourseList' })">浏览课程库</LtButton>
      </div>
      <p>{{ pageDescription }}</p>
    </header>
    <CoursePageState v-if="loading" state="loading" title="正在读取我的课程" />
    <CoursePageState
      v-else-if="loadFailed"
      state="error"
      title="暂时无法读取我的课程"
      description="课程和学习概况尚未加载，请重试。"
      @retry="fetchCourses"
    />
    <CoursePageState v-else-if="!courses.length" state="empty" title="还没有加入课程" :description="emptyDescription">
      <LtButton v-if="recommendedCourse" variant="primary" :disabled="joiningRecommended" @click="startRecommended">{{
        joiningRecommended ? '正在加入…' : '加入并开始学习'
      }}</LtButton>
      <LtButton v-else-if="recommendationError" @click="loadRecommendedCourse">重新查找推荐课程</LtButton>
      <LtButton v-if="!recommendedCourse && !recommendationLoading" @click="router.push({ name: 'CourseList' })"
        >浏览课程库</LtButton
      >
    </CoursePageState>
    <template v-else>
      <LtCard
        v-if="continueCourse"
        as="section"
        radius="xl"
        class="continue-section"
        aria-labelledby="continue-heading"
      >
        <div>
          <p class="course-muted">继续学习</p>
          <h2 id="continue-heading">{{ continueCourse.name }}</h2>
          <p v-if="continueTarget" class="course-muted">{{ continueTarget.title }} · {{ continueTarget.reason }}</p>
          <p v-else class="course-muted">回到课程空间，选择下一步学习内容。</p>
        </div>
        <LtButton variant="primary" :disabled="starting" @click="startContinue">{{
          starting ? '正在准备…' : '继续学习'
        }}</LtButton>
      </LtCard>
      <section class="my-list-section" aria-labelledby="course-list-heading">
        <div class="my-heading">
          <h2 id="course-list-heading">全部课程</h2>
          <span class="course-muted">共 {{ courses.length }} 门</span>
        </div>
        <div class="course-toolbar">
          <label class="course-search"
            ><Search aria-hidden="true" /><input
              v-model="keyword"
              type="search"
              placeholder="搜索我的课程"
              aria-label="搜索我的课程"
          /></label>
          <label class="course-select-label"
            >学习状态<select v-model="filter">
              <option value="all">全部状态</option>
              <option value="started">已开始学习</option>
              <option value="not-started">尚未开始</option>
              <option value="due">有到期复习</option>
            </select></label
          >
        </div>
        <CoursePageState
          v-if="!visibleCourses.length"
          state="empty"
          title="没有匹配的课程"
          description="换一个关键词或学习状态再试。"
          ><LtButton @click="clearFilters">清空筛选</LtButton></CoursePageState
        >
        <LtCard v-else radius="xl" padding="none" class="my-list">
          <article v-for="course in visibleCourses" :key="course.courseId" class="course-card">
            <BookOpen class="course-symbol" aria-hidden="true" />
            <div class="course-copy">
              <h3>{{ course.name }}</h3>
              <p class="course-muted">{{ course.description || '暂无课程描述' }}</p>
              <p v-if="course.overviewState === 'error'" class="course-error" role="alert">
                学习概况暂时无法读取。<LtButton variant="quiet" @click="retryOverview(course.courseId)"
                  >重新读取</LtButton
                >
              </p>
              <template v-else-if="course.overview">
                <p class="course-meta">
                  <template v-if="course.overview.lastLearningTime"
                    >上次学习 {{ formatRelativeTime(course.overview.lastLearningTime) }}</template
                  ><template v-else>尚未开始学习</template>
                </p>
                <dl class="course-facts" aria-label="课程学习事实">
                  <div>
                    <dt>已作答</dt>
                    <dd>{{ course.overview.answeredCount }}</dd>
                  </div>
                  <div>
                    <dt>答对</dt>
                    <dd>{{ course.overview.correctCount }}</dd>
                  </div>
                  <div v-if="course.overview.dueReviewCount">
                    <dt>到期复习</dt>
                    <dd>{{ course.overview.dueReviewCount }}</dd>
                  </div>
                </dl>
              </template>
            </div>
            <LtButton @click="openCourse(course.courseId)">进入课程空间</LtButton>
          </article>
        </LtCard>
        <nav v-if="pages > 1" class="course-pager" aria-label="我的课程翻页">
          <LtButton :disabled="page <= 1" @click="page--">上一页</LtButton><span>第 {{ page }} / {{ pages }} 页</span
          ><LtButton :disabled="page >= pages" @click="page++">下一页</LtButton>
        </nav>
      </section>
    </template>
    <p v-if="!courses.length && recommendationError && !loading && !loadFailed" class="course-error" role="alert">
      {{ recommendationError }}
    </p>
    <p v-if="!courses.length && joinError && !loading && !loadFailed" class="course-error" role="alert">
      {{ joinError }}
    </p>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { BookOpen, ChevronLeft, Search } from '@lucide/vue'
import { LtButton, LtCard, LtIconButton } from '@/components/living'
import CoursePageState from './CoursePageState.vue'
import './course-baseline.css'
import type { CourseVO, LearningTargetVO } from '@/api/course'
import {
  addCourseToLibrary,
  getAllCourses,
  getCourseOverview,
  getMyCourses,
  type CourseOverviewVO,
  type UserCourseVO,
} from '@/api/course'
import { openLearningTarget } from '@/utils/learningTarget'
import { formatRelativeTime } from '@/utils/format'
import { getAuthSessionVersion, isAuthenticated, onAuthSessionChange } from '@/utils/auth'
import { errorMessage } from '@/utils/errors'

type OverviewState = 'ready' | 'error'
interface CourseEntry {
  courseId: number
  name: string
  description: string | null
  overview: CourseOverviewVO | null
  overviewState: OverviewState
}

const router = useRouter()
const courses = ref<CourseEntry[]>([])
const loading = ref(false)
const loadFailed = ref(false)
const starting = ref(false)
const recommendedCourse = ref<CourseVO | null>(null)
const recommendationLoading = ref(false)
const recommendationError = ref('')
const joiningRecommended = ref(false)
const joinError = ref('')
let alive = true
let generation = 0

const keyword = ref('')
const filter = ref('all')
const page = ref(1)
const pageSize = 12
const filtered = computed(() =>
  courses.value.filter((course) => {
    if (!`${course.name} ${course.description || ''}`.toLowerCase().includes(keyword.value.trim().toLowerCase()))
      return false
    if (filter.value === 'started') return !!course.overview?.lastLearningTime
    if (filter.value === 'not-started') return !!course.overview && !course.overview.lastLearningTime
    if (filter.value === 'due') return !!course.overview?.dueReviewCount
    return true
  }),
)
const pages = computed(() => Math.ceil(filtered.value.length / pageSize))
const visibleCourses = computed(() => filtered.value.slice((page.value - 1) * pageSize, page.value * pageSize))
watch([keyword, filter], () => {
  page.value = 1
})
function clearFilters() {
  keyword.value = ''
  filter.value = 'all'
}

const continueCourse = computed(
  () =>
    [...courses.value.filter((course) => course.overview?.lastLearningTime)].sort(
      (a, b) => new Date(b.overview!.lastLearningTime!).getTime() - new Date(a.overview!.lastLearningTime!).getTime(),
    )[0],
)
const continueTarget = computed<LearningTargetVO | null>(
  () => continueCourse.value?.overview?.recommendedTargets[0] || null,
)
const emptyDescription = computed(() => {
  if (recommendedCourse.value) return `推荐从 ${recommendedCourse.value.name} 开始，按课程目录学习第一节。`
  if (recommendationLoading.value) return '正在查找适合开始的课程…'
  return '可以从课程库选择一门课程开始学习。'
})
const pageDescription = computed(() =>
  !loading.value && !loadFailed.value && !courses.value.length
    ? '加入一门课程，开始第一节学习。'
    : '查看已加入课程，并从当前学习处继续。',
)

function isCurrent(requestGeneration: number, session: number) {
  return alive && requestGeneration === generation && session === getAuthSessionVersion()
}
function toEntry(item: UserCourseVO, overview: CourseOverviewVO | null, overviewState: OverviewState): CourseEntry {
  return { courseId: item.courseId, name: item.courseName, description: item.description, overview, overviewState }
}

async function fetchCourses() {
  const requestGeneration = ++generation
  const session = getAuthSessionVersion()
  loading.value = true
  loadFailed.value = false
  try {
    const response = await getMyCourses({ errorDisplay: 'inline' })
    if (!isCurrent(requestGeneration, session)) return
    const list = response.data || []
    recommendedCourse.value = null
    recommendationError.value = ''
    joinError.value = ''
    if (!list.length) void loadRecommendedCourse()
    const overviews = await Promise.allSettled(
      list.map((item) => getCourseOverview(item.courseId, { errorDisplay: 'inline' }).then((result) => result.data)),
    )
    if (!isCurrent(requestGeneration, session)) return
    courses.value = list.map((item, index) => {
      const overview = overviews[index]
      return overview.status === 'fulfilled' ? toEntry(item, overview.value, 'ready') : toEntry(item, null, 'error')
    })
  } catch {
    if (isCurrent(requestGeneration, session)) loadFailed.value = true
  } finally {
    if (isCurrent(requestGeneration, session)) loading.value = false
  }
}

async function loadRecommendedCourse() {
  const requestGeneration = generation
  const session = getAuthSessionVersion()
  recommendationLoading.value = true
  recommendationError.value = ''
  try {
    const result = await getAllCourses({ errorDisplay: 'inline' })
    if (!isCurrent(requestGeneration, session)) return
    recommendedCourse.value = result.data.find((course) => course.contentKey === 'cs408-data-structures') || null
  } catch {
    if (isCurrent(requestGeneration, session)) recommendationError.value = '推荐课程暂时无法读取。'
  } finally {
    if (isCurrent(requestGeneration, session)) recommendationLoading.value = false
  }
}

async function startRecommended() {
  const course = recommendedCourse.value
  if (!course || joiningRecommended.value) return
  const requestGeneration = generation
  const session = getAuthSessionVersion()
  joiningRecommended.value = true
  joinError.value = ''
  try {
    await addCourseToLibrary(course.id, { errorDisplay: 'inline' })
    if (!isCurrent(requestGeneration, session)) return
    const result = await getCourseOverview(course.id, { errorDisplay: 'inline' })
    if (!isCurrent(requestGeneration, session)) return
    const target = result.data.recommendedTargets[0]
    if (target) openLearningTarget(router, course.id, target)
    else openCourse(course.id)
  } catch (cause) {
    if (isCurrent(requestGeneration, session)) joinError.value = errorMessage(cause, '暂时无法进入课程，请重试。')
  } finally {
    if (isCurrent(requestGeneration, session)) joiningRecommended.value = false
  }
}

async function retryOverview(courseId: number) {
  const requestGeneration = generation
  const session = getAuthSessionVersion()
  const index = courses.value.findIndex((course) => course.courseId === courseId)
  if (index < 0) return
  try {
    const result = await getCourseOverview(courseId, { errorDisplay: 'inline' })
    if (!isCurrent(requestGeneration, session)) return
    courses.value[index] = { ...courses.value[index], overview: result.data, overviewState: 'ready' }
  } catch {
    // 原位错误已保留，用户可再次重试。
  }
}

function openCourse(courseId: number) {
  router.push({ name: 'CourseOverview', params: { id: courseId } })
}
async function startContinue() {
  const entry = continueCourse.value
  if (!entry || starting.value) return
  starting.value = true
  try {
    const target = entry.overview?.recommendedTargets[0]
    if (target) openLearningTarget(router, entry.courseId, target)
    else openCourse(entry.courseId)
  } finally {
    starting.value = false
  }
}

const unsubscribeSession = onAuthSessionChange(() => {
  generation++
  page.value = 1
  courses.value = []
  loading.value = false
  loadFailed.value = false
  recommendedCourse.value = null
  recommendationLoading.value = false
  recommendationError.value = ''
  joiningRecommended.value = false
  joinError.value = ''
  if (isAuthenticated()) void fetchCourses()
})
onMounted(() => void fetchCourses())
onUnmounted(() => {
  alive = false
  generation++
  unsubscribeSession()
})
</script>
<style scoped>
.my-heading,
.continue-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lt-space-6);
}
.continue-section h2 {
  margin: var(--lt-space-2) 0;
}
.continue-section p {
  margin: var(--lt-space-2) 0 0;
}
.my-list-section {
  display: grid;
  gap: var(--lt-space-4);
}
.my-heading h2,
.course-copy h3,
.course-copy p {
  margin: 0;
}
.course-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: start;
  gap: var(--lt-space-4);
  padding: var(--lt-space-6);
}
.course-card + .course-card {
  border-top: calc(var(--lt-space-1) / 4) solid var(--lt-separator);
}
.course-copy p {
  margin-top: var(--lt-space-2);
}
.course-meta {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.course-facts {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lt-space-5);
  margin: var(--lt-space-3) 0 0;
}
.course-facts div {
  display: flex;
  align-items: baseline;
  gap: var(--lt-space-2);
}
.course-facts dt {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.course-facts dd {
  margin: 0;
  font-family: var(--lt-font-rounded);
  font-variant-numeric: tabular-nums;
}
</style>
