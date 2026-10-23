<template>
  <div class="lt course-page" data-appearance="dark">
    <header class="course-head">
      <LtIconButton label="返回我的课程" @click="router.push('/my-courses')"
        ><ChevronLeft aria-hidden="true"
      /></LtIconButton>
      <h1>课程库</h1>
      <p>选择一门课程，查看目录后加入自己的学习空间。</p>
    </header>
    <form class="course-toolbar" role="search" @submit.prevent="search">
      <label class="course-search"
        ><Search aria-hidden="true" /><input
          v-model="keyword"
          type="search"
          placeholder="搜索课程"
          aria-label="搜索课程"
      /></label>
      <LtButton type="submit">搜索</LtButton>
      <label class="course-select-label"
        >每页显示<select v-model="pageSize" @change="changePageSize">
          <option :value="12">12 门</option>
          <option :value="24">24 门</option>
          <option :value="48">48 门</option>
        </select></label
      >
    </form>
    <CoursePageState v-if="loading" state="loading" title="正在读取课程库" />
    <CoursePageState
      v-else-if="loadFailed"
      state="error"
      title="暂时无法读取课程库"
      description="课程列表尚未加载，请重试。"
      @retry="fetchCourses"
    />
    <CoursePageState
      v-else-if="!courses.length"
      state="empty"
      title="没有匹配的课程"
      description="换一个关键词，或清空搜索后查看全部课程。"
    >
      <LtButton @click="clearSearch">清空搜索</LtButton>
    </CoursePageState>
    <template v-else>
      <p class="course-muted" role="status">共 {{ total }} 门课程</p>
      <section class="catalog-grid" aria-label="课程列表">
        <LtCard v-for="course in courses" :key="course.id" as="article" radius="xl" class="catalog-card course-card">
          <BookOpen class="course-symbol" aria-hidden="true" />
          <h2>{{ course.name }}</h2>
          <p class="course-muted">{{ course.description || '暂无课程描述' }}</p>
          <LtButton @click="goToDetail(course.id)"
            >查看课程<template #trailing><ArrowRight aria-hidden="true" /></template
          ></LtButton>
        </LtCard>
      </section>
      <nav v-if="pages > 1" class="course-pager" aria-label="课程翻页">
        <LtButton :disabled="page <= 1" @click="page--">上一页</LtButton><span>第 {{ page }} / {{ pages }} 页</span
        ><LtButton :disabled="page >= pages" @click="page++">下一页</LtButton>
      </nav>
    </template>
  </div>
</template>
<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, BookOpen, ChevronLeft, Search } from '@lucide/vue'
import { LtButton, LtCard, LtIconButton } from '@/components/living'
import CoursePageState from './CoursePageState.vue'
import './course-baseline.css'
import { getCoursePage, type CourseVO } from '@/api/course'
import { getAuthSessionVersion, isAuthenticated, onAuthSessionChange } from '@/utils/auth'

const router = useRouter()
const courses = ref<CourseVO[]>([])
const loading = ref(false)
const loadFailed = ref(false)
const keyword = ref('')
let alive = true
let generation = 0

const query = ref('')
const page = ref(1)
const pageSize = ref(12)
const pages = ref(0)
const total = ref(0)
function clearSearch() {
  keyword.value = ''
  search()
}
function search() {
  query.value = keyword.value.trim()
  if (page.value !== 1) page.value = 1
  else void fetchCourses()
}
watch([page, pageSize], () => void fetchCourses())
function changePageSize() {
  if (page.value !== 1) page.value = 1
}

function isCurrent(requestGeneration: number, session: number) {
  return alive && requestGeneration === generation && session === getAuthSessionVersion()
}

async function fetchCourses() {
  const requestGeneration = ++generation
  const session = getAuthSessionVersion()
  loading.value = true
  loadFailed.value = false
  try {
    const response = await getCoursePage(
      { pageNum: page.value, pageSize: pageSize.value, keyword: query.value || undefined },
      { errorDisplay: 'inline' },
    )
    if (isCurrent(requestGeneration, session)) {
      courses.value = response.data.records
      pages.value = response.data.pages
      total.value = response.data.total
    }
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
.catalog-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, calc(var(--lt-space-10) * 4)), 1fr));
  gap: var(--lt-space-5);
}
.catalog-card {
  display: flex;
  flex-direction: column;
  align-items: start;
  gap: var(--lt-space-4);
}
.catalog-card h2 {
  margin: 0;
  font-size: var(--lt-text-title3);
}
.catalog-card p {
  flex: 1;
  margin: 0;
}
</style>
