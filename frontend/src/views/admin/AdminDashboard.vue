<template>
  <div class="dashboard-container admin-page">
    <header class="dashboard-header admin-page-header">
      <div>
        <p class="admin-page-kicker">PLATFORM OVERVIEW</p>
        <h2>平台数据总览</h2>
        <p class="admin-page-description">掌握内容供给、用户活跃与练习趋势，优先发现题库增长、试卷发布和活跃波动。</p>
      </div>
      <div class="update-time">
        <el-icon><Refresh /></el-icon>
        <span>数据更新于 {{ updateTime }}</span>
      </div>
    </header>

    <el-skeleton :loading="loading" animated :rows="8">
      <template #default>
        <el-row :gutter="16" class="metric-grid">
          <el-col v-for="metric in metrics" :key="metric.label" :xs="24" :sm="12" :lg="6">
            <el-card shadow="never" class="metric-card">
              <div class="metric-icon" :style="{ color: metric.color, backgroundColor: metric.background }">
                <el-icon><component :is="metric.icon" /></el-icon>
              </div>
              <div class="metric-content">
                <span class="metric-label">{{ metric.label }}</span>
                <strong class="metric-value">{{ metric.value }}</strong>
                <span class="metric-note">{{ metric.note }}</span>
              </div>
            </el-card>
          </el-col>
        </el-row>

        <el-row :gutter="16" class="chart-grid">
          <el-col :xs="24" :lg="16">
            <el-card shadow="never" class="panel-card">
              <template #header>
                <div class="panel-header">
                  <div>
                    <strong>近 7 日平台活跃</strong>
                    <span>刷题次数与活跃用户变化</span>
                  </div>
                  <el-tag type="success" effect="plain">累计 {{ stats.totalPracticeRecords }} 次练习</el-tag>
                </div>
              </template>
              <div ref="activityChartRef" class="activity-chart"></div>
            </el-card>
          </el-col>

          <el-col :xs="24" :lg="8">
            <el-card shadow="never" class="panel-card">
              <template #header>
                <div class="panel-header">
                  <div>
                    <strong>题型分布</strong>
                    <span>当前题库内容结构</span>
                  </div>
                </div>
              </template>
              <div ref="questionChartRef" class="question-chart"></div>
            </el-card>
          </el-col>
        </el-row>

        <el-row :gutter="16" class="status-grid">
          <el-col :xs="24" :md="12">
            <el-card shadow="never" class="status-card">
              <div class="status-copy">
                <span class="status-label">用户状态</span>
                <strong>{{ stats.enabledUsers }} / {{ stats.totalUsers }}</strong>
                <span>启用用户</span>
              </div>
              <el-progress type="dashboard" :percentage="enabledUserRate" :width="112" color="#2f855a" />
            </el-card>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-card shadow="never" class="status-card">
              <div class="status-copy">
                <span class="status-label">试卷发布</span>
                <strong>{{ stats.publishedExamPapers }} / {{ stats.totalExamPapers }}</strong>
                <span>{{ stats.draftExamPapers }} 份草稿待发布</span>
              </div>
              <el-progress type="dashboard" :percentage="publishedPaperRate" :width="112" color="#b7791f" />
            </el-card>
          </el-col>
        </el-row>
      </template>
    </el-skeleton>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Collection, DataAnalysis, Document, Refresh, User } from '@element-plus/icons-vue'
import { init, use } from 'echarts/core'
import { LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { ECharts } from 'echarts/core'

use([LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])
import { getAdminStatisticsOverview } from '@/api/statistics'
import type { AdminStatisticsOverview } from '@/api/statistics'

const emptyStats: AdminStatisticsOverview = {
  totalUsers: 0,
  enabledUsers: 0,
  totalQuestions: 0,
  weeklyNewQuestions: 0,
  totalExamPapers: 0,
  publishedExamPapers: 0,
  draftExamPapers: 0,
  todayActiveUsers: 0,
  totalPracticeRecords: 0,
  questionTypeDistribution: {},
  dailyActivity: [],
}

const loading = ref(true)
const stats = ref<AdminStatisticsOverview>({ ...emptyStats })
const updateTime = ref('--')
const activityChartRef = ref<HTMLElement | null>(null)
const questionChartRef = ref<HTMLElement | null>(null)
let activityChart: ECharts | null = null
let questionChart: ECharts | null = null

const metrics = computed(() => [
  {
    label: '注册用户',
    value: stats.value.totalUsers,
    note: `${stats.value.enabledUsers} 位用户正常启用`,
    icon: User,
    color: '#2563eb',
    background: '#eff6ff',
  },
  {
    label: '题库总量',
    value: stats.value.totalQuestions,
    note: `近 7 日新增 ${stats.value.weeklyNewQuestions} 道`,
    icon: Collection,
    color: '#0f766e',
    background: '#f0fdfa',
  },
  {
    label: '试卷总量',
    value: stats.value.totalExamPapers,
    note: `${stats.value.publishedExamPapers} 份已发布`,
    icon: Document,
    color: '#b45309',
    background: '#fffbeb',
  },
  {
    label: '今日活跃',
    value: stats.value.todayActiveUsers,
    note: '今日参与刷题的用户',
    icon: DataAnalysis,
    color: '#be123c',
    background: '#fff1f2',
  },
])

const enabledUserRate = computed(() => percentage(stats.value.enabledUsers, stats.value.totalUsers))
const publishedPaperRate = computed(() => percentage(stats.value.publishedExamPapers, stats.value.totalExamPapers))

onMounted(loadDashboard)

onBeforeUnmount(() => {
  activityChart?.dispose()
  questionChart?.dispose()
  window.removeEventListener('resize', resizeCharts)
})

async function loadDashboard() {
  loading.value = true
  let loaded = false
  try {
    const res = await getAdminStatisticsOverview()
    if (res.code === 0 && res.data) {
      stats.value = res.data
      updateTime.value = new Date().toLocaleString('zh-CN', { hour12: false })
      loaded = true
    }
  } catch {
    ElMessage.error('管理端统计数据加载失败')
  } finally {
    loading.value = false
  }

  if (loaded) {
    await nextTick()
    renderCharts()
    window.addEventListener('resize', resizeCharts)
  }
}

function renderCharts() {
  renderActivityChart()
  renderQuestionChart()
}

function renderActivityChart() {
  if (!activityChartRef.value) return
  activityChart?.dispose()
  activityChart = init(activityChartRef.value)
  const data = stats.value.dailyActivity || []
  activityChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['刷题次数', '活跃用户'], right: 8, top: 0 },
    grid: { left: 40, right: 24, top: 50, bottom: 28 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: data.map((item) => item.date.substring(5)),
      axisLine: { lineStyle: { color: '#d8dee8' } },
      axisLabel: { color: '#7a8492' },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#eef1f5' } },
      axisLabel: { color: '#7a8492' },
    },
    series: [
      {
        name: '刷题次数',
        type: 'line',
        smooth: true,
        symbolSize: 7,
        data: data.map((item) => item.practiceCount),
        lineStyle: { width: 3, color: '#2563eb' },
        itemStyle: { color: '#2563eb' },
        areaStyle: { color: 'rgba(37, 99, 235, 0.08)' },
      },
      {
        name: '活跃用户',
        type: 'line',
        smooth: true,
        symbolSize: 7,
        data: data.map((item) => item.activeUsers),
        lineStyle: { width: 2, color: '#0f766e' },
        itemStyle: { color: '#0f766e' },
      },
    ],
  })
}

function renderQuestionChart() {
  if (!questionChartRef.value) return
  questionChart?.dispose()
  questionChart = init(questionChartRef.value)
  const data = Object.entries(stats.value.questionTypeDistribution || {}).map(([name, value]) => ({ name, value }))
  questionChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}<br/>{c} 道（{d}%）' },
    legend: { orient: 'horizontal', bottom: 0, itemWidth: 10, itemHeight: 10 },
    color: ['#2563eb', '#0f766e', '#b45309', '#7c3aed', '#be123c'],
    series: [
      {
        type: 'pie',
        radius: ['45%', '70%'],
        center: ['50%', '44%'],
        avoidLabelOverlap: true,
        label: { show: false },
        data,
      },
    ],
  })
}

function resizeCharts() {
  activityChart?.resize()
  questionChart?.resize()
}

function percentage(value: number, total: number) {
  return total > 0 ? Math.round((value * 100) / total) : 0
}
</script>

<style scoped>
.dashboard-container {
  min-height: 100%;
  color: var(--lp-text);
}

.update-time {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--lp-text-muted);
  font-size: 12px;
}

.metric-grid,
.chart-grid {
  margin-bottom: 16px;
}

.metric-grid :deep(.el-col),
.chart-grid :deep(.el-col),
.status-grid :deep(.el-col) {
  margin-bottom: 16px;
}

.metric-card,
.panel-card,
.status-card {
  border-color: var(--lp-border);
  border-radius: var(--lp-radius);
}

.metric-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  gap: 16px;
  min-height: 112px;
  padding: 20px;
}

.metric-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 12px;
  font-size: 23px;
}

.metric-content {
  display: flex;
  flex-direction: column;
}

.metric-label,
.metric-note,
.panel-header span,
.status-copy span {
  color: #8490a0;
  font-size: 12px;
}

.metric-value {
  margin: 3px 0;
  color: #172033;
  font-size: 28px;
  line-height: 1.2;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.panel-header > div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.panel-header strong {
  font-size: 15px;
}

.activity-chart,
.question-chart {
  height: 320px;
}

.status-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 140px;
  padding: 18px 28px;
}

.status-copy {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.status-copy strong {
  color: #172033;
  font-size: 28px;
}

.status-label {
  color: #4b5563 !important;
  font-weight: 700;
}

@media (max-width: 768px) {
  .dashboard-container {
    padding: 16px;
  }

  .activity-chart,
  .question-chart {
    height: 280px;
  }
}
</style>
