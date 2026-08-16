<template>
  <div class="my-courses page-container">
    <LpPageHeader
      kicker="个人课程库"
      title="我的课程"
      description="这里是你建立长期学习关系的课程。从上次停下的地方继续，不需要研究软件怎么用。"
    >
      <template #actions>
        <el-button :icon="Plus" @click="router.push({ name: 'CourseList' })">浏览课程库</el-button>
      </template>
    </LpPageHeader>

    <template v-if="loading">
      <div class="courses-stack">
        <LpSkeleton card :rows="4" />
        <LpSkeleton card :rows="3" />
      </div>
    </template>

    <template v-else-if="loadFailed">
      <section class="state-panel">
        <LpEmptyState title="暂时无法读取课程库" description="请刷新重试；如果网络恢复正常，课程会重新出现。">
          <template #actions>
            <el-button type="primary" @click="fetchCourses">重新加载</el-button>
          </template>
        </LpEmptyState>
      </section>
    </template>

    <template v-else-if="courses.length === 0">
      <section class="state-panel">
        <LpEmptyState
          title="课程库还是空的"
          description="从课程库加入一门课程（例如 408 数据结构），就可以从这里持续学习。"
        >
          <template #actions>
            <el-button type="primary" :icon="Plus" @click="router.push({ name: 'CourseList' })">浏览课程库</el-button>
          </template>
        </LpEmptyState>
      </section>
    </template>

    <template v-else>
      <section v-if="continueCourse" class="continue-section" aria-labelledby="continue-heading">
        <div class="continue-copy">
          <LpKicker>继续学习</LpKicker>
          <h2 id="continue-heading">{{ continueCourse.name }}</h2>
          <p v-if="continueTarget" class="continue-target">{{ continueTarget.title }} · {{ continueTarget.reason }}</p>
          <p v-else class="continue-target">从课程空间选择下一步。</p>
          <span v-if="continueCourse.overview?.lastLearningTime" class="continue-time">
            上次学习：{{ formatRelativeTime(continueCourse.overview?.lastLearningTime) }}
          </span>
        </div>
        <div class="continue-actions">
          <el-button
            type="primary"
            size="large"
            :icon="ArrowRight"
            :loading="starting"
            @click="startContinue(continueCourse)"
          >
            继续学习
          </el-button>
          <el-button size="large" @click="openCourse(continueCourse.courseId)">进入课程空间</el-button>
        </div>
      </section>

      <section class="course-list-section" aria-labelledby="course-list-heading">
        <LpSectionHeading kicker="全部课程" title="我的课程" :description="`共 ${courses.length} 门已加入课程。`" />
        <div class="course-list">
          <article v-for="course in courses" :key="course.courseId" class="course-card">
            <div class="course-icon" aria-hidden="true">
              <el-icon :size="20"><Reading /></el-icon>
            </div>

            <div class="course-copy">
              <h3 class="course-name">{{ course.name }}</h3>
              <p class="course-desc">{{ course.description || '暂无课程描述，进入后可从目录开始。' }}</p>
              <div class="course-meta">
                <span>加入于 {{ formatDate(course.addedAt) }}</span>
                <span v-if="course.overview?.lastLearningTime"
                  >上次学习 {{ formatRelativeTime(course.overview?.lastLearningTime) }}</span
                >
                <span v-else>尚未开始学习</span>
              </div>
              <div
                v-if="(course.overview?.dueReviewCount ?? 0) > 0 || (course.overview?.unresolvedWrongCount ?? 0) > 0"
                class="course-signals"
              >
                <LpSignal
                  v-if="(course.overview?.dueReviewCount ?? 0) > 0"
                  icon="review"
                  tone="warning"
                  title="有到期的复习"
                  :reason="`${course.overview?.dueReviewCount ?? 0} 条复习计划已到时间`"
                />
                <LpSignal
                  v-if="(course.overview?.unresolvedWrongCount ?? 0) > 0"
                  icon="wrong"
                  tone="danger"
                  title="有待处理错题"
                  :reason="`${course.overview?.unresolvedWrongCount ?? 0} 道错题尚未标记掌握`"
                />
              </div>
            </div>

            <div class="course-actions">
              <el-button type="primary" :icon="ArrowRight" @click="startCourse(course)">
                {{ course.overview?.recommendedTargets[0] ? '继续学习' : '开始学习' }}
              </el-button>
              <el-button plain @click="openCourse(course.courseId)">进入课程空间</el-button>
            </div>
          </article>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Plus, Reading } from '@element-plus/icons-vue'
import { getMyCourses, getCourseOverview, type CourseOverviewVO, type UserCourseVO } from '@/api/course'
import { openLearningTarget } from '@/utils/learningTarget'
import { formatRelativeTime } from '@/utils/format'

interface CourseEntry {
  courseId: number
  name: string
  description: string | null
  addedAt: string
  overview: CourseOverviewVO | null
}

const router = useRouter()
const courses = ref<CourseEntry[]>([])
const loading = ref(false)
const loadFailed = ref(false)
const starting = ref(false)

/** 最近有学习记录的课程用于「继续学习」主入口。 */
const continueCourse = computed(() => {
  const withLearning = courses.value
    .filter((course) => course.overview?.lastLearningTime)
    .sort(
      (a, b) => new Date(b.overview!.lastLearningTime!).getTime() - new Date(a.overview!.lastLearningTime!).getTime(),
    )
  return withLearning[0] || null
})

const continueTarget = computed(() => continueCourse.value?.overview?.recommendedTargets[0] || null)

function formatDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString('zh-CN')
}

async function fetchCourses() {
  loading.value = true
  loadFailed.value = false
  try {
    const response = await getMyCourses()
    const list = response.data || []
    const overviews = await Promise.allSettled(
      list.map((item) => getCourseOverview(item.courseId).then((res) => res.data)),
    )
    courses.value = list.map((item: UserCourseVO, index: number) => {
      const settled = overviews[index]
      return {
        courseId: item.courseId,
        name: item.courseName,
        description: item.description,
        addedAt: item.addedAt,
        overview: settled.status === 'fulfilled' ? settled.value : null,
      }
    })
    courses.value.sort((a, b) => {
      const timeA = a.overview?.lastLearningTime ? new Date(a.overview.lastLearningTime).getTime() : 0
      const timeB = b.overview?.lastLearningTime ? new Date(b.overview.lastLearningTime).getTime() : 0
      return timeB - timeA
    })
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

function openCourse(courseId: number) {
  router.push({ name: 'CourseOverview', params: { id: courseId } })
}

async function startContinue(entry: CourseEntry) {
  if (!entry.overview) {
    openCourse(entry.courseId)
    return
  }
  const target = entry.overview.recommendedTargets[0]
  if (target) {
    openLearningTarget(router, entry.courseId, target)
  } else {
    router.push({ name: 'CourseOverview', params: { id: entry.courseId } })
  }
}

async function startCourse(entry: CourseEntry) {
  starting.value = true
  try {
    if (entry.overview?.recommendedTargets[0]) {
      openLearningTarget(router, entry.courseId, entry.overview.recommendedTargets[0])
    } else {
      router.push({ name: 'CourseOverview', params: { id: entry.courseId } })
    }
  } finally {
    starting.value = false
  }
}

onMounted(fetchCourses)
</script>

<style scoped>
.my-courses {
  display: flex;
  flex-direction: column;
  gap: var(--lp-space-6);
}

.courses-stack {
  display: grid;
  gap: var(--lp-space-4);
}

.state-panel {
  padding: var(--lp-space-6) 0;
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}

/* ---------------- Continue ---------------- */
.continue-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lp-space-6);
  padding: var(--lp-space-6);
  background: linear-gradient(120deg, var(--lp-primary-soft), var(--lp-surface) 62%);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
}

.continue-copy {
  min-width: 0;
}

.continue-copy h2 {
  margin-top: var(--lp-space-2);
  font-family: var(--lp-font-display);
  font-size: var(--lp-text-3xl);
  font-weight: var(--lp-weight-bold);
  line-height: var(--lp-leading-display);
  color: var(--lp-text);
}

.continue-target {
  margin: var(--lp-space-2) 0 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-md);
  line-height: var(--lp-leading-body);
}

.continue-time {
  display: block;
  margin-top: var(--lp-space-2);
  color: var(--lp-text-muted);
  font-size: var(--lp-text-sm);
}

.continue-actions {
  display: flex;
  gap: var(--lp-space-3);
  flex-shrink: 0;
  flex-wrap: wrap;
  justify-content: flex-end;
}

/* ---------------- Course list ---------------- */
.course-list-section {
  display: grid;
  gap: var(--lp-space-4);
}

.course-list {
  display: grid;
  gap: var(--lp-space-3);
}

.course-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: start;
  gap: var(--lp-space-4);
  padding: var(--lp-space-5);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
  transition:
    border-color var(--lp-duration-fast) var(--lp-ease-out),
    box-shadow var(--lp-duration-fast) var(--lp-ease-out);
}

.course-card:hover {
  border-color: var(--lp-border-strong);
  box-shadow: var(--lp-shadow-sm);
}

.course-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: var(--lp-radius-md);
  background: var(--lp-primary-soft);
  color: var(--lp-primary);
}

.course-copy {
  min-width: 0;
}

.course-name {
  font-size: var(--lp-text-xl);
  font-weight: var(--lp-weight-bold);
  color: var(--lp-text);
}

.course-desc {
  margin: var(--lp-space-1) 0 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-base);
  line-height: var(--lp-leading-body);
}

.course-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lp-space-2) var(--lp-space-4);
  margin-top: var(--lp-space-3);
  color: var(--lp-text-muted);
  font-size: var(--lp-text-sm);
}

.course-signals {
  display: grid;
  gap: var(--lp-space-2);
  margin-top: var(--lp-space-3);
  max-width: 560px;
}

.course-actions {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: var(--lp-space-2);
  flex-shrink: 0;
}

@media (max-width: 900px) {
  .continue-section {
    align-items: stretch;
    flex-direction: column;
  }
  .continue-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 767px) {
  .course-card {
    grid-template-columns: auto minmax(0, 1fr);
  }
  .course-actions {
    grid-column: 2;
    flex-direction: row;
    flex-wrap: wrap;
  }
}
</style>
