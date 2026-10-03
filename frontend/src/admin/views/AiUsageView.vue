<template>
  <div class="ai-usage-container admin-page">
    <header class="admin-page-header">
      <div>
        <h1>AI 调用分析</h1>
      </div>
      <div class="admin-header-actions">
        <el-select
          v-model="days"
          aria-label="统计周期"
          :disabled="acknowledgingId !== null"
          size="default"
          style="width: 140px"
          @change="fetchData"
        >
          <el-option label="近 1 天" :value="1" />
          <el-option label="近 7 天" :value="7" />
          <el-option label="近 14 天" :value="14" />
          <el-option label="近 30 天" :value="30" />
          <el-option label="近 90 天" :value="90" />
        </el-select>
        <el-button :icon="Refresh" :loading="loading" :disabled="acknowledgingId !== null" @click="fetchData"
          >刷新</el-button
        >
      </div>
    </header>

    <LpStatePanel
      :state="loading ? 'loading' : loadError ? 'error' : overview ? 'ready' : 'loading'"
      title="AI 调用数据暂时无法读取"
      :description="loadError"
      loading-label="正在读取 AI 调用数据"
      @retry="fetchData"
    >
      <template v-if="overview && report && learningEffect">
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

        <p v-if="actionError" role="alert" class="usage-action-error">{{ actionError }}</p>
        <AiUsageReportPanel
          :report="report"
          :acknowledging-id="acknowledgingId"
          @acknowledge="handleAcknowledgeAlert"
        />

        <el-card shadow="never" class="chart-card">
          <template #header><span>每日调用趋势</span></template>
          <div ref="trendChartRef" class="chart-container" aria-hidden="true"></div>
          <details class="usage-data-table">
            <summary>查看每日调用数据</summary>
            <table>
              <caption class="lp-sr-only">
                每日调用数据
              </caption>
              <thead>
                <tr>
                  <th scope="col">日期</th>
                  <th scope="col">成功</th>
                  <th scope="col">失败</th>
                  <th scope="col">Tokens</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="day in overview.dailyTrends" :key="day.date">
                  <th scope="row">{{ day.date }}</th>
                  <td>{{ day.successCount }}</td>
                  <td>{{ day.failedCount }}</td>
                  <td>{{ day.totalTokens }}</td>
                </tr>
              </tbody>
            </table>
            <p v-if="!overview.dailyTrends.length">当前周期暂无调用记录。</p>
          </details>
        </el-card>

        <el-row :gutter="16" class="chart-row">
          <el-col :xs="24" :md="12">
            <el-card shadow="never" class="chart-card">
              <template #header><span>按功能分布</span></template>
              <div ref="functionChartRef" class="chart-container" aria-hidden="true"></div>
            </el-card>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-card shadow="never" class="chart-card">
              <template #header><span>按模型分布</span></template>
              <div ref="modelChartRef" class="chart-container" aria-hidden="true"></div>
              <details class="usage-data-table">
                <summary>查看模型调用数据</summary>
                <table>
                  <caption class="lp-sr-only">
                    模型调用数据
                  </caption>
                  <thead>
                    <tr>
                      <th scope="col">模型</th>
                      <th scope="col">调用次数</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="model in overview.modelStats" :key="model.model">
                      <th scope="row">{{ model.model }}</th>
                      <td>{{ model.count }}</td>
                    </tr>
                  </tbody>
                </table>
                <p v-if="!overview.modelStats.length">当前周期暂无模型调用。</p>
              </details>
            </el-card>
          </el-col>
        </el-row>

        <AiUsageDetails :overview="overview" />
        <details class="learning-effect-disclosure">
          <summary>学习效果观察</summary>
          <AiLearningEffectPanel :effect="learningEffect" />
        </details>
      </template>
    </LpStatePanel>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
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
import { LpStatePanel } from '@/components/ui'
import { getAuthSessionVersion, isAuthenticated, onAuthSessionChange } from '@/utils/auth'
import { useAiUsageCharts } from '@/composables/useAiUsageCharts'
import { errorMessage } from '@/utils/errors'
import AiLearningEffectPanel from './ai-usage/AiLearningEffectPanel.vue'
import AiUsageDetails from './ai-usage/AiUsageDetails.vue'
import AiUsageReportPanel from './ai-usage/AiUsageReportPanel.vue'
import { formatCost, formatTokens } from './ai-usage/aiUsageDisplay'

const days = ref(30)
const loading = ref(false)
const acknowledgingId = ref<number | null>(null)
const overview = ref<AiUsageOverview | null>(null)
const report = ref<AiUsageReport | null>(null)
const learningEffect = ref<AiLearningEffect | null>(null)
const loadError = ref('')
const actionError = ref('')
let alive = true
let listVersion = 0
let actionVersion = 0
const current = (session: number) => alive && session === getAuthSessionVersion()

const usageStats = computed(() => {
  const data = overview.value
  if (!data || !report.value) return []
  return [
    {
      label: '总调用次数',
      value: data.totalCalls?.toLocaleString() ?? '-',
      note: `今日 ${data.todayCalls?.toLocaleString() ?? 0} 次`,
      icon: DataLine,
      className: 'is-primary',
    },
    {
      label: '成功率',
      value: `${data.successRate ?? '-'}%`,
      note: `成功 ${data.successCalls?.toLocaleString() ?? 0} 次`,
      icon: SuccessFilled,
      className: 'is-success',
    },
    {
      label: '失败调用',
      value: data.failedCalls?.toLocaleString() ?? '-',
      note: report.value.alerts.length ? `${report.value.alerts.length} 项提醒` : '暂无待处理提醒',
      icon: Warning,
      className: 'is-danger',
    },
    {
      label: '平均耗时',
      value: data.avgDuration ? `${data.avgDuration}ms` : '-',
      note: '同步与流式综合',
      icon: Timer,
      className: 'is-info',
    },
    {
      label: '总 Tokens',
      value: formatTokens(data.totalTokens),
      note: `今日 ${formatTokens(data.todayTokens)}`,
      icon: Coin,
      className: 'is-warning',
    },
    {
      label: '已计成本',
      value: formatCost(data.totalCostUsd),
      note: `今日 ${formatCost(data.todayCostUsd)}`,
      icon: Money,
      className: 'is-primary',
    },
    {
      label: '功能类型',
      value: data.functionStats?.length ?? 0,
      note: '有调用记录的功能',
      icon: TrendCharts,
      className: 'is-info',
    },
    {
      label: '模型数量',
      value: data.modelStats?.length ?? 0,
      note: '有调用记录的模型',
      icon: DataLine,
      className: 'is-success',
    },
  ]
})

const { trendChartRef, functionChartRef, modelChartRef, renderCharts, disposeCharts } = useAiUsageCharts(overview)

async function fetchData() {
  const version = ++listVersion,
    session = getAuthSessionVersion(),
    requestedDays = days.value
  loading.value = true
  loadError.value = ''
  disposeCharts()
  try {
    const [overviewResponse, reportResponse, effectResponse] = await Promise.all([
      getAiUsageOverview(requestedDays, { errorDisplay: 'inline' }),
      getAiUsageReport(requestedDays, { errorDisplay: 'inline' }),
      getAiLearningEffect(requestedDays, { errorDisplay: 'inline' }),
    ])
    if (!current(session) || version !== listVersion) return
    if (!overviewResponse.data || !reportResponse.data || !effectResponse.data)
      throw new Error('AI 调用数据暂时无法读取。')
    overview.value = overviewResponse.data
    report.value = reportResponse.data
    learningEffect.value = effectResponse.data
  } catch (cause) {
    if (current(session) && version === listVersion)
      loadError.value = errorMessage(cause, 'AI 调用数据暂时无法读取，请重试。')
  } finally {
    if (current(session) && version === listVersion) loading.value = false
  }
  await nextTick()
  if (current(session) && version === listVersion && !loadError.value) renderCharts()
}
async function handleAcknowledgeAlert(id: number) {
  if (acknowledgingId.value !== null) return
  const version = ++actionVersion,
    session = getAuthSessionVersion()
  acknowledgingId.value = id
  actionError.value = ''
  try {
    await acknowledgeAiUsageAlert(id, { errorDisplay: 'inline' })
    if (!current(session) || version !== actionVersion) return
    ElMessage.success('已确认该提醒')
    await fetchData()
  } catch (cause) {
    if (current(session) && version === actionVersion) actionError.value = errorMessage(cause, '确认提醒失败，请重试。')
  } finally {
    if (current(session) && version === actionVersion) acknowledgingId.value = null
  }
}
const unsubscribe = onAuthSessionChange(() => {
  listVersion++
  actionVersion++
  overview.value = report.value = learningEffect.value = null
  loadError.value = actionError.value = ''
  acknowledgingId.value = null
  loading.value = false
  disposeCharts()
  if (isAuthenticated()) void fetchData()
})
onBeforeUnmount(() => {
  alive = false
  listVersion++
  actionVersion++
  unsubscribe()
})
onMounted(fetchData)
</script>

<style scoped>
.usage-data-table {
  margin-top: var(--lp-space-3);
  padding-top: var(--lp-space-3);
  border-top: var(--lp-border-hairline);
  font-size: var(--lp-text-sm);
}
summary {
  cursor: pointer;
  color: var(--lp-primary);
}
.usage-data-table table {
  width: 100%;
  margin-top: var(--lp-space-3);
  border-collapse: collapse;
  font-variant-numeric: tabular-nums;
}
th,
td {
  padding: var(--lp-space-2);
  text-align: right;
  border-bottom: var(--lp-border-hairline);
  overflow-wrap: anywhere;
}
th:first-child {
  text-align: left;
}
th {
  font-weight: var(--lp-weight-medium);
}
.learning-effect-disclosure {
  margin-top: var(--lp-space-5);
}
.learning-effect-disclosure > summary {
  padding: var(--lp-space-4);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
  background: var(--lp-surface);
}
.learning-effect-disclosure[open] > summary {
  margin-bottom: var(--lp-space-4);
}

.usage-action-error {
  color: var(--lp-danger);
  background: var(--lp-danger-soft);
  padding: var(--lp-space-3);
  border-left: 3px solid var(--lp-danger);
}
.usage-summary-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}
.admin-summary-icon.is-primary {
  color: var(--lp-primary);
  background: var(--lp-primary-soft);
}
.admin-summary-icon.is-success {
  color: var(--lp-success);
  background: var(--lp-success-soft);
}
.admin-summary-icon.is-warning {
  color: var(--lp-warning);
  background: var(--lp-warning-soft);
}
.admin-summary-icon.is-danger {
  color: var(--lp-danger);
  background: var(--lp-danger-soft);
}
.admin-summary-icon.is-info {
  color: var(--lp-text-secondary);
  background: var(--lp-surface-soft);
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
