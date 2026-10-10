<template>
  <div class="my-courses page-container">
    <LpPageHeader title="我的课程" :description="pageDescription">
      <template v-if="courses.length" #actions
        ><el-button :icon="Plus" @click="router.push({ name: 'CourseList' })">浏览课程库</el-button></template
      >
    </LpPageHeader>

    <LpStatePanel v-if="loading" state="loading" loading-label="正在读取我的课程" />
    <LpStatePanel
      v-else-if="loadFailed"
      state="error"
      title="暂时无法读取我的课程"
      description="课程和学习概况尚未加载，请重试。"
      @retry="fetchCourses"
    />
    <LpStatePanel v-else-if="!courses.length" state="empty" title="还没有加入课程" :description="emptyDescription">
      <template #actions>
        <el-button
          v-if="recommendedCourse"
          type="primary"
          :icon="ArrowRight"
          :loading="joiningRecommended"
          @click="startRecommended"
          >加入并开始学习</el-button
        >
        <el-button v-else-if="recommendationError" @click="loadRecommendedCourse">重新查找推荐课程</el-button>
        <el-button v-if="!recommendedCourse && !recommendationLoading" @click="router.push({ name: 'CourseList' })"
          >浏览课程库</el-button
        >
      </template>
    </LpStatePanel>
    <template v-else>
      <section v-if="continueCourse" class="continue-section" aria-labelledby="continue-heading">
        <div>
          <LpKicker>继续学习</LpKicker>
          <h2 id="continue-heading">{{ continueCourse.name }}</h2>
          <p v-if="continueTarget" class="continue-target">{{ continueTarget.title }} · {{ continueTarget.reason }}</p>
          <p v-else class="continue-target">回到课程空间，选择下一步学习内容。</p>
        </div>
        <el-button type="primary" size="large" :icon="ArrowRight" :loading="starting" @click="startContinue">
          继续学习
        </el-button>
      </section>

      <section class="course-list-section" aria-labelledby="course-list-heading">
        <LpSectionHeading heading-id="course-list-heading" title="全部课程" :description="`共 ${courses.length} 门`" />
        <div class="course-list">
          <article v-for="course in courses" :key="course.courseId" class="course-card">
            <span class="course-icon" aria-hidden="true"
              ><el-icon :size="20"><Reading /></el-icon
            ></span>
            <div class="course-copy">
              <h3 class="course-name">{{ course.name }}</h3>
              <p class="course-desc">{{ course.description || '暂无课程描述' }}</p>
              <p v-if="course.overviewState === 'error'" class="overview-error" role="alert">
                学习概况暂时无法读取。
                <button type="button" @click="retryOverview(course.courseId)">重新读取</button>
              </p>
              <template v-else-if="course.overview">
                <p class="course-meta">
                  <template v-if="course.overview.lastLearningTime"
                    >上次学习 {{ formatRelativeTime(course.overview.lastLearningTime) }}</template
                  >
                  <template v-else>尚未开始学习</template>
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
            <el-button plain @click="openCourse(course.courseId)">进入课程空间</el-button>
          </article>
        </div>
      </section>
    </template>
    <p v-if="!courses.length && recommendationError && !loading && !loadFailed" class="course-empty-error" role="alert">
      {{ recommendationError }}
    </p>
    <p v-if="!courses.length && joinError && !loading && !loadFailed" class="course-empty-error" role="alert">
      {{ joinError }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Plus, Reading } from '@element-plus/icons-vue'
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
.my-courses {
  display: grid;
  gap: var(--lp-space-6);
}
.course-empty-error {
  margin: calc(var(--lp-space-4) * -1) 0 0;
  color: var(--lp-danger);
  font-size: var(--lp-text-sm);
}
.continue-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lp-space-6);
  padding: var(--lp-space-6);
  border: var(--lp-border-hairline);
  border-inline-start: 3px solid var(--lp-primary);
  border-radius: var(--lp-radius-lg);
  background: var(--lp-surface);
  box-shadow: var(--lp-shadow-xs);
}
.continue-section h2 {
  margin: var(--lp-space-2) 0 0;
  color: var(--lp-text);
  font-size: var(--lp-text-2xl);
  line-height: var(--lp-leading-snug);
}
.continue-target,
.course-desc,
.course-meta {
  margin: var(--lp-space-2) 0 0;
  color: var(--lp-text-secondary);
  line-height: var(--lp-leading-body);
}
.course-list-section,
.course-list {
  display: grid;
  gap: var(--lp-space-4);
}
.course-list {
  gap: var(--lp-space-3);
}
.course-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: var(--lp-space-4);
  align-items: start;
  padding: var(--lp-space-5);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  background: var(--lp-surface);
  box-shadow: var(--lp-shadow-xs);
}
.course-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  color: var(--lp-primary);
  background: var(--lp-primary-soft);
  border-radius: var(--lp-radius-md);
}
.course-copy {
  min-width: 0;
}
.course-name {
  margin: 0;
  color: var(--lp-text);
  font-size: var(--lp-text-lg);
}
.course-facts {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lp-space-4);
  margin: var(--lp-space-3) 0 0;
}
.course-facts div {
  display: flex;
  align-items: baseline;
  gap: var(--lp-space-1);
}
.course-facts dt,
.overview-error {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-sm);
}
.course-facts dd {
  margin: 0;
  color: var(--lp-text);
  font-weight: var(--lp-weight-semibold);
  font-variant-numeric: tabular-nums;
}
.overview-error {
  margin: var(--lp-space-3) 0 0;
}
.overview-error button {
  padding: 0;
  border: 0;
  color: var(--lp-primary);
  background: transparent;
  font: inherit;
  text-decoration: underline;
  cursor: pointer;
}
@media (max-width: 900px) {
  .continue-section {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
