<template>
  <div class="practice-container page-container">
    <section class="practice-hero">
      <div>
        <span class="section-kicker">练习复习</span>
        <h2>刷题练习</h2>
        <p>优先用智能推荐保持节奏，也可以按课程、题型和难度自选一组题。</p>
      </div>
      <div class="hero-actions">
        <el-button type="primary" :icon="Promotion" @click="startAdaptivePractice" :loading="adaptiveStartLoading">
          智能推荐
        </el-button>
        <el-button :icon="Filter" @click="scrollToConfig">自选练习</el-button>
      </div>
    </section>

    <section class="stats-grid">
      <el-card
        v-for="item in statCards"
        :key="item.label"
        shadow="never"
        class="stat-card"
        v-loading="statsLoading"
        element-loading-background="rgba(255,255,255,0.8)"
      >
        <span>{{ item.label }}</span>
        <strong :class="item.tone">{{ item.value }}</strong>
      </el-card>
    </section>

    <section class="practice-grid">
      <el-card
        class="adaptive-card"
        shadow="never"
        v-loading="adaptiveLoading"
        element-loading-background="rgba(255,255,255,0.8)"
      >
        <template #header>
          <div class="card-header">
            <span>智能推荐</span>
            <el-tag type="success" size="small" v-if="adaptiveSummary">自适应模式</el-tag>
          </div>
        </template>

        <div v-if="adaptiveSummary" class="adaptive-content">
          <div class="adaptive-overview">
            <div class="overview-item">
              <span class="overview-label">总答题</span>
              <span class="overview-value">{{ adaptiveSummary.totalAnswered }}</span>
            </div>
            <div class="overview-item">
              <span class="overview-label">整体正确率</span>
              <span class="overview-value">{{ adaptiveSummary.overallCorrectRate }}%</span>
            </div>
            <div class="overview-item">
              <span class="overview-label">推荐难度</span>
              <span class="overview-value recommend-diff">
                {{ adaptiveSummary.recommendedDifficulty.toFixed(1) }}
                <el-rate
                  :model-value="adaptiveSummary.recommendedDifficulty"
                  disabled
                  allow-half
                  :max="5"
                  size="small"
                />
              </span>
            </div>
          </div>

          <div class="difficulty-bars">
            <div v-for="item in adaptiveSummary.difficultyDetails" :key="item.difficulty" class="diff-bar-row">
              <span class="diff-label">{{ item.label }}</span>
              <div class="diff-bar-wrapper">
                <div class="diff-bar-bg">
                  <div
                    class="diff-bar-fill"
                    :style="{ width: item.weight * 100 + '%', backgroundColor: diffColors[item.difficulty - 1] }"
                  ></div>
                </div>
              </div>
              <span class="diff-weight">{{ (item.weight * 100).toFixed(0) }}%</span>
              <span class="diff-rate" v-if="item.total > 0">正确率 {{ item.correctRate }}%</span>
              <span class="diff-rate" v-else>暂无数据</span>
            </div>
          </div>

          <div class="adaptive-actions">
            <el-button
              type="primary"
              size="large"
              :icon="MagicStick"
              @click="startAdaptivePractice"
              :loading="adaptiveStartLoading"
            >
              开始智能推荐练习
            </el-button>
            <el-select v-model="adaptiveForm.courseId" placeholder="全部课程" clearable class="action-control">
              <el-option v-for="course in courseList" :key="course.id" :label="course.name" :value="course.id" />
            </el-select>
            <el-input-number v-model="adaptiveForm.count" :min="5" :max="50" />
          </div>
        </div>

        <div v-else-if="!adaptiveLoading" class="adaptive-empty">
          <el-empty description="暂无答题记录，开始刷题后将根据你的表现智能推荐题目">
            <el-button type="primary" :icon="Promotion" @click="scrollToConfig">开始刷题</el-button>
          </el-empty>
        </div>
      </el-card>

      <el-card class="config-card" ref="configCardRef" shadow="never">
        <template #header>
          <div class="card-header">
            <span>自选模式</span>
            <el-tag type="info" size="small">精准筛选</el-tag>
          </div>
        </template>

        <el-form :model="form" label-position="top">
          <el-form-item label="选择课程">
            <el-select v-model="form.courseId" placeholder="全部课程" clearable style="width: 100%">
              <el-option v-for="course in courseList" :key="course.id" :label="course.name" :value="course.id" />
            </el-select>
          </el-form-item>

          <el-form-item label="题型">
            <el-select v-model="form.questionType" placeholder="全部题型" clearable style="width: 100%">
              <el-option label="单选题" value="SINGLE_CHOICE" />
              <el-option label="多选题" value="MULTIPLE_CHOICE" />
              <el-option label="判断题" value="TRUE_FALSE" />
              <el-option label="填空题" value="FILL_BLANK" />
              <el-option label="简答题" value="SHORT_ANSWER" />
            </el-select>
          </el-form-item>

          <el-form-item label="难度">
            <el-rate v-model="form.difficulty" :max="5" allow-half />
          </el-form-item>

          <el-form-item label="题目数量">
            <el-input-number v-model="form.count" :min="1" :max="50" />
          </el-form-item>

          <el-form-item>
            <el-button type="primary" size="large" :icon="Promotion" @click="startPractice" :loading="loading">
              开始刷题
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </section>

    <section class="mode-strip">
      <button type="button" class="mode-card" @click="scrollToConfig">
        <el-icon><List /></el-icon>
        <strong>按课程整理</strong>
        <span>适合课后巩固，先选课程再控制题型和数量。</span>
      </button>
      <button type="button" class="mode-card" @click="scrollToConfig">
        <el-icon><Filter /></el-icon>
        <strong>按题型训练</strong>
        <span>集中练选择、判断、填空或简答，便于查漏补缺。</span>
      </button>
      <button type="button" class="mode-card" @click="startAdaptivePractice">
        <el-icon><TrendCharts /></el-icon>
        <strong>按表现推荐</strong>
        <span>根据历史正确率动态调整难度，减少盲目刷题。</span>
      </button>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { getCoursePage, type CourseVO } from '@/api/course'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Filter, List, MagicStick, Promotion, TrendCharts } from '@element-plus/icons-vue'
import { getPracticeQuestions, getPracticeStats, getAdaptiveQuestions, getAdaptiveSummary } from '@/api/practice'
import type { PracticeStatsVO, AdaptiveSummaryVO } from '@/api/practice'

const router = useRouter()
const loading = ref(false)
const statsLoading = ref(true)
const adaptiveLoading = ref(true)
const adaptiveStartLoading = ref(false)
const stats = ref<PracticeStatsVO | null>(null)
const adaptiveSummary = ref<AdaptiveSummaryVO | null>(null)
const courseList = ref<CourseVO[]>([])
const configCardRef = ref<{ $el: HTMLElement } | null>(null)

const diffColors = ['#67c23a', '#409eff', '#e6a23c', '#f56c6c', '#909399']

const form = reactive({
  courseId: undefined as number | undefined,
  questionType: '' as string,
  difficulty: undefined as number | undefined,
  count: 10,
})

const adaptiveForm = reactive({
  courseId: undefined as number | undefined,
  count: 10,
})

const statCards = computed(() => [
  { label: '总答题数', value: stats.value?.totalAnswered ?? 0, tone: 'tone-primary' },
  { label: '答对数', value: stats.value?.correctCount ?? 0, tone: 'tone-success' },
  { label: '答错数', value: stats.value?.wrongCount ?? 0, tone: 'tone-danger' },
  { label: '正确率', value: `${stats.value?.correctRate ?? 0}%`, tone: 'tone-warning' },
])

onMounted(() => {
  loadStats()
  loadCourses()
  loadAdaptiveSummary()
})

const loadStats = async () => {
  try {
    const res = await getPracticeStats()
    if (res.code === 0) {
      stats.value = res.data
    }
  } catch {
    // ignore
  } finally {
    statsLoading.value = false
  }
}

const loadCourses = async () => {
  try {
    const res = await getCoursePage({ pageNum: 1, pageSize: 100 })
    courseList.value = res.data?.records ?? []
  } catch {
    // ignore
  }
}

const loadAdaptiveSummary = async () => {
  try {
    const res = await getAdaptiveSummary()
    if (res.code === 0 && res.data && res.data.totalAnswered > 0) {
      adaptiveSummary.value = res.data
    }
  } catch {
    // ignore
  } finally {
    adaptiveLoading.value = false
  }
}

const startAdaptivePractice = async () => {
  adaptiveStartLoading.value = true
  try {
    const params: Parameters<typeof getAdaptiveQuestions>[0] = { count: adaptiveForm.count }
    if (adaptiveForm.courseId) params.courseId = adaptiveForm.courseId

    const res = await getAdaptiveQuestions(params)
    if (res.code === 0 && res.data && res.data.length > 0) {
      sessionStorage.setItem('practice_questions', JSON.stringify(res.data))
      sessionStorage.setItem('practice_mode', 'adaptive')
      router.push({ name: 'PracticeSession' })
    } else {
      ElMessage.warning('暂无可推荐的题目，请先添加题目或调整筛选条件')
    }
  } catch {
    ElMessage.error('获取题目失败')
  } finally {
    adaptiveStartLoading.value = false
  }
}

const startPractice = async () => {
  loading.value = true
  try {
    const params: Parameters<typeof getPracticeQuestions>[0] = { count: form.count }
    if (form.courseId) params.courseId = form.courseId
    if (form.questionType) params.questionType = form.questionType
    if (form.difficulty) params.difficulty = form.difficulty

    const res = await getPracticeQuestions(params)
    if (res.code === 0 && res.data && res.data.length > 0) {
      sessionStorage.setItem('practice_questions', JSON.stringify(res.data))
      sessionStorage.removeItem('practice_mode')
      router.push({ name: 'PracticeSession' })
    } else {
      ElMessage.warning('未找到符合条件的题目，请调整筛选条件')
    }
  } catch {
    ElMessage.error('获取题目失败')
  } finally {
    loading.value = false
  }
}

const scrollToConfig = () => {
  configCardRef.value?.$el?.scrollIntoView({ behavior: 'smooth' })
}
</script>

<style scoped>
.practice-container {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.practice-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding: 22px;
  background: var(--lp-surface);
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  box-shadow: var(--lp-shadow-sm);
}

.practice-hero h2 {
  margin: 4px 0 8px;
  font-size: 24px;
  color: var(--lp-text);
}

.practice-hero p {
  margin: 0;
  color: var(--lp-text-secondary);
  font-size: 14px;
}

.hero-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.section-kicker {
  color: var(--lp-primary);
  font-size: 12px;
  font-weight: 800;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.stat-card {
  min-height: 94px;
}

.stat-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.stat-card span {
  color: var(--lp-text-muted);
  font-size: 13px;
}

.stat-card strong {
  font-size: 28px;
  font-weight: 700;
}

.tone-primary {
  color: var(--lp-primary);
}
.tone-success {
  color: var(--lp-success);
}
.tone-danger {
  color: var(--lp-danger);
}
.tone-warning {
  color: var(--lp-warning);
}

.practice-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(320px, 0.65fr);
  gap: 16px;
}

.adaptive-content {
  padding-top: 4px;
}

.adaptive-overview {
  display: flex;
  gap: 24px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.overview-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.overview-label {
  font-size: 13px;
  color: var(--lp-text-muted);
}

.overview-value {
  font-size: 20px;
  font-weight: 600;
  color: var(--lp-text);
  display: flex;
  align-items: center;
  gap: 8px;
}

.recommend-diff :deep(.el-rate) {
  display: inline-flex;
}

.difficulty-bars {
  margin-bottom: 20px;
}

.diff-bar-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  font-size: 13px;
}

.diff-label {
  width: 36px;
  text-align: right;
  color: var(--lp-text-secondary);
  flex-shrink: 0;
}

.diff-bar-wrapper {
  flex: 1;
  min-width: 0;
}

.diff-bar-bg {
  height: 12px;
  background: var(--lp-surface-soft);
  border-radius: 6px;
  overflow: hidden;
}

.diff-bar-fill {
  height: 100%;
  border-radius: 6px;
  transition: width 0.6s ease;
  min-width: 2px;
}

.diff-weight {
  width: 40px;
  text-align: right;
  font-weight: 600;
  color: var(--lp-text);
  flex-shrink: 0;
}

.diff-rate {
  width: 90px;
  text-align: right;
  color: var(--lp-text-muted);
  flex-shrink: 0;
}

.adaptive-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.action-control {
  width: 200px;
}

.adaptive-empty {
  padding: 12px 0;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  font-size: 16px;
}

.mode-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.mode-card {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 4px 10px;
  align-items: start;
  min-height: 104px;
  padding: 16px;
  text-align: left;
  color: var(--lp-text);
  background: var(--lp-surface);
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  cursor: pointer;
  box-shadow: var(--lp-shadow-sm);
}

.mode-card .el-icon {
  grid-row: span 2;
  margin-top: 2px;
  color: var(--lp-primary);
  font-size: 20px;
}

.mode-card strong {
  font-size: 15px;
}

.mode-card span {
  color: var(--lp-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.mode-card:hover {
  border-color: var(--lp-primary);
}

@media (max-width: 960px) {
  .practice-grid,
  .mode-strip {
    grid-template-columns: 1fr;
  }

  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .practice-hero {
    align-items: stretch;
    flex-direction: column;
    padding: 16px;
  }

  .hero-actions {
    justify-content: stretch;
  }

  .hero-actions .el-button {
    flex: 1;
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }

  .adaptive-actions,
  .action-control {
    width: 100%;
  }
}
</style>
