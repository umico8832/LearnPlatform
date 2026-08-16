<template>
  <div class="course-list page-container">
    <section class="course-hero">
      <div class="hero-copy">
        <span class="section-kicker">课程中心</span>
        <h2>选择今天要推进的课程</h2>
        <p>从课程进入知识点、题库和练习，把分散的学习入口收束到同一条清晰路径里。</p>
      </div>
      <div class="hero-actions">
        <el-button :icon="Collection" @click="router.push({ name: 'QuestionList' })">浏览题库</el-button>
        <el-button type="primary" :icon="DataLine" @click="router.push({ name: 'Practice' })">开始练习</el-button>
      </div>
    </section>

    <section class="course-summary">
      <div class="summary-card">
        <span>可学习课程</span>
        <strong>{{ courses.length }}</strong>
      </div>
      <div class="summary-card">
        <span>有课程描述</span>
        <strong>{{ describedCount }}</strong>
      </div>
      <div class="summary-card">
        <span>快速入口</span>
        <strong>3</strong>
      </div>
    </section>

    <section class="course-workbench">
      <div class="workbench-header">
        <div>
          <h3>课程列表</h3>
          <p>{{ listSummary }}</p>
        </div>
        <el-input v-model="keyword" class="course-search" :prefix-icon="Search" placeholder="搜索课程" clearable />
      </div>

      <div v-loading="loading" class="course-grid" element-loading-text="加载课程中...">
        <article v-for="course in filteredCourses" :key="course.id" class="course-card">
          <button type="button" class="course-card-main" @click="goToDetail(course.id)">
            <span class="course-icon">
              <el-icon><Reading /></el-icon>
            </span>
            <span class="course-name">{{ course.name }}</span>
            <span class="course-desc">{{ course.description || '暂无课程描述，进入后可查看知识点结构。' }}</span>
          </button>
          <div class="course-card-actions">
            <el-button link type="primary" :icon="Collection" @click="goToQuestions(course.id)">查看题目</el-button>
            <el-button link type="primary" :icon="ArrowRight" @click="goToDetail(course.id)">课程详情</el-button>
          </div>
        </article>

        <div v-if="filteredCourses.length === 0 && !loading" class="empty-state">
          <el-empty :description="keyword ? '没有匹配的课程' : '暂无课程'">
            <el-button v-if="keyword" type="primary" @click="keyword = ''">清空搜索</el-button>
          </el-empty>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Collection, DataLine, Reading, Search } from '@element-plus/icons-vue'
import { getAllCourses, type CourseVO } from '@/api/course'

const router = useRouter()
const courses = ref<CourseVO[]>([])
const loading = ref(false)
const keyword = ref('')

const describedCount = computed(() => courses.value.filter((course) => course.description?.trim()).length)

const filteredCourses = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  const sortedCourses = [...courses.value].sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
  if (!query) return sortedCourses
  return sortedCourses.filter((course) => {
    return course.name.toLowerCase().includes(query) || (course.description || '').toLowerCase().includes(query)
  })
})

const listSummary = computed(() => {
  if (loading.value) return '正在加载课程...'
  if (keyword.value.trim()) return `找到 ${filteredCourses.value.length} 门匹配课程。`
  return `共 ${courses.value.length} 门课程，可进入详情查看知识点结构。`
})

async function fetchCourses() {
  loading.value = true
  try {
    const res = await getAllCourses()
    courses.value = res.data || []
  } catch {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}

function goToDetail(id: number) {
  router.push({ name: 'CourseDetail', params: { id } })
}

function goToQuestions(id: number) {
  router.push({ name: 'QuestionList', query: { courseId: String(id) } })
}

onMounted(() => {
  fetchCourses()
})
</script>

<style scoped>
.course-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.course-hero,
.course-workbench {
  background: var(--lp-surface);
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  box-shadow: var(--lp-shadow-sm);
}

.course-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  padding: 22px;
}

.hero-copy {
  max-width: 720px;
}

.section-kicker {
  color: var(--lp-primary);
  font-size: 12px;
  font-weight: 800;
}

.course-hero h2 {
  margin: 4px 0 8px;
  color: var(--lp-text);
  font-size: 24px;
  line-height: 1.25;
}

.course-hero p,
.workbench-header p {
  margin: 0;
  color: var(--lp-text-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.hero-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.course-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.summary-card {
  min-height: 92px;
  padding: 16px;
  background: var(--lp-surface);
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  box-shadow: var(--lp-shadow-sm);
}

.summary-card span {
  display: block;
  color: var(--lp-text-muted);
  font-size: 13px;
}

.summary-card strong {
  display: block;
  margin-top: 8px;
  color: var(--lp-primary);
  font-size: 30px;
  line-height: 1;
}

.course-workbench {
  padding: 18px;
}

.workbench-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;
}

.workbench-header h3 {
  margin: 0 0 5px;
  color: var(--lp-text);
  font-size: 18px;
}

.course-search {
  width: 260px;
  flex: 0 0 auto;
}

.course-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  min-height: 220px;
}

.course-card {
  display: flex;
  flex-direction: column;
  min-height: 220px;
  background: linear-gradient(180deg, #ffffff 0%, #fbfdff 100%);
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  transition:
    border-color 0.2s,
    box-shadow 0.2s,
    transform 0.2s;
}

.course-card:hover {
  border-color: var(--lp-border-strong);
  box-shadow: var(--lp-shadow-md);
  transform: translateY(-2px);
}

.course-card-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: flex-start;
  width: 100%;
  padding: 18px;
  text-align: left;
  background: transparent;
  border: 0;
  cursor: pointer;
}

.course-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  color: var(--lp-primary);
  background: var(--lp-primary-soft);
  border-radius: 8px;
  font-size: 23px;
}

.course-name {
  margin-top: 15px;
  color: var(--lp-text);
  font-size: 18px;
  font-weight: 800;
  line-height: 1.35;
}

.course-desc {
  display: -webkit-box;
  margin-top: 9px;
  overflow: hidden;
  color: var(--lp-text-secondary);
  font-size: 14px;
  line-height: 1.65;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.course-card-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--lp-border);
}

.empty-state {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 220px;
}

@media (max-width: 1100px) {
  .course-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 767px) {
  .course-hero,
  .workbench-header {
    align-items: stretch;
    flex-direction: column;
  }

  .hero-actions,
  .hero-actions .el-button,
  .course-search {
    width: 100%;
  }

  .course-summary,
  .course-grid {
    grid-template-columns: 1fr;
  }

  .course-workbench {
    padding: 14px;
  }
}
</style>
