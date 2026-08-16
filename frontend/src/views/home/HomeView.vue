<template>
  <div class="home-container page-container">
    <section class="workspace-hero">
      <div class="hero-copy">
        <span class="eyebrow">今日学习工作台</span>
        <h2>{{ greeting }}，{{ userInfo?.nickname || userInfo?.username || '同学' }}</h2>
        <p>先完成今日计划，再处理错题和复习任务，保持稳定推进。</p>
      </div>
      <div class="hero-actions">
        <el-button type="primary" :icon="Promotion" @click="go('/practice')">开始刷题</el-button>
        <el-button :icon="Timer" @click="go('/review')">智能复习</el-button>
      </div>
    </section>

    <section class="dashboard-grid">
      <el-card class="plan-card" shadow="never" v-loading="planLoading">
        <div class="card-heading">
          <div>
            <span class="section-kicker">今日计划</span>
            <h3>完成 {{ plan.dailyGoal }} 题目标</h3>
          </div>
          <el-button type="primary" link :icon="Setting" @click="showGoalDialog = true">设置</el-button>
        </div>

        <div class="plan-meter">
          <el-progress
            type="dashboard"
            :percentage="plan.progress"
            :width="150"
            :stroke-width="12"
            :color="progressColor"
            :format="(p: number) => `${p}%`"
          />
          <div class="plan-meta">
            <strong>{{ plan.todayCount }}</strong>
            <span>今日已完成题数</span>
            <p>距离目标还差 {{ remainingQuestions }} 题，连续学习 {{ plan.streakDays }} 天。</p>
          </div>
        </div>

        <div class="next-task">
          <span>下一步</span>
          <strong>{{ nextTask.title }}</strong>
          <el-button size="small" :icon="nextTask.icon" @click="go(nextTask.path)">
            {{ nextTask.action }}
          </el-button>
        </div>
      </el-card>

      <div class="metric-grid">
        <el-card
          v-for="metric in metrics"
          :key="metric.label"
          class="metric-card"
          shadow="never"
          v-loading="statsLoading"
        >
          <div class="metric-icon">
            <el-icon><component :is="metric.icon" /></el-icon>
          </div>
          <strong>{{ metric.value }}</strong>
          <span>{{ metric.label }}</span>
        </el-card>
      </div>
    </section>

    <section class="quick-panel">
      <div class="section-title">
        <div>
          <span class="section-kicker">高频入口</span>
          <h3>按任务继续学习</h3>
        </div>
      </div>
      <div class="quick-grid">
        <button v-for="link in quickLinks" :key="link.path" type="button" class="quick-card" @click="go(link.path)">
          <span class="quick-icon">
            <el-icon><component :is="link.icon" /></el-icon>
          </span>
          <strong>{{ link.title }}</strong>
          <small>{{ link.desc }}</small>
        </button>
      </div>
    </section>

    <section class="chart-grid">
      <el-card shadow="never">
        <template #header>
          <div class="chart-header">
            <span>近 7 天刷题趋势</span>
            <small>正确与错误题量</small>
          </div>
        </template>
        <div v-if="!trendEmpty" ref="trendChartRef" class="chart-container"></div>
        <el-empty v-else description="暂无刷题数据" :image-size="96" />
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="chart-header">
            <span>课程正确率分布</span>
            <small>最多展示 6 门课程</small>
          </div>
        </template>
        <div v-if="!courseEmpty" ref="courseChartRef" class="chart-container"></div>
        <el-empty v-else description="暂无课程数据" :image-size="96" />
      </el-card>
    </section>

    <el-dialog v-model="showGoalDialog" title="设置每日刷题目标" width="400px" :close-on-click-modal="false">
      <el-form label-width="92px">
        <el-form-item label="每日目标">
          <el-input-number v-model="goalInput" :min="1" :max="200" :step="5" />
          <span class="goal-unit">题/天</span>
        </el-form-item>
        <el-form-item>
          <el-text type="info" size="small">建议设置一个能长期坚持的目标。</el-text>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showGoalDialog = false">取消</el-button>
        <el-button type="primary" :loading="goalSaving" @click="handleSaveGoal">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount, computed, nextTick } from 'vue'
import type { Component } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import {
  Promotion,
  WarningFilled,
  Trophy,
  MagicStick,
  Calendar,
  Setting,
  Timer,
  DataLine,
  EditPen,
  TrendCharts,
  CircleCheckFilled,
} from '@element-plus/icons-vue'
import { getStatisticsOverview, getDailyTrend, getCourseStats } from '@/api/statistics'
import { getLearningPlan, updateDailyGoal } from '@/api/learningPlan'
import type { LearningPlanVO } from '@/api/learningPlan'
import { ElMessage } from 'element-plus'
import type { StatisticsOverview } from '@/api/statistics'
import { use } from 'echarts/core'
import { BarChart, RadarChart } from 'echarts/charts'
import { GridComponent, LegendComponent, RadarComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { ECharts } from 'echarts/core'
import { init } from 'echarts/core'

use([BarChart, RadarChart, GridComponent, LegendComponent, RadarComponent, TooltipComponent, CanvasRenderer])

const router = useRouter()
const userStore = useUserStore()
const userInfo = computed(() => userStore.userInfo)

const stats = reactive<StatisticsOverview>({
  totalPractice: 0,
  correctCount: 0,
  wrongCount: 0,
  correctRate: 0,
  todayPractice: 0,
  streakDays: 0,
  wrongQuestionCount: 0,
  masteredCount: 0,
})

const plan = reactive<LearningPlanVO>({
  dailyGoal: 20,
  todayCount: 0,
  progress: 0,
  streakDays: 0,
  lastPracticeDate: null,
})
const planLoading = ref(true)
const showGoalDialog = ref(false)
const goalInput = ref(20)
const goalSaving = ref(false)

const statsLoading = ref(true)
const trendEmpty = ref(false)
const courseEmpty = ref(false)
const trendChartRef = ref<HTMLElement | null>(null)
const courseChartRef = ref<HTMLElement | null>(null)
let trendChart: ECharts | null = null
let courseChart: ECharts | null = null

interface QuickLink {
  path: string
  title: string
  desc: string
  icon: Component
}

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 12) return '早上好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const progressColor = computed(() => {
  if (plan.progress >= 100) return '#2f855a'
  if (plan.progress >= 60) return '#1769aa'
  return '#d8a83f'
})

const remainingQuestions = computed(() => Math.max(plan.dailyGoal - plan.todayCount, 0))

const nextTask = computed(() => {
  if (remainingQuestions.value > 0) {
    return { title: '先补齐今日刷题目标', action: '去刷题', path: '/practice', icon: Promotion }
  }
  if (stats.wrongQuestionCount > 0) {
    return { title: '复盘错题并更新掌握度', action: '看错题', path: '/wrong-questions', icon: WarningFilled }
  }
  return { title: '查看学习诊断与推荐路径', action: '看诊断', path: '/learning-diagnosis', icon: TrendCharts }
})

const metrics = computed(() => [
  { label: '总刷题数', value: stats.totalPractice, icon: EditPen },
  { label: '正确率', value: `${stats.correctRate}%`, icon: CircleCheckFilled },
  { label: '今日刷题', value: stats.todayPractice, icon: Calendar },
  { label: '连续学习', value: `${stats.streakDays} 天`, icon: DataLine },
])

const quickLinks: QuickLink[] = [
  { path: '/practice', title: '开始刷题', desc: '按课程或知识点练习', icon: Promotion },
  { path: '/review', title: '智能复习', desc: '处理今日间隔复习', icon: Timer },
  { path: '/wrong-questions', title: '查看错题', desc: '跟进薄弱题目', icon: WarningFilled },
  { path: '/exams', title: '参加考试', desc: '完成阶段测评', icon: Trophy },
  { path: '/learning-diagnosis', title: '学习诊断', desc: '查看能力短板', icon: TrendCharts },
  { path: '/submit', title: '题目投稿', desc: '参与题库共建', icon: MagicStick },
]

function go(path: string) {
  router.push(path)
}

const handleSaveGoal = async () => {
  goalSaving.value = true
  try {
    const res = await updateDailyGoal(goalInput.value)
    if (res.code === 0 && res.data) {
      Object.assign(plan, res.data)
      showGoalDialog.value = false
      ElMessage.success('目标已更新')
    }
  } catch {
    ElMessage.error('保存失败，请重试')
  } finally {
    goalSaving.value = false
  }
}

function handleResize() {
  trendChart?.resize()
  courseChart?.resize()
}

onMounted(async () => {
  window.addEventListener('resize', handleResize)

  try {
    const planRes = await getLearningPlan()
    if (planRes.code === 0 && planRes.data) {
      Object.assign(plan, planRes.data)
      goalInput.value = planRes.data.dailyGoal
    }
  } catch {
    // 学习计划加载失败时保持默认状态
  } finally {
    planLoading.value = false
  }

  try {
    const res = await getStatisticsOverview()
    if (res.code === 0 && res.data) Object.assign(stats, res.data)
  } catch {
    // 统计数据加载失败时保持默认状态
  } finally {
    statsLoading.value = false
  }

  await nextTick()
  await loadTrendChart()
  await loadCourseChart()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  courseChart?.dispose()
})

const loadTrendChart = async () => {
  if (!trendChartRef.value) return
  try {
    const res = await getDailyTrend()
    if (res.code !== 0 || !res.data || res.data.length === 0) {
      trendEmpty.value = true
      return
    }
    const data = res.data
    trendChart = init(trendChartRef.value)
    trendChart.setOption({
      color: ['#2f855a', '#c2413b'],
      tooltip: { trigger: 'axis' },
      legend: { data: ['答对', '答错'], bottom: 0, icon: 'roundRect' },
      grid: { left: 36, right: 18, top: 24, bottom: 44 },
      xAxis: {
        type: 'category',
        data: data.map((d) => d.date.substring(5)),
        axisLine: { lineStyle: { color: '#dfe7ef' } },
        axisTick: { show: false },
      },
      yAxis: {
        type: 'value',
        minInterval: 1,
        splitLine: { lineStyle: { color: '#edf2f7' } },
      },
      series: [
        {
          name: '答对',
          type: 'bar',
          stack: 'total',
          barWidth: 18,
          data: data.map((d) => d.correct),
          itemStyle: { borderRadius: [5, 5, 0, 0] },
        },
        {
          name: '答错',
          type: 'bar',
          stack: 'total',
          barWidth: 18,
          data: data.map((d) => d.wrong),
          itemStyle: { borderRadius: [5, 5, 0, 0] },
        },
      ],
    })
  } catch {
    trendEmpty.value = true
  }
}

const loadCourseChart = async () => {
  if (!courseChartRef.value) return
  try {
    const res = await getCourseStats()
    if (res.code !== 0 || !res.data || res.data.length === 0) {
      courseEmpty.value = true
      return
    }
    const data = res.data.slice(0, 6)
    courseChart = init(courseChartRef.value)
    courseChart.setOption({
      color: ['#1769aa'],
      tooltip: { trigger: 'item', formatter: '{b}: {c}%' },
      radar: {
        indicator: data.map((d) => ({
          name: d.courseName.length > 6 ? `${d.courseName.substring(0, 6)}..` : d.courseName,
          max: 100,
        })),
        radius: '62%',
        splitLine: { lineStyle: { color: '#dfe7ef' } },
        splitArea: { areaStyle: { color: ['#ffffff', '#f8fafc'] } },
        axisName: { color: '#536272' },
      },
      series: [
        {
          type: 'radar',
          data: [
            {
              value: data.map((d) => d.correctRate),
              name: '正确率',
              areaStyle: { opacity: 0.18 },
              lineStyle: { width: 2 },
              symbolSize: 4,
            },
          ],
        },
      ],
    })
  } catch {
    courseEmpty.value = true
  }
}
</script>

<style scoped>
.home-container {
  display: grid;
  gap: 18px;
}

.workspace-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  min-height: 156px;
  padding: 28px;
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  background: linear-gradient(135deg, rgba(23, 105, 170, 0.12), rgba(216, 168, 63, 0.12)), #ffffff;
  box-shadow: var(--lp-shadow-sm);
}

.hero-copy {
  max-width: 680px;
}

.eyebrow,
.section-kicker {
  display: inline-flex;
  color: var(--lp-primary);
  font-size: 12px;
  font-weight: 800;
}

.workspace-hero h2 {
  margin: 8px 0 8px;
  color: var(--lp-text);
  font-size: 30px;
  line-height: 1.18;
  font-weight: 850;
}

.workspace-hero p {
  margin: 0;
  color: var(--lp-text-secondary);
  font-size: 15px;
}

.hero-actions {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(360px, 0.85fr);
  gap: 18px;
}

.card-heading,
.section-title,
.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.card-heading h3,
.section-title h3 {
  margin: 4px 0 0;
  color: var(--lp-text);
  font-size: 18px;
  font-weight: 800;
}

.plan-meter {
  display: flex;
  align-items: center;
  gap: 28px;
  padding: 14px 0 20px;
}

.plan-meta {
  display: grid;
  gap: 6px;
}

.plan-meta strong {
  color: var(--lp-text);
  font-size: 42px;
  line-height: 1;
}

.plan-meta span {
  color: var(--lp-text-secondary);
  font-weight: 700;
}

.plan-meta p {
  margin: 0;
  color: var(--lp-text-muted);
  font-size: 13px;
}

.next-task {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border-radius: var(--lp-radius);
  background: var(--lp-surface-soft);
  border: 1px solid var(--lp-border);
}

.next-task span {
  color: var(--lp-text-muted);
  font-size: 12px;
  font-weight: 800;
}

.next-task strong {
  min-width: 0;
  color: var(--lp-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.metric-card :deep(.el-card__body) {
  min-height: 134px;
  display: grid;
  align-content: space-between;
  gap: 8px;
}

.metric-icon,
.quick-icon {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: var(--lp-radius);
  background: var(--lp-primary-soft);
  color: var(--lp-primary);
  font-size: 19px;
}

.metric-card strong {
  color: var(--lp-text);
  font-size: 28px;
  line-height: 1;
}

.metric-card span {
  color: var(--lp-text-muted);
  font-size: 13px;
  font-weight: 700;
}

.quick-panel {
  padding: 20px;
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  background: #ffffff;
  box-shadow: var(--lp-shadow-sm);
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.quick-card {
  min-height: 126px;
  display: grid;
  align-content: start;
  gap: 9px;
  padding: 14px;
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  background: #ffffff;
  text-align: left;
  cursor: pointer;
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease,
    border-color 0.18s ease;
}

.quick-card:hover {
  transform: translateY(-2px);
  border-color: #9bb7d0;
  box-shadow: var(--lp-shadow-md);
}

.quick-card strong {
  color: var(--lp-text);
  font-size: 15px;
}

.quick-card small {
  color: var(--lp-text-muted);
  line-height: 1.4;
}

.chart-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(320px, 0.75fr);
  gap: 18px;
}

.chart-header span {
  color: var(--lp-text);
  font-weight: 800;
}

.chart-header small {
  color: var(--lp-text-muted);
  font-size: 12px;
}

.chart-container {
  height: 300px;
}

.goal-unit {
  margin-left: 8px;
  color: var(--lp-text-muted);
}

@media (max-width: 1180px) {
  .quick-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 980px) {
  .dashboard-grid,
  .chart-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 767px) {
  .workspace-hero {
    align-items: stretch;
    flex-direction: column;
    min-height: auto;
    padding: 18px;
  }

  .workspace-hero h2 {
    font-size: 23px;
  }

  .hero-actions {
    flex-wrap: wrap;
  }

  .hero-actions .el-button {
    flex: 1;
    min-width: 128px;
  }

  .plan-meter {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .next-task {
    grid-template-columns: 1fr;
    align-items: start;
  }

  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
  }

  .metric-card :deep(.el-card__body) {
    min-height: 116px;
  }

  .metric-card strong {
    font-size: 23px;
  }

  .quick-panel {
    padding: 14px;
  }

  .quick-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
  }

  .quick-card {
    min-height: 118px;
    padding: 12px;
  }

  .chart-container {
    height: 240px;
  }
}
</style>
