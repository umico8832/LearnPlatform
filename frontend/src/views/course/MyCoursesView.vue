<template>
  <div class="my-courses page-container">
    <section class="library-hero">
      <div>
        <span class="section-kicker">个人课程库</span>
        <h2>已加入的课程</h2>
        <p>从已加入课程进入学习总览，查看真实作答、复习与错题记录，并选择下一步。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="router.push({ name: 'CourseList' })">
        浏览课程
      </el-button>
    </section>

    <section v-loading="loading" class="library-panel" element-loading-text="加载课程库中...">
      <div class="panel-header">
        <div>
          <h3>我的课程</h3>
          <p>{{ listSummary }}</p>
        </div>
        <el-button :icon="Refresh" text :loading="loading" @click="fetchMyCourses">刷新</el-button>
      </div>

      <div v-if="courses.length" class="course-grid">
        <article v-for="course in courses" :key="course.id" class="course-card">
          <div class="course-card-icon">
            <el-icon><Reading /></el-icon>
          </div>
          <div class="course-card-copy">
            <h4>{{ course.courseName }}</h4>
            <p>{{ course.description || '暂无课程描述，可先查看课程目录和题目。' }}</p>
            <span>加入于 {{ formatDate(course.addedAt) }}</span>
          </div>
          <el-button type="primary" plain :icon="ArrowRight" @click="openCourse(course.courseId)">
            进入学习总览
          </el-button>
        </article>
      </div>

      <el-empty v-else-if="!loading" description="课程库还是空的">
        <template #default>
          <p class="empty-description">从课程中心加入一门课程后，就可以在这里统一查看。</p>
          <el-button type="primary" :icon="Plus" @click="router.push({ name: 'CourseList' })">浏览课程</el-button>
        </template>
      </el-empty>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Plus, Reading, Refresh } from '@element-plus/icons-vue'
import { getMyCourses, type UserCourseVO } from '@/api/course'

const router = useRouter()
const courses = ref<UserCourseVO[]>([])
const loading = ref(false)

const listSummary = computed(() => {
  if (loading.value) return '正在读取已加入的课程...'
  return courses.value.length ? `共 ${courses.value.length} 门已加入课程。` : '还没有加入课程。'
})

function formatDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString('zh-CN')
}

async function fetchMyCourses() {
  loading.value = true
  try {
    const response = await getMyCourses()
    courses.value = response.data || []
  } catch {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}

function openCourse(courseId: number) {
  router.push({ name: 'CourseOverview', params: { id: courseId } })
}

onMounted(fetchMyCourses)
</script>

<style scoped>
.my-courses {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.library-hero,
.library-panel {
  background: var(--lp-surface);
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  box-shadow: var(--lp-shadow-sm);
}

.library-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  padding: 22px;
}

.section-kicker {
  color: var(--lp-primary);
  font-size: 12px;
  font-weight: 800;
}

.library-hero h2,
.panel-header h3,
.course-card-copy h4 {
  margin: 0;
  color: var(--lp-text);
}

.library-hero h2 {
  margin-top: 4px;
  font-size: 24px;
  line-height: 1.25;
}

.library-hero p,
.panel-header p,
.course-card-copy p,
.course-card-copy span,
.empty-description {
  color: var(--lp-text-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.library-hero p {
  max-width: 720px;
  margin: 8px 0 0;
}

.library-panel {
  min-height: 280px;
  padding: 18px;
}

.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.panel-header h3 {
  font-size: 18px;
}

.panel-header p {
  margin: 5px 0 0;
}

.course-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.course-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: start;
  gap: 14px;
  padding: 18px;
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  background: var(--lp-surface-soft);
}

.course-card-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 8px;
  background: var(--lp-primary-soft);
  color: var(--lp-primary);
  font-size: 22px;
}

.course-card-copy {
  min-width: 0;
}

.course-card-copy h4 {
  font-size: 16px;
  line-height: 1.4;
}

.course-card-copy p {
  margin: 6px 0;
}

.course-card-copy span {
  color: var(--lp-text-muted);
  font-size: 12px;
}

.course-card .el-button {
  grid-column: 2;
  justify-self: start;
  margin-top: 2px;
}

.empty-description {
  max-width: 320px;
  margin: 4px auto 14px;
}

@media (max-width: 767px) {
  .library-hero {
    align-items: stretch;
    flex-direction: column;
  }

  .library-hero .el-button {
    width: 100%;
  }

  .course-grid {
    grid-template-columns: 1fr;
  }
}
</style>
