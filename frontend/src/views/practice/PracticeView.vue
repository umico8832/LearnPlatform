<template>
  <div class="practice-container page-container">
    <LpPageHeader
      kicker="课程内学习活动"
      title="练习"
      description="优先用智能推荐保持节奏，也可以按课程、题型和难度自选一组题。"
    >
      <template #actions>
        <el-button type="primary" :icon="Promotion" @click="startAdaptivePractice" :loading="adaptiveStartLoading">
          智能推荐
        </el-button>
        <el-button :icon="Filter" @click="scrollToConfig">自选练习</el-button>
      </template>
    </LpPageHeader>

    <section class="stats-section" aria-label="练习统计">
      <LpSectionHeading
        kicker="学习概览"
        title="练习统计"
        description="累计答题情况，帮助你判断最近练习的节奏与短板。"
      />
      <div v-if="statsLoading" class="stats-grid">
        <LpSkeleton v-for="n in 4" :key="n" card :rows="2" />
      </div>
      <div v-else class="stats-grid">
        <LpStat v-for="item in statCards" :key="item.label" :label="item.label" :value="item.value" :tone="item.tone" />
      </div>
    </section>

    <div class="practice-grid">
      <section class="adaptive-section">
        <LpSectionHeading kicker="自适应模式" title="智能推荐" description="根据历史正确率动态调整难度，减少盲目刷题。">
          <template #aside>
            <el-tag v-if="adaptiveSummary" type="success" size="small" effect="plain">自适应模式</el-tag>
          </template>
        </LpSectionHeading>

        <div class="adaptive-panel" v-loading="adaptiveLoading">
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
            <LpEmptyState title="暂无答题记录" description="开始刷题后将根据你的表现智能推荐题目。">
              <template #actions>
                <el-button type="primary" :icon="Promotion" @click="scrollToConfig">开始刷题</el-button>
              </template>
            </LpEmptyState>
          </div>
        </div>
      </section>

      <section class="config-section">
        <LpSectionHeading kicker="自选模式" title="自选练习" description="按课程、题型和难度筛选一组题。">
          <template #aside>
            <el-tag type="info" size="small" effect="plain">精准筛选</el-tag>
          </template>
        </LpSectionHeading>

        <el-card class="config-card" ref="configCardRef" shadow="never">
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
    </div>

    <section class="mode-section">
      <LpSectionHeading
        kicker="怎么开始"
        title="三种练习方式"
        description="每种方式适合不同的目标，选择一个开始即可。"
      />
      <div class="mode-strip">
        <button type="button" class="mode-item" @click="scrollToConfig">
          <span class="mode-icon" aria-hidden="true"
            ><el-icon><List /></el-icon
          ></span>
          <span class="mode-copy">
            <strong>按课程整理</strong>
            <span>适合课后巩固，先选课程再控制题型和数量。</span>
          </span>
        </button>
        <button type="button" class="mode-item" @click="scrollToConfig">
          <span class="mode-icon" aria-hidden="true"
            ><el-icon><Filter /></el-icon
          ></span>
          <span class="mode-copy">
            <strong>按题型训练</strong>
            <span>集中练选择、判断、填空或简答，便于查漏补缺。</span>
          </span>
        </button>
        <button type="button" class="mode-item" @click="startAdaptivePractice">
          <span class="mode-icon" aria-hidden="true"
            ><el-icon><TrendCharts /></el-icon
          ></span>
          <span class="mode-copy">
            <strong>按表现推荐</strong>
            <span>根据历史正确率动态调整难度，减少盲目刷题。</span>
          </span>
        </button>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { getCoursePage, type CourseVO } from '@/api/course'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Filter, List, MagicStick, Promotion, TrendCharts } from '@element-plus/icons-vue'
import { getPracticeQuestions, getPracticeStats, getAdaptiveQuestions, getAdaptiveSummary } from '@/api/practice'
import type { PracticeStatsVO, AdaptiveSummaryVO } from '@/api/practice'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const statsLoading = ref(true)
const adaptiveLoading = ref(true)
const adaptiveStartLoading = ref(false)
const stats = ref<PracticeStatsVO | null>(null)
const adaptiveSummary = ref<AdaptiveSummaryVO | null>(null)
const courseList = ref<CourseVO[]>([])
const configCardRef = ref<{ $el: HTMLElement } | null>(null)

const diffColors = ['var(--lp-success)', 'var(--lp-primary)', 'var(--lp-warning)', 'var(--lp-danger)', 'var(--lp-info)']

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
  { label: '总答题数', value: stats.value?.totalAnswered ?? 0, tone: 'emphasis' as const },
  { label: '答对数', value: stats.value?.correctCount ?? 0, tone: 'default' as const },
  { label: '答错数', value: stats.value?.wrongCount ?? 0, tone: 'danger' as const },
  { label: '正确率', value: `${stats.value?.correctRate ?? 0}%`, tone: 'warning' as const },
])

function positiveQueryNumber(value: unknown) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : undefined
}

onMounted(() => {
  const presetCourseId = positiveQueryNumber(route.query.courseId)
  if (presetCourseId !== undefined) {
    form.courseId = presetCourseId
  }
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
  gap: var(--lp-space-8);
}

.stats-section,
.adaptive-section,
.config-section,
.mode-section {
  display: grid;
  gap: var(--lp-space-4);
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--lp-space-3);
}

.practice-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(320px, 0.65fr);
  gap: var(--lp-space-6);
  align-items: start;
}

/* 智能推荐面板：独立对象，使用 Surface + 边框 + 极轻阴影 */
.adaptive-panel,
.config-card {
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}

.adaptive-panel {
  padding: var(--lp-space-5);
  min-height: 120px;
}

.config-card {
  --el-card-border-color: transparent;
  border: var(--lp-border-hairline);
}

.config-card :deep(.el-card__body) {
  padding: var(--lp-space-5);
}

.adaptive-content {
  display: grid;
  gap: var(--lp-space-5);
}

.adaptive-overview {
  display: flex;
  gap: var(--lp-space-8);
  flex-wrap: wrap;
}

.overview-item {
  display: flex;
  flex-direction: column;
  gap: var(--lp-space-1);
}

.overview-label {
  font-size: var(--lp-text-xs);
  color: var(--lp-text-muted);
}

.overview-value {
  font-size: var(--lp-text-2xl);
  font-weight: var(--lp-weight-bold);
  color: var(--lp-text);
  display: flex;
  align-items: center;
  gap: var(--lp-space-2);
  font-variant-numeric: tabular-nums;
}

.recommend-diff :deep(.el-rate) {
  display: inline-flex;
}

.difficulty-bars {
  display: grid;
  gap: var(--lp-space-2);
}

.diff-bar-row {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  font-size: var(--lp-text-sm);
}

.diff-label {
  width: 40px;
  text-align: right;
  color: var(--lp-text-secondary);
  flex-shrink: 0;
}

.diff-bar-wrapper {
  flex: 1;
  min-width: 0;
}

.diff-bar-bg {
  height: 6px;
  background: var(--lp-surface-inset);
  border-radius: var(--lp-radius-full);
  overflow: hidden;
}

.diff-bar-fill {
  height: 100%;
  border-radius: var(--lp-radius-full);
  transition: width var(--lp-duration-slow) var(--lp-ease-out);
  min-width: 2px;
}

.diff-weight {
  width: 44px;
  text-align: right;
  font-weight: var(--lp-weight-semibold);
  color: var(--lp-text);
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

.diff-rate {
  width: 96px;
  text-align: right;
  color: var(--lp-text-muted);
  flex-shrink: 0;
}

.adaptive-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--lp-space-3);
}

.action-control {
  width: 200px;
}

.adaptive-empty {
  min-height: 120px;
}

/* 三种练习方式：说明性引导，不使用卡片，仅靠图标与留白分区 */
.mode-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--lp-space-3);
}

.mode-item {
  display: flex;
  gap: var(--lp-space-3);
  align-items: flex-start;
  padding: var(--lp-space-3) var(--lp-space-2);
  text-align: left;
  color: var(--lp-text);
  background: transparent;
  border: 0;
  border-radius: var(--lp-radius-md);
  cursor: pointer;
  transition: background-color var(--lp-duration-fast) var(--lp-ease-out);
}

.mode-item:hover {
  background: var(--lp-surface-subtle);
}

.mode-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  border-radius: var(--lp-radius-md);
  background: var(--lp-primary-soft);
  color: var(--lp-primary);
  font-size: var(--lp-text-lg);
}

.mode-copy {
  display: flex;
  flex-direction: column;
  gap: var(--lp-space-1);
  min-width: 0;
}

.mode-copy strong {
  font-size: var(--lp-text-md);
  font-weight: var(--lp-weight-semibold);
  color: var(--lp-text);
}

.mode-copy span {
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  line-height: var(--lp-leading-body);
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
  .stats-grid {
    grid-template-columns: 1fr;
  }

  .adaptive-actions,
  .action-control {
    width: 100%;
  }

  .adaptive-panel,
  .config-card :deep(.el-card__body) {
    padding: var(--lp-space-4);
  }

  .diff-rate {
    display: none;
  }
}
</style>
