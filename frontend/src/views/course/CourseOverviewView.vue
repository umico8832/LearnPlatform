<template>
  <div class="course-overview page-container">
    <section class="overview-hero">
      <div>
        <el-button :icon="ArrowLeft" text @click="router.push({ name: 'MyCourses' })">返回课程库</el-button>
        <span class="section-kicker">课程学习中枢</span>
        <h2>{{ overview?.courseName || '课程总览' }}</h2>
        <p>这里呈现已发生的作答、复习与错题事实；它们不会被浏览时长或自评替代。</p>
      </div>
      <el-button :icon="Collection" @click="openCourseContent">查看课程目录</el-button>
    </section>

    <section v-loading="loading" class="overview-content" element-loading-text="正在汇总学习记录...">
      <template v-if="overview">
        <div class="stats-grid" aria-label="课程学习统计">
          <article class="stat-card">
            <span>已作答</span>
            <strong>{{ overview.answeredCount }}</strong>
            <small>来自课程内真实判分</small>
          </article>
          <article class="stat-card">
            <span>答对</span>
            <strong>{{ overview.correctCount }}</strong>
            <small>不等同于课程掌握度</small>
          </article>
          <article class="stat-card emphasis">
            <span>待复习</span>
            <strong>{{ overview.dueReviewCount }}</strong>
            <small>已到间隔复习时间</small>
          </article>
          <article class="stat-card warning">
            <span>待处理错题</span>
            <strong>{{ overview.unresolvedWrongCount }}</strong>
            <small>尚未标记为已掌握</small>
          </article>
        </div>

        <div class="overview-grid">
          <section class="target-panel" aria-labelledby="next-target-heading">
            <div class="panel-header">
              <div>
                <span class="section-kicker">开始学习</span>
                <h3 id="next-target-heading">选择下一步</h3>
              </div>
              <el-tag type="info" effect="plain">按现有学习事实排序</el-tag>
            </div>
            <div class="target-list">
              <article v-for="(target, index) in overview.recommendedTargets" :key="target.type" class="target-item">
                <div class="target-index" aria-hidden="true">{{ index + 1 }}</div>
                <div class="target-copy">
                  <h4>{{ target.title }}</h4>
                  <p>{{ target.reason }}</p>
                </div>
                <el-button
                  :type="index === 0 ? 'primary' : 'default'"
                  :icon="ArrowRight"
                  @click="openTarget(target)"
                >
                  开始
                </el-button>
              </article>
            </div>
          </section>

          <aside class="activity-panel" aria-labelledby="activity-heading">
            <span class="section-kicker">最近学习</span>
            <h3 id="activity-heading">学习记录</h3>
            <p v-if="overview.lastLearningTime">最近一次课程内判分：{{ formatDateTime(overview.lastLearningTime) }}</p>
            <p v-else>还没有课程内学习记录。可从课程目录或题目开始。</p>
            <el-button text :icon="Refresh" :loading="loading" @click="fetchOverview">刷新记录</el-button>
          </aside>
        </div>
      </template>

      <el-result
        v-else-if="!loading && loadFailed"
        icon="error"
        title="暂时无法读取课程总览"
        sub-title="请刷新重试；如果课程尚未加入课程库，请先从课程中心加入。"
      >
        <template #extra><el-button type="primary" @click="fetchOverview">重新加载</el-button></template>
      </el-result>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, Collection, Refresh } from '@element-plus/icons-vue'
import { getCourseOverview, type CourseOverviewVO, type LearningTargetVO } from '@/api/course'

const route = useRoute()
const router = useRouter()
const overview = ref<CourseOverviewVO | null>(null)
const loading = ref(false)
const loadFailed = ref(false)
const courseId = computed(() => Number(route.params.id))

async function fetchOverview() {
  loading.value = true
  loadFailed.value = false
  try {
    const response = await getCourseOverview(courseId.value)
    overview.value = response.data
  } catch {
    overview.value = null
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

function openCourseContent() {
  router.push({ name: 'CourseDetail', params: { id: courseId.value } })
}

function openTarget(target: LearningTargetVO) {
  if (target.type === 'TUTOR' && target.knowledgePointId) {
    router.push({
      name: 'TutorSession',
      params: { id: courseId.value },
      query: { knowledgePointId: String(target.knowledgePointId) },
    })
    return
  }
  router.push({ name: 'QuestionList', query: { courseId: String(courseId.value), target: target.type } })
}

function formatDateTime(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN')
}

onMounted(fetchOverview)
</script>

<style scoped>
.course-overview { display: flex; flex-direction: column; gap: 16px; }
.overview-hero, .overview-content, .target-panel, .activity-panel, .stat-card { background: var(--lp-surface); border: 1px solid var(--lp-border); border-radius: var(--lp-radius); box-shadow: var(--lp-shadow-sm); }
.overview-hero { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; padding: 22px; }
.overview-hero h2, .panel-header h3, .activity-panel h3, .target-copy h4 { margin: 0; color: var(--lp-text); }
.overview-hero h2 { margin-top: 4px; font-size: 24px; line-height: 1.25; }
.overview-hero p, .target-copy p, .activity-panel p { max-width: 720px; margin: 8px 0 0; color: var(--lp-text-secondary); font-size: 14px; line-height: 1.7; }
.section-kicker { display: block; margin-top: 10px; color: var(--lp-primary); font-size: 12px; font-weight: 800; }
.overview-content { min-height: 320px; padding: 18px; }
.stats-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.stat-card { min-height: 122px; padding: 16px; }
.stat-card span, .stat-card small { display: block; color: var(--lp-text-muted); font-size: 13px; line-height: 1.5; }
.stat-card strong { display: block; margin: 10px 0 5px; color: var(--lp-primary); font-size: 30px; line-height: 1; font-variant-numeric: tabular-nums; }
.stat-card.warning strong { color: var(--el-color-warning); }
.stat-card.emphasis { background: var(--lp-surface-soft); }
.overview-grid { display: grid; grid-template-columns: minmax(0, 1.65fr) minmax(260px, .8fr); gap: 14px; margin-top: 16px; }
.target-panel, .activity-panel { padding: 18px; }
.panel-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.panel-header .section-kicker, .activity-panel .section-kicker { margin-top: 0; }
.panel-header h3, .activity-panel h3 { margin-top: 4px; font-size: 18px; }
.target-list { display: flex; flex-direction: column; gap: 10px; }
.target-item { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 12px; padding: 14px; border: 1px solid var(--lp-border); border-radius: 8px; background: var(--lp-surface-soft); }
.target-index { display: inline-flex; align-items: center; justify-content: center; width: 32px; height: 32px; border-radius: 50%; background: var(--lp-primary-soft); color: var(--lp-primary); font-size: 14px; font-weight: 800; }
.target-copy h4 { font-size: 15px; line-height: 1.45; }
.target-copy p { margin-top: 3px; }
.activity-panel .el-button { margin-top: 10px; }
@media (max-width: 900px) { .stats-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .overview-grid { grid-template-columns: 1fr; } }
@media (max-width: 767px) { .overview-hero { align-items: stretch; flex-direction: column; } .overview-hero > .el-button { width: 100%; } .stats-grid { grid-template-columns: 1fr; } .target-item { grid-template-columns: auto minmax(0, 1fr); } .target-item .el-button { grid-column: 2; justify-self: start; } }
</style>
