<template>
  <div class="course-list page-container">
    <LpPageHeader title="课程库">
      <template #actions>
        <el-input v-model="keyword" class="course-search" :prefix-icon="Search" placeholder="搜索课程" clearable />
      </template>
    </LpPageHeader>

    <template v-if="loading">
      <LpSkeleton card :rows="5" />
      <LpSkeleton card :rows="3" />
    </template>

    <template v-else-if="loadFailed">
      <section class="state-panel">
        <LpEmptyState title="暂时无法读取课程库" description="请刷新重试。">
          <template #actions>
            <el-button type="primary" @click="fetchCourses">重新加载</el-button>
          </template>
        </LpEmptyState>
      </section>
    </template>

    <template v-else>
      <section v-if="filtered408.length > 0" class="category-section">
        <LpSectionHeading title="408 计算机学科专业基础" />
        <div class="category-grid">
          <article v-for="course in filtered408" :key="course.id" class="course-card">
            <div class="course-card-top">
              <span class="course-icon" aria-hidden="true">
                <el-icon :size="21"><Reading /></el-icon>
              </span>
              <el-tag size="small" type="success" effect="plain">可学习</el-tag>
            </div>
            <h3 class="course-name">{{ course.name }}</h3>
            <p class="course-desc">{{ course.description }}</p>
            <div class="course-card-footer">
              <el-button type="primary" plain :icon="ArrowRight" @click="goToDetail(course.id)"> 查看课程 </el-button>
            </div>
          </article>
        </div>
      </section>

      <section v-if="filteredOthers.length > 0" class="category-section">
        <LpSectionHeading title="更多课程" />
        <div class="category-grid">
          <article v-for="course in filteredOthers" :key="course.id ?? course.name" class="course-card">
            <div class="course-card-top">
              <span class="course-icon" aria-hidden="true">
                <el-icon :size="21"><Reading /></el-icon>
              </span>
              <el-tag size="small" type="success" effect="plain">可学习</el-tag>
            </div>
            <h3 class="course-name">{{ course.name }}</h3>
            <p class="course-desc">{{ course.description }}</p>
            <div class="course-card-footer">
              <el-button type="primary" plain :icon="ArrowRight" @click="goToDetail(course.id)"> 查看课程 </el-button>
            </div>
          </article>
        </div>
      </section>

      <section v-if="filtered408.length === 0 && filteredOthers.length === 0 && !loading" class="state-panel">
        <LpEmptyState title="没有匹配的课程" description="换一个关键词再试试。">
          <template #actions>
            <el-button type="primary" @click="keyword = ''">清空搜索</el-button>
          </template>
        </LpEmptyState>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Reading, Search } from '@element-plus/icons-vue'
import { getAllCourses, type CourseVO } from '@/api/course'

interface LibraryCourse {
  id: number
  name: string
  description: string
}

const router = useRouter()
const courses = ref<CourseVO[]>([])
const loading = ref(false)
const loadFailed = ref(false)
const keyword = ref('')

const is408Course = (course: CourseVO) => course.name.includes('408') || (course.contentKey || '').startsWith('cs408-')

const real408 = computed<LibraryCourse[]>(() =>
  courses.value
    .filter(is408Course)
    .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
    .map((course) => ({
      id: course.id,
      name: course.name,
      description: course.description,
    })),
)

const realOthers = computed<LibraryCourse[]>(() =>
  courses.value
    .filter((course) => !is408Course(course))
    .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
    .map((course) => ({
      id: course.id,
      name: course.name,
      description: course.description,
    })),
)
const filtered408 = computed(() => filterByKeyword(real408.value))
const filteredOthers = computed(() => filterByKeyword(realOthers.value))

function filterByKeyword(list: LibraryCourse[]) {
  const query = keyword.value.trim().toLowerCase()
  if (!query) return list
  return list.filter(
    (course) => course.name.toLowerCase().includes(query) || course.description.toLowerCase().includes(query),
  )
}

async function fetchCourses() {
  loading.value = true
  loadFailed.value = false
  try {
    const res = await getAllCourses()
    courses.value = res.data || []
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

function goToDetail(id: number) {
  router.push({ name: 'CourseDetail', params: { id } })
}

onMounted(fetchCourses)
</script>

<style scoped>
.course-list {
  display: flex;
  flex-direction: column;
  gap: var(--lp-space-8);
}

.course-search {
  width: 260px;
}

.category-section {
  display: grid;
  gap: var(--lp-space-4);
}

.category-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(264px, 1fr));
  gap: var(--lp-space-4);
}

.course-card {
  display: flex;
  flex-direction: column;
  gap: var(--lp-space-3);
  min-height: 208px;
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

.course-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lp-space-2);
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

.course-name {
  font-size: var(--lp-text-xl);
  font-weight: var(--lp-weight-bold);
  color: var(--lp-text);
  line-height: var(--lp-leading-snug);
}

.course-desc {
  margin: 0;
  flex: 1;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-base);
  line-height: var(--lp-leading-body);
}

.course-card-footer {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  min-height: 36px;
}

.state-panel {
  padding: var(--lp-space-6) 0;
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}

@media (max-width: 767px) {
  .course-search {
    width: 100%;
  }
}
</style>
