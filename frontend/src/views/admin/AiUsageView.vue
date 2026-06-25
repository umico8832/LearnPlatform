<template>
  <div class="ai-usage-container">
    <div class="page-header">
      <h2>AI 调用分析</h2>
      <div class="header-actions">
        <el-select v-model="days" size="default" @change="fetchData" style="width: 140px">
          <el-option label="近 1 天" :value="1" />
          <el-option label="近 7 天" :value="7" />
          <el-option label="近 14 天" :value="14" />
          <el-option label="近 30 天" :value="30" />
          <el-option label="近 90 天" :value="90" />
        </el-select>
        <el-button :icon="Refresh" @click="fetchData" :loading="loading">刷新</el-button>
      </div>
    </div>

    <div v-loading="loading" element-loading-text="加载中...">
      <!-- 顶部统计卡片 -->
      <el-row :gutter="16" class="stat-cards">
        <el-col :xs="12" :sm="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-value primary">{{ overview.totalCalls?.toLocaleString() ?? '-' }}</div>
            <div class="stat-label">总调用次数</div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-value success">{{ overview.successRate ?? '-' }}%</div>
            <div class="stat-label">成功率</div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-value warning">{{ overview.todayCalls?.toLocaleString() ?? '-' }}</div>
            <div class="stat-label">今日调用</div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-value info">{{ formatTokens(overview.totalTokens) }}</div>
            <div class="stat-label">总 Tokens</div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16" class="stat-cards cost-cards">
        <el-col :xs="12" :sm="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-value primary">{{ formatCost(overview.totalCostUsd) }}</div>
            <div class="stat-label">已计成本（USD）</div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-value warning">{{ formatCost(overview.todayCostUsd) }}</div>
            <div class="stat-label">今日成本（USD）</div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16" class="stat-cards secondary">
        <el-col :xs="12" :sm="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-value success">{{ overview.successCalls?.toLocaleString() ?? '-' }}</div>
            <div class="stat-label">成功调用</div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-value danger">{{ overview.failedCalls?.toLocaleString() ?? '-' }}</div>
            <div class="stat-label">失败调用</div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-value info">{{ overview.avgDuration ? overview.avgDuration + 'ms' : '-' }}</div>
            <div class="stat-label">平均耗时</div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-value warning">{{ formatTokens(overview.todayTokens) }}</div>
            <div class="stat-label">今日 Tokens</div>
          </el-card>
        </el-col>
      </el-row>

      <el-card shadow="hover" class="report-card">
        <template #header>
          <div class="report-header">
            <div>
              <span>运营报告</span>
              <span class="report-subtitle">与前一 {{ report.days }} 天周期对比</span>
            </div>
            <el-tag :type="report.alerts.length ? 'warning' : 'success'" effect="light">
              {{ report.alerts.length ? `${report.alerts.length} 项待关注` : '运行平稳' }}
            </el-tag>
          </div>
        </template>
        <el-row :gutter="16" class="report-metrics">
          <el-col :xs="12" :sm="6">
            <div class="report-metric">
              <span>调用量环比</span>
              <strong :class="changeClass(report.changes.callsPercent)">{{ formatChange(report.changes.callsPercent) }}</strong>
            </div>
          </el-col>
          <el-col :xs="12" :sm="6">
            <div class="report-metric">
              <span>Token 环比</span>
              <strong :class="changeClass(report.changes.tokensPercent)">{{ formatChange(report.changes.tokensPercent) }}</strong>
            </div>
          </el-col>
          <el-col :xs="12" :sm="6">
            <div class="report-metric">
              <span>失败率变化</span>
              <strong :class="changeClass(report.changes.failureRatePointChange, true)">{{ formatPointChange(report.changes.failureRatePointChange) }}</strong>
            </div>
          </el-col>
          <el-col :xs="12" :sm="6">
            <div class="report-metric">
              <span>平均耗时环比</span>
              <strong :class="changeClass(report.changes.avgDurationPercent)">{{ formatChange(report.changes.avgDurationPercent) }}</strong>
            </div>
          </el-col>
        </el-row>
        <el-alert
          v-for="alert in report.alerts"
          :key="alert.type"
          :title="alert.message"
          :type="alert.level === 'WARNING' ? 'warning' : 'info'"
          :closable="false"
          show-icon
          class="usage-alert"
        />
        <el-empty v-if="!report.alerts.length" description="当前周期未发现失败率、耗时或调用量异常" :image-size="52" />
      </el-card>

      <!-- 每日调用趋势 -->
      <el-card shadow="hover" class="chart-card">
        <template #header><span>每日调用趋势</span></template>
        <div ref="trendChartRef" class="chart-container"></div>
      </el-card>

      <el-row :gutter="16" class="chart-row">
        <!-- 按功能分布 -->
        <el-col :xs="24" :md="12">
          <el-card shadow="hover" class="chart-card">
            <template #header><span>按功能分布</span></template>
            <div ref="functionChartRef" class="chart-container"></div>
          </el-card>
        </el-col>
        <!-- 按模型分布 -->
        <el-col :xs="24" :md="12">
          <el-card shadow="hover" class="chart-card">
            <template #header><span>按模型分布</span></template>
            <div ref="modelChartRef" class="chart-container"></div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16" class="chart-row">
        <!-- 功能调用详情 -->
        <el-col :xs="24" :md="12">
          <el-card shadow="hover">
            <template #header><span>功能调用详情</span></template>
            <el-table :data="overview.functionStats" stripe size="small" max-height="360">
              <el-table-column prop="functionType" label="功能" min-width="120" />
              <el-table-column prop="count" label="调用次数" width="90" align="right" />
              <el-table-column label="成功率" width="80" align="right">
                <template #default="{ row }">
                  {{ row.count > 0 ? ((row.successCount / row.count) * 100).toFixed(1) : 0 }}%
                </template>
              </el-table-column>
              <el-table-column label="Tokens" width="90" align="right">
                <template #default="{ row }">{{ formatTokens(row.totalTokens) }}</template>
              </el-table-column>
              <el-table-column label="成本(USD)" width="100" align="right">
                <template #default="{ row }">{{ formatCost(row.totalCostUsd) }}</template>
              </el-table-column>
              <el-table-column label="平均耗时" width="90" align="right">
                <template #default="{ row }">{{ row.avgDuration ? row.avgDuration + 'ms' : '-' }}</template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
        <!-- Top 活跃用户 -->
        <el-col :xs="24" :md="12">
          <el-card shadow="hover">
            <template #header><span>Top 活跃用户</span></template>
            <el-table :data="overview.topUsers" stripe size="small" max-height="360">
              <el-table-column label="#" width="50" type="index" align="center" />
              <el-table-column prop="username" label="用户名" min-width="100" />
              <el-table-column prop="callCount" label="调用次数" width="90" align="right" />
              <el-table-column label="Tokens" width="90" align="right">
                <template #default="{ row }">{{ formatTokens(row.totalTokens) }}</template>
              </el-table-column>
              <el-table-column label="成本(USD)" width="100" align="right">
                <template #default="{ row }">{{ formatCost(row.totalCostUsd) }}</template>
              </el-table-column>
              <el-table-column label="平均耗时" width="90" align="right">
                <template #default="{ row }">{{ row.avgDuration ? row.avgDuration + 'ms' : '-' }}</template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
      </el-row>

      <!-- 最近失败调用 -->
      <el-card shadow="hover" class="chart-card" v-if="overview.recentFailures?.length">
        <template #header>
          <div class="failure-header">
            <span>最近失败调用</span>
            <el-tag type="danger" size="small">{{ overview.recentFailures.length }} 条</el-tag>
          </div>
        </template>
        <el-table :data="overview.recentFailures" stripe size="small" max-height="400">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="functionType" label="功能" width="140" />
          <el-table-column prop="model" label="模型" width="140" />
          <el-table-column label="Trace ID" width="110">
            <template #default="{ row }">{{ row.traceId || '-' }}</template>
          </el-table-column>
          <el-table-column label="Prompt 指纹" width="110">
            <template #default="{ row }">{{ shortHash(row.promptHash) }}</template>
          </el-table-column>
          <el-table-column label="模型配置" width="110">
            <template #default="{ row }">{{ shortHash(row.modelConfigVersion) }}</template>
          </el-table-column>
          <el-table-column prop="errorMessage" label="错误信息" min-width="200" show-overflow-tooltip />
          <el-table-column prop="createTime" label="时间" width="170" />
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getAiUsageOverview, getAiUsageReport, type AiUsageOverview, type AiUsageReport } from '@/api/aiUsage'
import * as echarts from 'echarts/core'
import { BarChart, PieChart, LineChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  BarChart,
  PieChart,
  LineChart,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
  CanvasRenderer,
])

const days = ref(30)
const loading = ref(false)
const overview = reactive<AiUsageOverview>({
  totalCalls: 0,
  successCalls: 0,
  failedCalls: 0,
  successRate: 0,
  totalTokens: 0,
  avgDuration: 0,
  todayCalls: 0,
  todayTokens: 0,
  totalCostUsd: 0,
  todayCostUsd: 0,
  functionStats: [],
  modelStats: [],
  dailyTrends: [],
  topUsers: [],
  recentFailures: [],
})
const report = reactive<AiUsageReport>({
  days: 7,
  current: { totalCalls: 0, failedCalls: 0, failureRate: 0, totalTokens: 0, avgDuration: 0, totalCostUsd: null },
  previous: { totalCalls: 0, failedCalls: 0, failureRate: 0, totalTokens: 0, avgDuration: 0, totalCostUsd: null },
  changes: { callsPercent: null, tokensPercent: null, costPercent: null, failureRatePointChange: 0, avgDurationPercent: null },
  alerts: [],
})

const trendChartRef = ref<HTMLElement>()
const functionChartRef = ref<HTMLElement>()
const modelChartRef = ref<HTMLElement>()

let trendChart: echarts.ECharts | null = null
let functionChart: echarts.ECharts | null = null
let modelChart: echarts.ECharts | null = null

function formatTokens(tokens: number | undefined): string {
  if (!tokens) return '0'
  if (tokens >= 1_000_000) return (tokens / 1_000_000).toFixed(1) + 'M'
  if (tokens >= 1_000) return (tokens / 1_000).toFixed(1) + 'K'
  return tokens.toLocaleString()
}

function formatCost(cost: number | null | undefined): string {
  if (cost === null || cost === undefined) return '-'
  return '$' + cost.toFixed(cost < 0.01 ? 6 : 4)
}

function shortHash(hash: string | null | undefined): string {
  return hash ? hash.slice(0, 10) : '-'
}

function formatChange(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—'
  return `${value > 0 ? '+' : ''}${value.toFixed(1)}%`
}

function formatPointChange(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—'
  return `${value > 0 ? '+' : ''}${value.toFixed(1)} 个百分点`
}

function changeClass(value: number | null | undefined, isRiskMetric = false): string {
  if (value === null || value === undefined || value === 0) return 'neutral'
  const isIncrease = value > 0
  return isRiskMetric ? (isIncrease ? 'negative' : 'positive') : (isIncrease ? 'positive' : 'negative')
}

async function fetchData() {
  loading.value = true
  try {
    const [overviewResponse, reportResponse] = await Promise.all([
      getAiUsageOverview(days.value),
      getAiUsageReport(days.value),
    ])
    Object.assign(overview, overviewResponse.data)
    Object.assign(report, reportResponse.data)
    await nextTick()
    renderCharts()
  } catch (e: any) {
    console.error('Failed to fetch AI usage overview', e)
  } finally {
    loading.value = false
  }
}

function renderCharts() {
  renderTrendChart()
  renderFunctionChart()
  renderModelChart()
}

function renderTrendChart() {
  if (!trendChartRef.value) return
  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value)
  }
  const trends = overview.dailyTrends || []
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['成功', '失败', 'Tokens'], bottom: 0 },
    grid: { left: '3%', right: '4%', bottom: '12%', top: '8%', containLabel: true },
    xAxis: {
      type: 'category',
      data: trends.map(t => t.date.slice(5)),
      axisLabel: { rotate: trends.length > 15 ? 45 : 0 },
    },
    yAxis: [
      { type: 'value', name: '调用次数', position: 'left' },
      { type: 'value', name: 'Tokens', position: 'right' },
    ],
    series: [
      {
        name: '成功',
        type: 'bar',
        stack: 'calls',
        data: trends.map(t => t.successCount),
        itemStyle: { color: '#67C23A' },
      },
      {
        name: '失败',
        type: 'bar',
        stack: 'calls',
        data: trends.map(t => t.failedCount),
        itemStyle: { color: '#F56C6C' },
      },
      {
        name: 'Tokens',
        type: 'line',
        yAxisIndex: 1,
        data: trends.map(t => t.totalTokens),
        itemStyle: { color: '#E6A23C' },
        smooth: true,
        lineStyle: { width: 2 },
      },
    ],
  })
}

function renderFunctionChart() {
  if (!functionChartRef.value) return
  if (!functionChart) {
    functionChart = echarts.init(functionChartRef.value)
  }
  const funcs = overview.functionStats || []
  functionChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { orient: 'vertical', right: '5%', top: 'center', type: 'scroll' },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['35%', '50%'],
        avoidLabelOverlap: true,
        label: { show: false },
        data: funcs.map(f => ({ name: f.functionType, value: f.count })),
      },
    ],
  })
}

function renderModelChart() {
  if (!modelChartRef.value) return
  if (!modelChart) {
    modelChart = echarts.init(modelChartRef.value)
  }
  const models = overview.modelStats || []
  modelChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { orient: 'vertical', right: '5%', top: 'center', type: 'scroll' },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['35%', '50%'],
        avoidLabelOverlap: true,
        label: { show: false },
        data: models.map(m => ({ name: m.model, value: m.count })),
      },
    ],
  })
}

function handleResize() {
  trendChart?.resize()
  functionChart?.resize()
  modelChart?.resize()
}

onMounted(() => {
  fetchData()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  functionChart?.dispose()
  modelChart?.dispose()
})
</script>

<style scoped>
.ai-usage-container {
  padding: 4px 0;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0;
  font-size: 20px;
}
.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}
.stat-cards {
  margin-bottom: 16px;
}
.stat-cards.secondary {
  margin-bottom: 20px;
}
.stat-cards.cost-cards {
  margin-top: -4px;
}
.stat-card {
  text-align: center;
  padding: 8px 0;
}
.stat-value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.3;
}
.stat-value.primary { color: #409EFF; }
.stat-value.success { color: #67C23A; }
.stat-value.warning { color: #E6A23C; }
.stat-value.danger { color: #F56C6C; }
.stat-value.info { color: #909399; }
.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}
.chart-card {
  margin-bottom: 16px;
}
.report-card {
  margin-bottom: 16px;
  border-left: 3px solid #409eff;
}
.report-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.report-subtitle {
  margin-left: 8px;
  color: #909399;
  font-size: 13px;
  font-weight: normal;
}
.report-metrics {
  margin-bottom: 12px;
}
.report-metric {
  display: flex;
  min-height: 58px;
  flex-direction: column;
  justify-content: center;
  gap: 5px;
  padding: 0 12px;
  border-left: 1px solid #ebeef5;
}
.report-metric span {
  color: #909399;
  font-size: 13px;
}
.report-metric strong {
  font-size: 18px;
}
.positive { color: #67c23a; }
.negative { color: #f56c6c; }
.neutral { color: #909399; }
.usage-alert + .usage-alert {
  margin-top: 8px;
}
.chart-row {
  margin-bottom: 16px;
}
.chart-container {
  width: 100%;
  height: 320px;
}
.failure-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
@media (max-width: 768px) {
  .stat-value { font-size: 20px; }
  .chart-container { height: 240px; }
  .page-header { flex-direction: column; gap: 8px; align-items: flex-start; }
  .report-metric { border-left: none; padding: 8px 0; }
}
</style>
