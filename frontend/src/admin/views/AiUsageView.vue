<template>
  <div class="ai-usage-container admin-page">
    <header class="admin-page-header">
      <div>
        <p class="admin-page-kicker">AI OPERATIONS</p>
        <h2>AI 调用分析</h2>
        <p class="admin-page-description">
          同时跟踪 AI 调用成本与真实学习行为，用于排查异常、控制成本并观察学习资产价值。
        </p>
      </div>
      <div class="admin-header-actions">
        <el-select v-model="days" size="default" style="width: 140px" @change="fetchData">
          <el-option label="近 1 天" :value="1" />
          <el-option label="近 7 天" :value="7" />
          <el-option label="近 14 天" :value="14" />
          <el-option label="近 30 天" :value="30" />
          <el-option label="近 90 天" :value="90" />
        </el-select>
        <el-button :icon="Refresh" :loading="loading" @click="fetchData">刷新</el-button>
      </div>
    </header>

    <div v-loading="loading" element-loading-text="加载中...">
      <section class="admin-summary-grid usage-summary-grid">
        <el-card v-for="item in usageStats" :key="item.label" shadow="never" class="admin-summary-card">
          <span class="admin-summary-icon" :class="item.className">
            <el-icon><component :is="item.icon" /></el-icon>
          </span>
          <div class="admin-summary-copy">
            <p class="admin-summary-label">{{ item.label }}</p>
            <div class="admin-summary-value">{{ item.value }}</div>
            <div class="admin-summary-note">{{ item.note }}</div>
          </div>
        </el-card>
      </section>

      <AiUsageReportPanel :report="report" :acknowledging-id="acknowledgingId" @acknowledge="handleAcknowledgeAlert" />
      <AiLearningEffectPanel :effect="learningEffect" />

      <el-card shadow="hover" class="chart-card">
        <template #header><span>每日调用趋势</span></template>
        <div ref="trendChartRef" class="chart-container"></div>
      </el-card>

      <el-row :gutter="16" class="chart-row">
        <el-col :xs="24" :md="12">
          <el-card shadow="hover" class="chart-card">
            <template #header><span>按功能分布</span></template>
            <div ref="functionChartRef" class="chart-container"></div>
          </el-card>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-card shadow="hover" class="chart-card">
            <template #header><span>按模型分布</span></template>
            <div ref="modelChartRef" class="chart-container"></div>
          </el-card>
        </el-col>
      </el-row>

      <AiUsageDetails :overview="overview" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { Coin, DataLine, Money, Refresh, SuccessFilled, Timer, TrendCharts, Warning } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  acknowledgeAiUsageAlert,
  getAiLearningEffect,
  getAiUsageOverview,
  getAiUsageReport,
  type AiLearningEffect,
  type AiUsageOverview,
  type AiUsageReport,
} from '@/api/aiUsage'
import { useAiUsageCharts } from '@/composables/useAiUsageCharts'
import { errorMessage } from '@/utils/errors'
import AiLearningEffectPanel from './ai-usage/AiLearningEffectPanel.vue'
import AiUsageDetails from './ai-usage/AiUsageDetails.vue'
import AiUsageReportPanel from './ai-usage/AiUsageReportPanel.vue'
import { formatCost, formatTokens } from './ai-usage/aiUsageDisplay'

const days = ref(30)
const loading = ref(false)
const acknowledgingId = ref<number | null>(null)
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
  changes: {
    callsPercent: null,
    tokensPercent: null,
    costPercent: null,
    failureRatePointChange: 0,
    avgDurationPercent: null,
  },
  alerts: [],
})
const learningEffect = reactive<AiLearningEffect>({
  days: 30,
  periodStart: '',
  periodEnd: '',
  assetViewCount: 0,
  engagedUserCount: 0,
  viewedQuestionCount: 0,
  feedbackCount: 0,
  helpfulRate: null,
  minimumComparisonSample: 5,
  minimumDistinctUsers: 3,
  variantTrainingStartedCount: 0,
  variantTrainingCompletedCount: 0,
  variantTrainingCompletionRate: null,
  variantTrainingAnsweredCount: 0,
  variantTrainingCorrectCount: 0,
  variantTrainingCorrectRate: null,
  variantDifficultyMinimumSample: 5,
  variantDifficultyCoveredCount: 0,
  variantDifficultySufficientCount: 0,
  variantDifficultyReadiness: 'INSUFFICIENT_DATA',
  variantDifficultyConclusion: '暂无结构化变式首次判分样本。',
  variantDifficultyStats: [],
  afterViewPracticeCount: 0,
  afterViewUserCount: 0,
  afterViewCorrectRate: null,
  baselinePracticeCount: 0,
  baselineUserCount: 0,
  baselineCorrectRate: null,
  correctRateLift: null,
  conclusionLevel: 'INSUFFICIENT_DATA',
  conclusion: '当前暂无足够数据。',
  crossQuestionWindowDays: 30,
  crossQuestionAfterViewPracticeCount: 0,
  crossQuestionAfterViewUserCount: 0,
  crossQuestionAfterViewCorrectRate: null,
  crossQuestionBaselinePracticeCount: 0,
  crossQuestionBaselineUserCount: 0,
  crossQuestionBaselineCorrectRate: null,
  crossQuestionCorrectRateLift: null,
  crossQuestionConclusionLevel: 'INSUFFICIENT_DATA',
  crossQuestionConclusion: '当前暂无足够的跨题作答数据。',
  assetTypeStats: [],
})

const usageStats = computed(() => [
  {
    label: '总调用次数',
    value: overview.totalCalls?.toLocaleString() ?? '-',
    note: `今日 ${overview.todayCalls?.toLocaleString() ?? 0} 次`,
    icon: DataLine,
    className: 'is-primary',
  },
  {
    label: '成功率',
    value: `${overview.successRate ?? '-'}%`,
    note: `成功 ${overview.successCalls?.toLocaleString() ?? 0} 次`,
    icon: SuccessFilled,
    className: 'is-success',
  },
  {
    label: '失败调用',
    value: overview.failedCalls?.toLocaleString() ?? '-',
    note: report.alerts.length ? `${report.alerts.length} 项提醒` : '当前周期平稳',
    icon: Warning,
    className: 'is-danger',
  },
  {
    label: '平均耗时',
    value: overview.avgDuration ? `${overview.avgDuration}ms` : '-',
    note: '同步与流式综合',
    icon: Timer,
    className: 'is-info',
  },
  {
    label: '总 Tokens',
    value: formatTokens(overview.totalTokens),
    note: `今日 ${formatTokens(overview.todayTokens)}`,
    icon: Coin,
    className: 'is-warning',
  },
  {
    label: '已计成本',
    value: formatCost(overview.totalCostUsd),
    note: `今日 ${formatCost(overview.todayCostUsd)}`,
    icon: Money,
    className: 'is-primary',
  },
  {
    label: '功能类型',
    value: overview.functionStats?.length ?? 0,
    note: '有调用记录的功能',
    icon: TrendCharts,
    className: 'is-info',
  },
  {
    label: '模型数量',
    value: overview.modelStats?.length ?? 0,
    note: '有调用记录的模型',
    icon: DataLine,
    className: 'is-success',
  },
])

const { trendChartRef, functionChartRef, modelChartRef, renderCharts } = useAiUsageCharts(overview)

async function fetchData() {
  loading.value = true
  try {
    const [overviewResponse, reportResponse, effectResponse] = await Promise.all([
      getAiUsageOverview(days.value),
      getAiUsageReport(days.value),
      getAiLearningEffect(days.value),
    ])
    Object.assign(overview, overviewResponse.data)
    Object.assign(report, reportResponse.data)
    Object.assign(learningEffect, effectResponse.data)
    await nextTick()
    renderCharts()
  } catch (error) {
    console.error('Failed to fetch AI usage overview', error)
  } finally {
    loading.value = false
  }
}

async function handleAcknowledgeAlert(id: number) {
  acknowledgingId.value = id
  try {
    await acknowledgeAiUsageAlert(id)
    ElMessage.success('已确认该提醒')
    await fetchData()
  } catch (error) {
    console.error('Failed to acknowledge AI usage alert', error)
    ElMessage.error(errorMessage(error, '确认提醒失败'))
  } finally {
    acknowledgingId.value = null
  }
}

onMounted(fetchData)
</script>

<style scoped>
.usage-summary-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}
.admin-summary-icon.is-primary {
  color: var(--lp-primary);
  background: var(--lp-primary-soft);
}
.admin-summary-icon.is-success {
  color: var(--lp-success);
  background: #e9f8ef;
}
.admin-summary-icon.is-warning {
  color: var(--lp-warning);
  background: #fff7df;
}
.admin-summary-icon.is-danger {
  color: var(--lp-danger);
  background: #fff0ef;
}
.admin-summary-icon.is-info {
  color: var(--lp-text-secondary);
  background: #eef3f8;
}
.chart-card,
.chart-row {
  margin-bottom: 16px;
}
.chart-container {
  width: 100%;
  height: 320px;
}
@media (max-width: 768px) {
  .chart-container {
    height: 240px;
  }
}
</style>
