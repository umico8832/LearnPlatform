<template>
  <div class="course-list page-container">
    <LpPageHeader title="课程库" description="选择一门课程，查看目录后加入自己的学习空间。">
      <template #actions>
        <el-input v-model="keyword" class="course-search" :prefix-icon="Search" placeholder="搜索课程" clearable />
      </template>
    </LpPageHeader>

    <LpStatePanel v-if="loading" state="loading" loading-label="正在读取课程库" />
    <LpStatePanel
      v-else-if="loadFailed"
      state="error"
      title="暂时无法读取课程库"
      description="课程列表尚未加载，请重试。"
      @retry="fetchCourses"
    />

    <template v-else>
      <section v-if="filtered408.length" class="category-section" aria-labelledby="cs408-heading">
        <LpSectionHeading
          heading-id="cs408-heading"
          title="408 计算机学科专业基础"
          :description="`${filtered408.length} 门与 408 范围相关的公开课程`"
        />
        <div :class="['category-grid', { 'category-grid--featured': filtered408.length === 1 }]">
          <article v-for="course in filtered408" :key="course.id" class="course-card">
            <span class="course-icon" aria-hidden="true"
              ><el-icon :size="20"><Reading /></el-icon
            ></span>
            <div class="course-copy">
              <h2 class="course-name">{{ course.name }}</h2>
              <p class="course-desc">{{ course.description || '暂无课程描述' }}</p>
            </div>
            <el-button type="primary" :icon="ArrowRight" @click="goToDetail(course.id)">查看课程</el-button>
          </article>
        </div>
      </section>

      <section v-if="filteredOthers.length" class="category-section" aria-labelledby="other-courses-heading">
        <LpSectionHeading
          heading-id="other-courses-heading"
          title="更多课程"
          :description="`${filteredOthers.length} 门公开课程`"
        />
        <div class="category-grid">
          <article v-for="course in filteredOthers" :key="course.id" class="course-card">
            <span class="course-icon" aria-hidden="true"
              ><el-icon :size="20"><Reading /></el-icon
            ></span>
            <div class="course-copy">
              <h2 class="course-name">{{ course.name }}</h2>
              <p class="course-desc">{{ course.description || '暂无课程描述' }}</p>
            </div>
            <el-button type="primary" :icon="ArrowRight" @click="goToDetail(course.id)">查看课程</el-button>
          </article>
        </div>
      </section>

      <LpStatePanel
        v-if="!filtered408.length && !filteredOthers.length"
        state="empty"
        title="没有匹配的课程"
        description="换一个关键词，或清空搜索后查看全部课程。"
      >
        <template #actions><el-button type="primary" @click="keyword = ''">清空搜索</el-button></template>
      </LpStatePanel>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Reading, Search } from '@element-plus/icons-vue'
import { getAllCourses, type CourseVO } from '@/api/course'
import { getAuthSessionVersion, isAuthenticated, onAuthSessionChange } from '@/utils/auth'

const router = useRouter()
const courses = ref<CourseVO[]>([])
const loading = ref(false)
const loadFailed = ref(false)
const keyword = ref('')
let alive = true
let generation = 0

const is408Course = (course: CourseVO) => course.name.includes('408') || (course.contentKey || '').startsWith('cs408-')
const filterByKeyword = (list: CourseVO[]) => {
  const query = keyword.value.trim().toLowerCase()
  if (!query) return list
  return list.filter((course) => `${course.name} ${course.description || ''}`.toLowerCase().includes(query))
}
const sortedCourses = (predicate: (course: CourseVO) => boolean) =>
  computed(() =>
    filterByKeyword([...courses.value.filter(predicate)].sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))),
  )
const filtered408 = sortedCourses(is408Course)
const filteredOthers = sortedCourses((course) => !is408Course(course))

function isCurrent(requestGeneration: number, session: number) {
  return alive && requestGeneration === generation && session === getAuthSessionVersion()
}

async function fetchCourses() {
  const requestGeneration = ++generation
  const session = getAuthSessionVersion()
  loading.value = true
  loadFailed.value = false
  try {
    const response = await getAllCourses({ errorDisplay: 'inline' })
    if (isCurrent(requestGeneration, session)) courses.value = response.data || []
  } catch {
    if (isCurrent(requestGeneration, session)) loadFailed.value = true
  } finally {
    if (isCurrent(requestGeneration, session)) loading.value = false
  }
}

function goToDetail(id: number) {
  router.push({ name: 'CourseDetail', params: { id }, query: { from: 'course-list' } })
}

const unsubscribeSession = onAuthSessionChange(() => {
  generation++
  courses.value = []
  loadFailed.value = false
  loading.value = false
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
.course-list {
  display: grid;
  gap: var(--lp-space-8);
}
.course-search {
  width: 280px;
}
.category-section {
  display: grid;
  gap: var(--lp-space-4);
}
.category-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--lp-space-3);
}
.category-grid--featured {
  grid-template-columns: minmax(0, 1fr);
}
.category-grid--featured .course-card {
  grid-template-columns: auto minmax(0, 1fr) auto;
  grid-template-rows: auto;
  align-items: center;
}
.category-grid--featured .course-card > .el-button {
  grid-column: 3;
  grid-row: 1;
  align-self: center;
  justify-self: end;
}
.course-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  grid-template-rows: minmax(0, 1fr) auto;
  gap: var(--lp-space-3) var(--lp-space-4);
  align-items: start;
  padding: var(--lp-space-5);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  background: var(--lp-surface);
  box-shadow: var(--lp-shadow-xs);
}
.course-card > .el-button {
  grid-column: 2;
  align-self: end;
  justify-self: start;
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
  line-height: var(--lp-leading-snug);
}
.course-desc {
  margin: var(--lp-space-2) 0 0;
  color: var(--lp-text-secondary);
  line-height: var(--lp-leading-body);
}
@media (max-width: 767px) {
  .course-search {
    width: 100%;
  }
  .category-grid--featured .course-card {
    grid-template-columns: auto minmax(0, 1fr);
    align-items: start;
  }
  .category-grid--featured .course-card > .el-button {
    grid-column: 2;
    grid-row: 2;
    align-self: end;
    justify-self: start;
  }
}
</style>
