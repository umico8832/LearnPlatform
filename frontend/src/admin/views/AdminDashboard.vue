<template>
  <div class="dashboard-container admin-page">
    <header class="admin-page-header">
      <h1>平台数据总览</h1>
      <div class="dashboard-actions">
        <span v-if="stats" class="update-time">读取于 {{ updateTime }}</span>
        <el-button :icon="Refresh" :loading="loading" @click="loadDashboard">刷新数据</el-button>
      </div>
    </header>
    <LpStatePanel
      :state="loading ? 'loading' : error ? 'error' : stats ? 'ready' : 'loading'"
      title="平台数据暂时无法读取"
      :description="error"
      loading-label="正在读取平台数据"
      @retry="loadDashboard"
    >
      <template v-if="stats">
        <div class="metric-grid">
          <div v-for="metric in metrics" :key="metric.label" class="metric-card">
            <span class="metric-label">{{ metric.label }}</span>
            <strong class="metric-value">{{ metric.value.toLocaleString() }}</strong>
            <span class="metric-note">{{ metric.note }}</span>
          </div>
        </div>
        <div class="chart-grid">
          <section class="dashboard-panel" aria-labelledby="activity-heading">
            <header class="panel-header">
              <div>
                <h2 id="activity-heading">近 7 日平台活跃</h2>
                <p>刷题次数与活跃用户变化</p>
              </div>
              <span>累计 {{ stats.totalPracticeRecords.toLocaleString() }} 次练习</span>
            </header>
            <div v-if="stats.dailyActivity.length" ref="activityChartRef" class="activity-chart" aria-hidden="true" />
            <LpEmptyState v-else title="暂无活跃记录" description="发生练习后，这里将展示每日变化。" />
            <details v-if="stats.dailyActivity.length" class="chart-data">
              <summary>查看每日数据</summary>
              <table>
                <caption class="lp-sr-only">
                  平台每日活跃数据
                </caption>
                <thead>
                  <tr>
                    <th scope="col">日期</th>
                    <th scope="col">刷题次数</th>
                    <th scope="col">活跃用户</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="day in stats.dailyActivity" :key="day.date">
                    <th scope="row">{{ day.date }}</th>
                    <td>{{ day.practiceCount }}</td>
                    <td>{{ day.activeUsers }}</td>
                  </tr>
                </tbody>
              </table>
            </details>
          </section>
          <section class="dashboard-panel" aria-labelledby="question-types-heading">
            <header class="panel-header">
              <div>
                <h2 id="question-types-heading">题型分布</h2>
                <p>当前题库内容结构</p>
              </div>
            </header>
            <div v-if="questionTypes.length" ref="questionChartRef" class="question-chart" aria-hidden="true" />
            <LpEmptyState v-else title="题库暂无题目" />
            <details v-if="questionTypes.length" class="chart-data">
              <summary>查看题型数据</summary>
              <table>
                <caption class="lp-sr-only">
                  题型数量
                </caption>
                <thead>
                  <tr>
                    <th scope="col">题型</th>
                    <th scope="col">题目数</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="[type, count] in questionTypes" :key="type">
                    <th scope="row">{{ type }}</th>
                    <td>{{ count }}</td>
                  </tr>
                </tbody>
              </table>
            </details>
          </section>
        </div>
        <div class="status-grid">
          <section class="dashboard-panel status-panel">
            <div>
              <h2>用户状态</h2>
              <p>
                <strong>{{ stats.enabledUsers }} / {{ stats.totalUsers }}</strong> 位用户启用
              </p>
            </div>
            <el-progress
              :percentage="percentage(stats.enabledUsers, stats.totalUsers)"
              :stroke-width="6"
              :show-text="false"
              aria-hidden="true"
            />
          </section>
          <section class="dashboard-panel status-panel">
            <div>
              <h2>试卷发布</h2>
              <p>
                <strong>{{ stats.publishedExamPapers }} / {{ stats.totalExamPapers }}</strong> 份已发布 ·
                {{ stats.draftExamPapers }} 份草稿
              </p>
            </div>
            <el-progress
              :percentage="percentage(stats.publishedExamPapers, stats.totalExamPapers)"
              :stroke-width="6"
              :show-text="false"
              aria-hidden="true"
            />
          </section>
        </div>
      </template>
    </LpStatePanel>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { LpEmptyState, LpStatePanel } from '@/components/ui'
import { getAdminStatisticsOverview, type AdminStatisticsOverview } from '@/api/statistics'
import { getAuthSessionVersion, isAuthenticated, onAuthSessionChange } from '@/utils/auth'
import { errorMessage } from '@/utils/errors'
import { useReducedMotion } from '@/composables/useReducedMotion'
import { useDashboardCharts } from './useDashboardCharts'

const stats = ref<AdminStatisticsOverview | null>(null)
const loading = ref(false)
const error = ref('')
const updateTime = ref('')
const { reducedMotion } = useReducedMotion()
const { activityChartRef, questionChartRef, renderCharts, disposeCharts } = useDashboardCharts()
let generation = 0
let alive = true
const valid = (version: number, session: number) =>
  alive && version === generation && session === getAuthSessionVersion()
const metrics = computed(() =>
  stats.value
    ? [
        { label: '注册用户', value: stats.value.totalUsers, note: `${stats.value.enabledUsers} 位正常启用` },
        {
          label: '题库总量',
          value: stats.value.totalQuestions,
          note: `近 7 日新增 ${stats.value.weeklyNewQuestions} 道`,
        },
        { label: '试卷总量', value: stats.value.totalExamPapers, note: `${stats.value.publishedExamPapers} 份已发布` },
        { label: '今日活跃', value: stats.value.todayActiveUsers, note: '今日参与刷题的用户' },
      ]
    : [],
)
const questionTypes = computed(() => Object.entries(stats.value?.questionTypeDistribution ?? {}))

async function loadDashboard() {
  if (loading.value) return
  const version = ++generation,
    session = getAuthSessionVersion()
  loading.value = true
  error.value = ''
  disposeCharts()
  try {
    const res = await getAdminStatisticsOverview({ errorDisplay: 'inline' })
    if (!valid(version, session)) return
    if (res.code !== 0 || !res.data) throw new Error(res.message || '平台数据暂时无法读取。')
    stats.value = res.data
    updateTime.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  } catch (cause) {
    if (valid(version, session)) error.value = errorMessage(cause, '平台数据暂时无法读取，请重试。')
  } finally {
    if (valid(version, session)) loading.value = false
  }
  await nextTick()
  if (valid(version, session) && !error.value && stats.value) renderCharts(stats.value, reducedMotion.value)
}
const unsubscribe = onAuthSessionChange(() => {
  generation++
  stats.value = null
  loading.value = false
  error.value = ''
  disposeCharts()
  if (isAuthenticated()) void loadDashboard()
})
watch(reducedMotion, () => {
  if (stats.value && !loading.value && !error.value) renderCharts(stats.value, reducedMotion.value)
})
onMounted(loadDashboard)
onBeforeUnmount(() => {
  alive = false
  generation++
  unsubscribe()
})
function percentage(value: number, total: number) {
  return total > 0 ? Math.round((value * 100) / total) : 0
}
</script>

<style scoped>
.dashboard-actions {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
}
.update-time,
.metric-note,
.panel-header p,
.panel-header > span,
.status-panel p {
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}
.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-bottom: var(--lp-space-6);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  background: var(--lp-surface);
}
.metric-card {
  min-width: 0;
  display: grid;
  gap: var(--lp-space-2);
  padding: var(--lp-space-5);
}
.metric-card + .metric-card {
  border-left: var(--lp-border-hairline);
}
.metric-label {
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}
.metric-value {
  color: var(--lp-text);
  font-size: var(--lp-text-3xl);
  font-weight: var(--lp-weight-semibold);
  line-height: var(--lp-leading-tight);
  font-variant-numeric: tabular-nums;
}
.chart-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(0, 1fr);
  gap: var(--lp-space-5);
  margin-bottom: var(--lp-space-5);
}
.dashboard-panel {
  min-width: 0;
  padding: var(--lp-space-5);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  background: var(--lp-surface);
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: var(--lp-space-3);
}
h2 {
  margin: 0;
  color: var(--lp-text);
  font-size: var(--lp-text-base);
  font-weight: var(--lp-weight-semibold);
}
.panel-header p {
  margin: var(--lp-space-1) 0 0;
}
.activity-chart,
.question-chart {
  height: 280px;
  margin-top: var(--lp-space-4);
}
.chart-data {
  border-top: var(--lp-border-hairline);
  padding-top: var(--lp-space-3);
}
.chart-data summary {
  cursor: pointer;
  color: var(--lp-primary);
  font-size: var(--lp-text-sm);
}
.chart-data table {
  width: 100%;
  margin-top: var(--lp-space-3);
  border-collapse: collapse;
  font-size: var(--lp-text-sm);
  font-variant-numeric: tabular-nums;
}
th,
td {
  padding: var(--lp-space-2);
  border-bottom: var(--lp-border-hairline);
  text-align: right;
}
th:first-child {
  text-align: left;
}
th {
  font-weight: var(--lp-weight-medium);
}
.status-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--lp-space-5);
}
.status-panel strong {
  color: var(--lp-text);
  font-weight: var(--lp-weight-semibold);
  font-variant-numeric: tabular-nums;
}
@media (max-width: 900px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .metric-card:nth-child(3) {
    border-left: 0;
  }
  .chart-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 600px) {
  .status-grid {
    grid-template-columns: 1fr;
  }
  .dashboard-actions {
    flex-wrap: wrap;
  }
}
</style>
