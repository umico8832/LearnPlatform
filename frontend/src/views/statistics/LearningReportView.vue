<template>
  <div class="report-container">
    <div class="report-header">
      <div>
        <h2>个人学习报告</h2>
        <p class="report-month">{{ currentMonthText }} · 学习表现复盘</p>
      </div>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" v-loading="loading" style="height: 200px;"></div>

    <template v-else>
      <!-- 核心指标卡片 -->
      <el-row :gutter="20" class="metric-row">
        <el-col :xs="12" :sm="8" :md="4">
          <el-card shadow="hover" class="metric-card">
            <div class="metric-value primary">{{ report.monthTotalPractice }}</div>
            <div class="metric-label">本月刷题</div>
            <div class="metric-sub" :class="report.practiceGrowthRate >= 0 ? 'text-success' : 'text-danger'">
              {{ report.practiceGrowthRate >= 0 ? '↑' : '↓' }} {{ Math.abs(report.practiceGrowthRate) }}%
              <span class="vs-text">vs 上月 {{ report.lastMonthTotalPractice }}</span>
            </div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="8" :md="4">
          <el-card shadow="hover" class="metric-card">
            <div class="metric-value success">{{ report.monthCorrectRate }}%</div>
            <div class="metric-label">本月正确率</div>
            <div class="metric-sub text-muted">
              {{ report.monthCorrectCount }}/{{ report.monthTotalPractice }} 对/总
            </div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="8" :md="4">
          <el-card shadow="hover" class="metric-card">
            <div class="metric-value" :class="report.correctRateChange >= 0 ? 'success' : 'warning'">
              {{ report.correctRateChange >= 0 ? '+' : '' }}{{ report.correctRateChange }}
            </div>
            <div class="metric-label">正确率变化</div>
            <div class="metric-sub text-muted">
              pct · 上月 {{ report.lastMonthCorrectRate }}%
            </div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="8" :md="4">
          <el-card shadow="hover" class="metric-card">
            <div class="metric-value danger">{{ report.monthNewWrongCount }}</div>
            <div class="metric-label">本月新增错题</div>
            <div class="metric-sub text-muted">
              已掌握 {{ report.monthMasteredCount }} 题
            </div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="8" :md="4">
          <el-card shadow="hover" class="metric-card">
            <div class="metric-value primary">{{ report.monthExamCount }}</div>
            <div class="metric-label">本月考试</div>
            <div class="metric-sub text-muted">
              平均 {{ report.monthExamAvgScore }} 分
            </div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="8" :md="4">
          <el-card shadow="hover" class="metric-card">
            <div class="metric-value primary">{{ Object.values(report.questionTypeDistribution || {}).reduce((a, b) => a + b, 0) }}</div>
            <div class="metric-label">本月题型覆盖</div>
            <div class="metric-sub text-muted">
              {{ Object.keys(report.questionTypeDistribution || {}).length }} 种题型
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-card shadow="hover" class="effect-card" :class="effectLevelClass">
        <div class="effect-summary">
          <div>
            <div class="section-title">学习效果</div>
            <div class="effect-title">
              <span>{{ report.learningEffectLabel }}</span>
              <el-tag :type="effectTagType" effect="light">{{ report.learningEffectScore }} 分</el-tag>
            </div>
            <p>{{ report.learningEffectSummary }}</p>
            <div class="effect-note">
              <span>综合分由正确率变化、错题转化、复习掌握和活跃天数共同构成</span>
            </div>
          </div>
          <el-progress
            type="dashboard"
            :percentage="effectScorePercent"
            :color="effectProgressColor"
            :width="116"
          />
        </div>
        <div class="effect-grid">
          <div v-for="item in effectDimensions" :key="item.label" class="effect-item">
            <div class="effect-item-head">
              <span>{{ item.label }}</span>
              <strong :class="item.className">{{ item.display }}</strong>
            </div>
            <div class="effect-bar">
              <i :style="{ width: `${item.progress}%`, backgroundColor: item.color }"></i>
            </div>
            <p>{{ item.description }}</p>
          </div>
        </div>
      </el-card>

      <!-- 复习统计卡片 -->
      <el-row :gutter="20" class="metric-row" v-if="report.totalReviewCards > 0">
        <el-col :xs="12" :sm="8" :md="4">
          <el-card shadow="hover" class="metric-card">
            <div class="metric-value primary">{{ report.totalReviewCards }}</div>
            <div class="metric-label">复习卡片总数</div>
            <div class="metric-sub text-muted">
              已掌握 {{ report.masteredReviewCards }} 张
            </div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="8" :md="4">
          <el-card shadow="hover" class="metric-card">
            <div class="metric-value success">{{ report.monthlyReviewedCount }}</div>
            <div class="metric-label">本月复习次数</div>
            <div class="metric-sub text-muted">
              间隔重复系统
            </div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="8" :md="4">
          <el-card shadow="hover" class="metric-card">
            <div class="metric-value" :class="report.reviewStreakDays > 0 ? 'success' : 'warning'">
              {{ report.reviewStreakDays }}
            </div>
            <div class="metric-label">连续复习天数</div>
            <div class="metric-sub text-muted">
              坚持就是胜利 💪
            </div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="8" :md="4">
          <el-card shadow="hover" class="metric-card">
            <div class="metric-value" :class="report.dueTodayCount > 0 ? 'danger' : 'success'">
              {{ report.dueTodayCount }}
            </div>
            <div class="metric-label">今日待复习</div>
            <div class="metric-sub text-muted">
              {{ report.dueTodayCount > 0 ? '别忘了复习哦' : '已完成 ✓' }}
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 图表区域 -->
      <el-row :gutter="20" class="chart-row">
        <!-- 本月每日刷题趋势 -->
        <el-col :xs="24" :lg="16">
          <el-card shadow="hover">
            <template #header>
              <span class="chart-title">📈 本月每日刷题趋势</span>
            </template>
            <div v-if="report.dailyTrend && report.dailyTrend.length > 0">
              <div ref="dailyChartRef" class="chart-container"></div>
            </div>
            <el-empty v-else description="暂无刷题数据" />
          </el-card>
        </el-col>

        <!-- 题型分布 -->
        <el-col :xs="24" :lg="8">
          <el-card shadow="hover">
            <template #header>
              <span class="chart-title">📋 题型分布</span>
            </template>
            <div v-if="hasTypeData">
              <div ref="typeChartRef" class="chart-container"></div>
            </div>
            <el-empty v-else description="暂无题型数据" />
          </el-card>
        </el-col>
      </el-row>

      <!-- 本月每日复习趋势 -->
      <el-row :gutter="20" class="chart-row" v-if="report.totalReviewCards > 0">
        <el-col :xs="24">
          <el-card shadow="hover">
            <template #header>
              <span class="chart-title">🔄 本月每日复习趋势</span>
            </template>
            <div v-if="report.monthlyReviewTrend && report.monthlyReviewTrend.length > 0">
              <div ref="reviewChartRef" class="chart-container"></div>
            </div>
            <el-empty v-else description="本月暂无复习数据" />
          </el-card>
        </el-col>
      </el-row>

      <!-- 课程正确率 -->
      <el-row :gutter="20" class="chart-row">
        <el-col :xs="24">
          <el-card shadow="hover">
            <template #header>
              <span class="chart-title">📚 本月各课程正确率</span>
            </template>
            <div v-if="report.courseStats && report.courseStats.length > 0">
              <div ref="courseChartRef" class="chart-container-lg"></div>
            </div>
            <el-empty v-else description="本月暂无课程刷题数据" />
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { getLearningReport } from '@/api/statistics'
import type { LearningReport } from '@/api/statistics'
import { ElMessage } from 'element-plus'
import { use } from 'echarts/core'
import { BarChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent, TitleComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { ECharts } from 'echarts/core'
import { init } from 'echarts/core'

use([BarChart, PieChart, GridComponent, LegendComponent, TooltipComponent, TitleComponent, CanvasRenderer])

const currentMonthText = computed(() => {
  const now = new Date()
  return `${now.getFullYear()} 年 ${now.getMonth() + 1} 月`
})

const loading = ref(true)
const report = reactive<LearningReport>({
  monthTotalPractice: 0,
  monthCorrectCount: 0,
  monthCorrectRate: 0,
  monthNewWrongCount: 0,
  monthMasteredCount: 0,
  monthExamCount: 0,
  monthExamAvgScore: 0,
  lastMonthTotalPractice: 0,
  lastMonthCorrectRate: 0,
  practiceGrowthRate: 0,
  correctRateChange: 0,
  learningEffectScore: 0,
  learningEffectLevel: 'AT_RISK',
  learningEffectLabel: '需要关注',
  learningEffectSummary: '暂无足够学习数据，先完成几组练习建立基线。',
  wrongQuestionConversionRate: 0,
  reviewMasteryRate: 0,
  activeStudyDays: 0,
  dailyTrend: [],
  courseStats: [],
  questionTypeDistribution: {},
  totalReviewCards: 0,
  monthlyReviewedCount: 0,
  reviewStreakDays: 0,
  masteredReviewCards: 0,
  dueTodayCount: 0,
  monthlyReviewTrend: []
})

const hasTypeData = computed(() => {
  const dist = report.questionTypeDistribution
  return dist && Object.keys(dist).length > 0 && Object.values(dist).some(v => v > 0)
})

const effectTagType = computed(() => {
  switch (report.learningEffectLevel) {
    case 'EXCELLENT': return 'success'
    case 'IMPROVING': return 'primary'
    case 'STABLE': return 'warning'
    default: return 'danger'
  }
})

const effectProgressColor = computed(() => {
  switch (report.learningEffectLevel) {
    case 'EXCELLENT': return '#67c23a'
    case 'IMPROVING': return '#409eff'
    case 'STABLE': return '#e6a23c'
    default: return '#f56c6c'
  }
})

const effectLevelClass = computed(() => `is-${report.learningEffectLevel.toLowerCase().replace('_', '-')}`)

const effectScorePercent = computed(() => clampPercent(Math.round(report.learningEffectScore)))

const effectDimensions = computed(() => [
  {
    label: '正确率提升',
    display: `${report.correctRateChange >= 0 ? '+' : ''}${report.correctRateChange} pct`,
    description: report.correctRateChange >= 0 ? '本月答题质量高于上月基线' : '本月正确率低于上月，需要回看薄弱题型',
    progress: clampPercent(50 + report.correctRateChange * 2),
    color: report.correctRateChange >= 0 ? '#67c23a' : '#f56c6c',
    className: report.correctRateChange >= 0 ? 'text-success' : 'text-danger'
  },
  {
    label: '错题转化率',
    display: `${report.wrongQuestionConversionRate}%`,
    description: '衡量新增错题中已转化为掌握的比例',
    progress: clampPercent(report.wrongQuestionConversionRate),
    color: '#409eff',
    className: ''
  },
  {
    label: '复习掌握率',
    display: `${report.reviewMasteryRate}%`,
    description: '间隔重复卡片中已达到掌握状态的比例',
    progress: clampPercent(report.reviewMasteryRate),
    color: '#7c4dff',
    className: ''
  },
  {
    label: '活跃学习天数',
    display: `${report.activeStudyDays} 天`,
    description: '本月产生真实学习记录的天数',
    progress: activeStudyProgress.value,
    color: '#e6a23c',
    className: ''
  }
])

const activeStudyProgress = computed(() => {
  const now = new Date()
  const daysInMonth = new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate()
  return clampPercent(Math.round((report.activeStudyDays / daysInMonth) * 100))
})

function clampPercent(value: number) {
  if (!Number.isFinite(value)) return 0
  return Math.max(0, Math.min(100, value))
}

// Chart refs
const dailyChartRef = ref<HTMLElement | null>(null)
const typeChartRef = ref<HTMLElement | null>(null)
const courseChartRef = ref<HTMLElement | null>(null)
const reviewChartRef = ref<HTMLElement | null>(null)
let dailyChart: ECharts | null = null
let typeChart: ECharts | null = null
let courseChart: ECharts | null = null
let reviewChart: ECharts | null = null

function handleResize() {
  dailyChart?.resize()
  typeChart?.resize()
  courseChart?.resize()
  reviewChart?.resize()
}

function initDailyChart() {
  if (!dailyChartRef.value || !report.dailyTrend?.length) return
  dailyChart = init(dailyChartRef.value)
  const dates = report.dailyTrend.map(d => {
    const parts = d.date.split('-')
    return `${parts[1]}/${parts[2]}`
  })
  const corrects = report.dailyTrend.map(d => d.correct)
  const wrongs = report.dailyTrend.map(d => d.wrong)

  dailyChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' }
    },
    legend: { data: ['答对', '答错'], top: 0, right: 0 },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      data: dates,
      axisLabel: { rotate: dates.length > 15 ? 45 : 0, fontSize: 11 }
    },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: '答对',
        type: 'bar',
        stack: 'total',
        data: corrects,
        itemStyle: { color: '#67c23a' },
        barMaxWidth: 24
      },
      {
        name: '答错',
        type: 'bar',
        stack: 'total',
        data: wrongs,
        itemStyle: { color: '#f56c6c' },
        barMaxWidth: 24
      }
    ]
  })
}

function initTypeChart() {
  if (!typeChartRef.value || !hasTypeData.value) return
  typeChart = init(typeChartRef.value)
  const data = Object.entries(report.questionTypeDistribution)
    .filter(([, v]) => v > 0)
    .map(([name, value]) => ({ name, value }))

  const colors = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399']
  typeChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 题 ({d}%)' },
    legend: { orient: 'vertical', right: '5%', top: 'center' },
    color: colors,
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['40%', '50%'],
      avoidLabelOverlap: false,
      label: { show: false },
      emphasis: {
        label: { show: true, fontSize: 14, fontWeight: 'bold' }
      },
      data
    }]
  })
}

function initReviewChart() {
  if (!reviewChartRef.value || !report.monthlyReviewTrend?.length) return
  reviewChart = init(reviewChartRef.value)
  const now = new Date()
  const monthStart = new Date(now.getFullYear(), now.getMonth(), 1)
  const dates: string[] = []
  for (let i = 0; i < report.monthlyReviewTrend.length; i++) {
    const d = new Date(monthStart)
    d.setDate(d.getDate() + i)
    dates.push(`${d.getMonth() + 1}/${d.getDate()}`)
  }

  reviewChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line' }
    },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      data: dates,
      axisLabel: { rotate: dates.length > 15 ? 45 : 0, fontSize: 11 }
    },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: '复习卡片数',
        type: 'bar',
        data: report.monthlyReviewTrend,
        itemStyle: { color: '#7c4dff' },
        barMaxWidth: 20
      }
    ]
  })
}

function initCourseChart() {
  if (!courseChartRef.value || !report.courseStats?.length) return
  courseChart = init(courseChartRef.value)
  const names = report.courseStats.map(c => c.courseName)
  const rates = report.courseStats.map(c => c.correctRate)
  const totals = report.courseStats.map(c => c.total)

  courseChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: unknown) => {
        const list = params as Array<{ dataIndex: number }>
        if (!list.length) return ''
        const idx = list[0].dataIndex
        return `${names[idx]}<br/>正确率: ${rates[idx]}%<br/>刷题量: ${totals[idx]} 题`
      }
    },
    grid: { left: '3%', right: '4%', bottom: '8%', containLabel: true },
    xAxis: {
      type: 'category',
      data: names,
      axisLabel: { rotate: names.length > 5 ? 30 : 0, fontSize: 12 }
    },
    yAxis: [
      { type: 'value', name: '正确率(%)', max: 100, minInterval: 1 },
      { type: 'value', name: '刷题量', minInterval: 1 }
    ],
    series: [
      {
        name: '正确率',
        type: 'bar',
        data: rates,
        itemStyle: {
          color: (params: { value: number }) => {
            return params.value >= 80 ? '#67c23a' : params.value >= 60 ? '#e6a23c' : '#f56c6c'
          }
        },
        barMaxWidth: 40,
        label: { show: true, position: 'top', formatter: '{c}%', fontSize: 11 }
      },
      {
        name: '刷题量',
        type: 'bar',
        yAxisIndex: 1,
        data: totals,
        itemStyle: { color: '#409eff', opacity: 0.5 },
        barMaxWidth: 40
      }
    ]
  })
}

onMounted(async () => {
  window.addEventListener('resize', handleResize)
  try {
    const res = await getLearningReport()
    if (res.code === 0 && res.data) {
      Object.assign(report, res.data)
    }
  } catch {
    ElMessage.error('加载学习报告失败')
  } finally {
    loading.value = false
  }

  await nextTick()
  initDailyChart()
  initTypeChart()
  initReviewChart()
  initCourseChart()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  dailyChart?.dispose()
  typeChart?.dispose()
  courseChart?.dispose()
  reviewChart?.dispose()
})
</script>

<style scoped>
.report-container {
  max-width: 1200px;
}

.report-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.report-header h2 {
  margin: 0 0 6px;
  color: #1f2d3d;
  font-size: 26px;
  font-weight: 750;
  letter-spacing: 0;
}

.report-month {
  margin: 0;
  color: #7b8794;
  font-size: 14px;
}

.metric-row {
  margin-bottom: 20px;
}

.metric-row .el-col {
  margin-bottom: 12px;
}

.metric-card {
  text-align: center;
  padding: 4px 0;
  border: 1px solid #edf0f5;
}

.metric-card .el-card__body {
  padding: 16px 12px;
}

.metric-value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
  white-space: nowrap;
}

.metric-value.primary { color: #409eff; }
.metric-value.success { color: #67c23a; }
.metric-value.warning { color: #e6a23c; }
.metric-value.danger { color: #f56c6c; }

.metric-label {
  font-size: 13px;
  color: #606266;
  margin: 6px 0 4px;
}

.metric-sub {
  font-size: 12px;
  line-height: 1.4;
}

.metric-sub.text-success { color: #67c23a; }
.metric-sub.text-danger { color: #f56c6c; }
.metric-sub.text-muted { color: #909399; }

.text-success { color: #67c23a; }
.text-danger { color: #f56c6c; }

.vs-text {
  font-size: 11px;
  color: #c0c4cc;
}

.effect-card {
  margin-bottom: 20px;
  overflow: hidden;
  border: 1px solid #e6ebf2;
  background:
    linear-gradient(135deg, rgba(64, 158, 255, 0.08), rgba(103, 194, 58, 0.05) 42%, rgba(255, 255, 255, 0) 72%),
    #fff;
}

.effect-card.is-excellent {
  border-color: rgba(103, 194, 58, 0.35);
}

.effect-card.is-improving {
  border-color: rgba(64, 158, 255, 0.35);
}

.effect-card.is-stable {
  border-color: rgba(230, 162, 60, 0.35);
}

.effect-card.is-at-risk {
  border-color: rgba(245, 108, 108, 0.32);
}

.effect-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

.section-title {
  margin-bottom: 8px;
  color: #6b7785;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
  text-transform: uppercase;
}

.effect-title {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #303133;
  font-size: 22px;
  font-weight: 700;
}

.effect-summary p {
  max-width: 720px;
  margin: 10px 0 0;
  color: #606266;
  font-size: 14px;
  line-height: 1.7;
}

.effect-note {
  display: inline-flex;
  margin-top: 12px;
  padding: 7px 10px;
  border: 1px solid #d9e6f7;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
  color: #526578;
  font-size: 12px;
  line-height: 1.5;
}

.effect-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
}

.effect-item {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 14px;
  background: rgba(255, 255, 255, 0.84);
  box-shadow: 0 6px 18px rgba(31, 45, 61, 0.04);
}

.effect-item-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.effect-item-head span {
  color: #909399;
  font-size: 12px;
}

.effect-item-head strong {
  color: #303133;
  font-size: 18px;
  white-space: nowrap;
}

.effect-bar {
  height: 7px;
  overflow: hidden;
  border-radius: 999px;
  background: #eef2f7;
}

.effect-bar i {
  display: block;
  height: 100%;
  min-width: 4px;
  border-radius: inherit;
  transition: width 0.2s ease;
}

.effect-item p {
  min-height: 36px;
  margin: 10px 0 0;
  color: #6b7785;
  font-size: 12px;
  line-height: 1.5;
}

.chart-row {
  margin-bottom: 20px;
}

.chart-title {
  font-weight: 600;
  font-size: 15px;
}

.chart-container {
  width: 100%;
  height: 320px;
}

.chart-container-lg {
  width: 100%;
  height: 360px;
}

@media (max-width: 768px) {
  .report-header {
    align-items: flex-start;
  }

  .report-header h2 {
    font-size: 22px;
  }

  .metric-value {
    font-size: 22px;
  }

  .metric-label {
    font-size: 12px;
  }

  .chart-container {
    height: 260px;
  }

  .chart-container-lg {
    height: 280px;
  }

  .effect-summary {
    align-items: flex-start;
  }

  .effect-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 520px) {
  .report-header {
    flex-direction: column;
  }

  .effect-summary {
    flex-direction: column;
  }

  .effect-grid {
    grid-template-columns: 1fr;
  }
}
</style>
