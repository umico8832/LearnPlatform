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
        <el-select v-model="days" size="default" @change="fetchData" style="width: 140px">
          <el-option label="近 1 天" :value="1" />
          <el-option label="近 7 天" :value="7" />
          <el-option label="近 14 天" :value="14" />
          <el-option label="近 30 天" :value="30" />
          <el-option label="近 90 天" :value="90" />
        </el-select>
        <el-button :icon="Refresh" @click="fetchData" :loading="loading">刷新</el-button>
      </div>
    </header>

    <div v-loading="loading" element-loading-text="加载中...">
      <!-- 顶部统计卡片 -->
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
              <strong :class="changeClass(report.changes.callsPercent)">{{
                formatChange(report.changes.callsPercent)
              }}</strong>
            </div>
          </el-col>
          <el-col :xs="12" :sm="6">
            <div class="report-metric">
              <span>Token 环比</span>
              <strong :class="changeClass(report.changes.tokensPercent)">{{
                formatChange(report.changes.tokensPercent)
              }}</strong>
            </div>
          </el-col>
          <el-col :xs="12" :sm="6">
            <div class="report-metric">
              <span>失败率变化</span>
              <strong :class="changeClass(report.changes.failureRatePointChange, true)">{{
                formatPointChange(report.changes.failureRatePointChange)
              }}</strong>
            </div>
          </el-col>
          <el-col :xs="12" :sm="6">
            <div class="report-metric">
              <span>平均耗时环比</span>
              <strong :class="changeClass(report.changes.avgDurationPercent)">{{
                formatChange(report.changes.avgDurationPercent)
              }}</strong>
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
        >
          <template #default>
            <div class="usage-alert-content">
              <span v-if="alert.periodStart && alert.periodEnd" class="usage-alert-period">
                {{ alert.periodStart }} 至 {{ alert.periodEnd }}
              </span>
              <el-button
                v-if="alert.id && alert.status === 'OPEN'"
                link
                type="primary"
                :loading="acknowledgingId === alert.id"
                @click="handleAcknowledgeAlert(alert.id)"
              >
                确认
              </el-button>
            </div>
          </template>
        </el-alert>
        <el-empty v-if="!report.alerts.length" description="当前周期未发现失败率、耗时或调用量异常" :image-size="52" />
      </el-card>

      <el-card shadow="hover" class="learning-effect-card">
        <template #header>
          <div class="effect-card-header">
            <div>
              <span>AI 学习效果观察</span>
              <span class="report-subtitle">同题记忆与知识点跨题迁移对照</span>
            </div>
            <el-tag :type="effectTagType" effect="light">{{ effectTagLabel }}</el-tag>
          </div>
        </template>

        <div class="effect-context">
          <div>
            <strong>{{ learningEffect.periodStart || '-' }} 至 {{ learningEffect.periodEnd || '-' }}</strong>
            <p>
              只统计用户实际看到已缓存学习资产后的真实作答；方向判断同时要求作答量与独立学习者覆盖，避免少数高频用户主导结论。
            </p>
          </div>
          <div class="effect-coverage">
            <span
              ><b>{{ learningEffect.assetViewCount }}</b> 次查看</span
            >
            <span
              ><b>{{ learningEffect.engagedUserCount }}</b> 位用户</span
            >
            <span
              ><b>{{ learningEffect.viewedQuestionCount }}</b> 道题</span
            >
          </div>
        </div>

        <div class="effect-comparison">
          <div class="effect-group is-after-view">
            <span class="effect-group-label">阅读后同题作答</span>
            <strong>{{ formatRate(learningEffect.afterViewCorrectRate) }}</strong>
            <div class="effect-rate-track">
              <i :style="{ width: rateWidth(learningEffect.afterViewCorrectRate) }"></i>
            </div>
            <small
              >{{ learningEffect.afterViewPracticeCount }} 条作答 ·
              {{ learningEffect.afterViewUserCount }} 位学习者</small
            >
          </div>
          <div class="effect-lift">
            <span>正确率差异</span>
            <strong :class="effectLiftClass">{{ formatLift(learningEffect.correctRateLift) }}</strong>
            <small>阅读后组 − 对照组</small>
          </div>
          <div class="effect-group is-baseline">
            <span class="effect-group-label">未阅读前 / 未阅读作答</span>
            <strong>{{ formatRate(learningEffect.baselineCorrectRate) }}</strong>
            <div class="effect-rate-track">
              <i :style="{ width: rateWidth(learningEffect.baselineCorrectRate) }"></i>
            </div>
            <small
              >{{ learningEffect.baselinePracticeCount }} 条作答 ·
              {{ learningEffect.baselineUserCount }} 位学习者</small
            >
          </div>
        </div>

        <el-alert
          :title="learningEffect.conclusion"
          :type="effectAlertType"
          :closable="false"
          show-icon
          class="effect-conclusion"
        />

        <section class="transfer-observation">
          <div class="transfer-header">
            <div>
              <strong>知识点跨题迁移</strong>
              <p>
                排除原题重答，仅比较共享知识点的另一道题；前后组均限制在相关阅读前后
                {{ learningEffect.crossQuestionWindowDays }} 天，对照组不含更早暴露，且每组至少覆盖
                {{ learningEffect.minimumDistinctUsers }} 位学习者。
              </p>
            </div>
            <el-tag :type="transferTagType" effect="plain">{{ transferTagLabel }}</el-tag>
          </div>

          <div class="effect-comparison is-transfer">
            <div class="effect-group is-after-view">
              <span class="effect-group-label">阅读后跨题作答</span>
              <strong>{{ formatRate(learningEffect.crossQuestionAfterViewCorrectRate) }}</strong>
              <div class="effect-rate-track">
                <i :style="{ width: rateWidth(learningEffect.crossQuestionAfterViewCorrectRate) }"></i>
              </div>
              <small
                >{{ learningEffect.crossQuestionAfterViewPracticeCount }} 条作答 ·
                {{ learningEffect.crossQuestionAfterViewUserCount }} 位学习者</small
              >
            </div>
            <div class="effect-lift">
              <span>正确率差异</span>
              <strong :class="transferLiftClass">{{ formatLift(learningEffect.crossQuestionCorrectRateLift) }}</strong>
              <small>阅读后组 − 阅读前组</small>
            </div>
            <div class="effect-group is-baseline">
              <span class="effect-group-label">首次相关阅读前跨题作答</span>
              <strong>{{ formatRate(learningEffect.crossQuestionBaselineCorrectRate) }}</strong>
              <div class="effect-rate-track">
                <i :style="{ width: rateWidth(learningEffect.crossQuestionBaselineCorrectRate) }"></i>
              </div>
              <small
                >{{ learningEffect.crossQuestionBaselinePracticeCount }} 条作答 ·
                {{ learningEffect.crossQuestionBaselineUserCount }} 位学习者</small
              >
            </div>
          </div>

          <el-alert
            :title="learningEffect.crossQuestionConclusion"
            :type="transferAlertType"
            :closable="false"
            show-icon
            class="effect-conclusion"
          />
        </section>

        <div class="effect-detail-grid">
          <div class="effect-feedback">
            <span>内容反馈</span>
            <strong>{{ formatRate(learningEffect.helpfulRate) }}</strong>
            <small>{{ learningEffect.feedbackCount }} 条反馈中的有帮助占比</small>
          </div>
          <div class="effect-feedback is-variant-training">
            <span>变式训练完成率</span>
            <strong>{{ formatRate(learningEffect.variantTrainingCompletionRate) }}</strong>
            <small
              >{{ learningEffect.variantTrainingCompletedCount }} /
              {{ learningEffect.variantTrainingStartedCount }} 个周期内开始记录</small
            >
            <em>兼容旧版手动确认与新版提交判分</em>
          </div>
          <div class="effect-feedback is-variant-accuracy">
            <span>结构化变式正确率</span>
            <strong>{{ formatRate(learningEffect.variantTrainingCorrectRate) }}</strong>
            <small
              >{{ learningEffect.variantTrainingCorrectCount }} /
              {{ learningEffect.variantTrainingAnsweredCount }} 次首次判分</small
            >
            <em>只统计服务端真实判分</em>
          </div>
          <el-table :data="learningEffect.assetTypeStats" stripe size="small" class="effect-type-table">
            <el-table-column prop="assetTypeLabel" label="资产类型" min-width="120" />
            <el-table-column prop="viewCount" label="查看" width="76" align="right" />
            <el-table-column prop="userCount" label="用户" width="76" align="right" />
            <el-table-column prop="feedbackCount" label="反馈" width="76" align="right" />
            <el-table-column label="有帮助率" width="96" align="right">
              <template #default="{ row }">{{ formatRate(row.helpfulRate) }}</template>
            </el-table-column>
            <template #empty>
              <el-empty description="当前周期暂无学习资产查看数据" :image-size="48" />
            </template>
          </el-table>
        </div>

        <section class="asset-type-observation">
          <div class="transfer-header">
            <div>
              <strong>按资产类型观察同题表现</strong>
              <p>
                分别按每类资产的首次查看时间切分周期内作答；任一组少于 {{ learningEffect.minimumComparisonSample }} 条或
                {{
                  learningEffect.minimumDistinctUsers
                }}
                位学习者时不判断方向。多资产暴露样本可能重叠，因此不做资产排名或自动推荐。
              </p>
            </div>
          </div>
          <el-table :data="learningEffect.assetTypeStats" stripe size="small" class="asset-type-effect-table">
            <el-table-column prop="assetTypeLabel" label="资产类型" min-width="128" />
            <el-table-column label="阅读后同题" min-width="118" align="right">
              <template #default="{ row }">
                <div class="asset-type-effect-value">
                  <strong>{{ formatRate(row.afterViewCorrectRate) }}</strong>
                  <small>{{ row.afterViewPracticeCount }} 条 · {{ row.afterViewUserCount }} 人</small>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="未阅读 / 阅读前" min-width="132" align="right">
              <template #default="{ row }">
                <div class="asset-type-effect-value">
                  <strong>{{ formatRate(row.baselineCorrectRate) }}</strong>
                  <small>{{ row.baselinePracticeCount }} 条 · {{ row.baselineUserCount }} 人</small>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="正确率差异" min-width="116" align="right">
              <template #default="{ row }">
                <strong :class="liftClass(row.correctRateLift)">{{ formatLift(row.correctRateLift) }}</strong>
              </template>
            </el-table-column>
            <el-table-column label="观察状态" min-width="126" align="center">
              <template #default="{ row }">
                <el-tooltip :content="row.conclusion" placement="top">
                  <el-tag :type="assetTypeTagType(row.conclusionLevel)" size="small" effect="light">
                    {{ assetTypeTagLabel(row.conclusionLevel) }}
                  </el-tag>
                </el-tooltip>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="当前周期暂无可分类型观察的数据" :image-size="48" />
            </template>
          </el-table>
        </section>

        <section class="variant-sample-structure">
          <div class="transfer-header">
            <div>
              <strong>结构化变式难度样本</strong>
              <p>
                只按服务端首次判分归档；每档至少 {{ learningEffect.variantDifficultyMinimumSample }} 条且覆盖
                {{ learningEffect.minimumDistinctUsers }} 位学习者，至少两个难度档达标后才提示可开始分层观察。
              </p>
            </div>
            <el-tag :type="variantDifficultyReady ? 'success' : 'info'" effect="plain">
              {{ variantDifficultyReady ? '可开始分层观察' : '样本积累中' }}
            </el-tag>
          </div>
          <div class="variant-sample-summary">
            <span
              >已覆盖 <b>{{ learningEffect.variantDifficultyCoveredCount }}</b> / 5 个难度档</span
            >
            <span
              >达标 <b>{{ learningEffect.variantDifficultySufficientCount }}</b> 个难度档</span
            >
          </div>
          <el-table :data="learningEffect.variantDifficultyStats" stripe size="small" class="variant-difficulty-table">
            <el-table-column label="难度" min-width="120">
              <template #default="{ row }">{{ row.difficulty }} · {{ row.difficultyLabel }}</template>
            </el-table-column>
            <el-table-column prop="answeredCount" label="首次判分" width="96" align="right" />
            <el-table-column prop="answeredUserCount" label="学习者" width="82" align="right" />
            <el-table-column prop="correctCount" label="正确" width="76" align="right" />
            <el-table-column label="正确率" width="96" align="right">
              <template #default="{ row }">{{ formatRate(row.correctRate) }}</template>
            </el-table-column>
            <el-table-column label="样本状态" width="116" align="center">
              <template #default="{ row }">
                <el-tag :type="row.sampleSufficient ? 'success' : 'info'" size="small" effect="light">
                  {{ row.sampleSufficient ? '达到门槛' : '继续积累' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-alert
            :title="learningEffect.variantDifficultyConclusion"
            :type="variantDifficultyReady ? 'success' : 'info'"
            :closable="false"
            show-icon
            class="effect-conclusion variant-difficulty-conclusion"
          />
        </section>
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
import { computed, ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { errorMessage } from '@/utils/errors'
import { Coin, DataLine, Money, Refresh, Timer, TrendCharts, Warning, SuccessFilled } from '@element-plus/icons-vue'
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
import * as echarts from 'echarts/core'
import { BarChart, PieChart, LineChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
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

const effectTagLabel = computed(
  () =>
    ({
      INSUFFICIENT_DATA: '样本积累中',
      POSITIVE_ASSOCIATION: '正向关联',
      NO_CLEAR_DIFFERENCE: '差异不明确',
      NEEDS_ATTENTION: '需要关注',
    })[learningEffect.conclusionLevel],
)

const effectTagType = computed(
  () =>
    ({
      INSUFFICIENT_DATA: 'info',
      POSITIVE_ASSOCIATION: 'success',
      NO_CLEAR_DIFFERENCE: 'info',
      NEEDS_ATTENTION: 'warning',
    })[learningEffect.conclusionLevel] as 'info' | 'success' | 'warning',
)

const effectAlertType = computed(() =>
  learningEffect.conclusionLevel === 'NEEDS_ATTENTION'
    ? 'warning'
    : learningEffect.conclusionLevel === 'POSITIVE_ASSOCIATION'
      ? 'success'
      : 'info',
)

const effectLiftClass = computed(() => {
  if (learningEffect.correctRateLift === null || learningEffect.correctRateLift === 0) return 'neutral'
  return learningEffect.correctRateLift > 0 ? 'positive' : 'negative'
})

const transferTagLabel = computed(
  () =>
    ({
      INSUFFICIENT_DATA: '跨题样本积累中',
      POSITIVE_ASSOCIATION: '跨题正向关联',
      NO_CLEAR_DIFFERENCE: '跨题差异不明确',
      NEEDS_ATTENTION: '跨题效果需关注',
    })[learningEffect.crossQuestionConclusionLevel],
)

const transferTagType = computed(
  () =>
    ({
      INSUFFICIENT_DATA: 'info',
      POSITIVE_ASSOCIATION: 'success',
      NO_CLEAR_DIFFERENCE: 'info',
      NEEDS_ATTENTION: 'warning',
    })[learningEffect.crossQuestionConclusionLevel] as 'info' | 'success' | 'warning',
)

const transferAlertType = computed(() =>
  learningEffect.crossQuestionConclusionLevel === 'NEEDS_ATTENTION'
    ? 'warning'
    : learningEffect.crossQuestionConclusionLevel === 'POSITIVE_ASSOCIATION'
      ? 'success'
      : 'info',
)

const transferLiftClass = computed(() => {
  if (learningEffect.crossQuestionCorrectRateLift === null || learningEffect.crossQuestionCorrectRateLift === 0)
    return 'neutral'
  return learningEffect.crossQuestionCorrectRateLift > 0 ? 'positive' : 'negative'
})

const variantDifficultyReady = computed(() => learningEffect.variantDifficultyReadiness === 'READY')

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

function formatRate(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : `${value.toFixed(1)}%`
}

function formatLift(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—'
  return `${value > 0 ? '+' : ''}${value.toFixed(1)} 个百分点`
}

function rateWidth(value: number | null | undefined): string {
  if (value === null || value === undefined) return '0%'
  return `${Math.max(0, Math.min(100, value))}%`
}

function liftClass(value: number | null | undefined): string {
  if (value === null || value === undefined || value === 0) return 'neutral'
  return value > 0 ? 'positive' : 'negative'
}

function assetTypeTagLabel(level: AiLearningEffect['conclusionLevel']): string {
  return {
    INSUFFICIENT_DATA: '样本积累中',
    POSITIVE_ASSOCIATION: '正向关联',
    NO_CLEAR_DIFFERENCE: '差异不明确',
    NEEDS_ATTENTION: '需要关注',
  }[level]
}

function assetTypeTagType(level: AiLearningEffect['conclusionLevel']): 'info' | 'success' | 'warning' {
  if (level === 'POSITIVE_ASSOCIATION') return 'success'
  if (level === 'NEEDS_ATTENTION') return 'warning'
  return 'info'
}

function changeClass(value: number | null | undefined, isRiskMetric = false): string {
  if (value === null || value === undefined || value === 0) return 'neutral'
  const isIncrease = value > 0
  return isRiskMetric ? (isIncrease ? 'negative' : 'positive') : isIncrease ? 'positive' : 'negative'
}

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
  } catch (e) {
    console.error('Failed to fetch AI usage overview', e)
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
  } catch (e) {
    console.error('Failed to acknowledge AI usage alert', e)
    ElMessage.error(errorMessage(e, '确认提醒失败'))
  } finally {
    acknowledgingId.value = null
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
      data: trends.map((t) => t.date.slice(5)),
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
        data: trends.map((t) => t.successCount),
        itemStyle: { color: '#67C23A' },
      },
      {
        name: '失败',
        type: 'bar',
        stack: 'calls',
        data: trends.map((t) => t.failedCount),
        itemStyle: { color: '#F56C6C' },
      },
      {
        name: 'Tokens',
        type: 'line',
        yAxisIndex: 1,
        data: trends.map((t) => t.totalTokens),
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
        data: funcs.map((f) => ({ name: f.functionType, value: f.count })),
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
        data: models.map((m) => ({ name: m.model, value: m.count })),
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
.chart-card {
  margin-bottom: 16px;
}
.report-card {
  margin-bottom: 16px;
  border-left: 3px solid #409eff;
}
.learning-effect-card {
  margin-bottom: 16px;
  border-left: 3px solid var(--lp-success);
}
.effect-card-header,
.effect-context,
.effect-comparison,
.effect-detail-grid {
  display: flex;
  align-items: center;
}
.effect-card-header,
.effect-context {
  justify-content: space-between;
  gap: 20px;
}
.effect-context {
  padding: 4px 2px 18px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.effect-context strong {
  color: var(--lp-text-primary);
  font-size: 14px;
}
.effect-context p {
  max-width: 720px;
  margin: 6px 0 0;
  color: var(--lp-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}
.effect-coverage {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}
.effect-coverage span {
  padding: 8px 10px;
  border: 1px solid #dbe7e0;
  border-radius: 8px;
  background: #f4faf6;
  color: var(--lp-text-secondary);
  font-size: 12px;
}
.effect-coverage b {
  color: #25794d;
  font-size: 15px;
}
.effect-comparison {
  justify-content: center;
  gap: 28px;
  padding: 24px 0;
}
.effect-group {
  width: min(300px, 32%);
  padding: 18px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  background: #fafcfe;
}
.effect-group.is-after-view {
  border-color: #cce8d7;
  background: #f3fbf6;
}
.effect-group-label,
.effect-group small,
.effect-lift span,
.effect-lift small,
.effect-feedback span,
.effect-feedback small {
  display: block;
  color: var(--lp-text-secondary);
  font-size: 12px;
}
.effect-group strong {
  display: block;
  margin: 8px 0 10px;
  color: var(--lp-text-primary);
  font-size: 28px;
  line-height: 1;
}
.effect-rate-track {
  height: 7px;
  margin-bottom: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: #e9eef4;
}
.effect-rate-track i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #8aa1b8;
}
.is-after-view .effect-rate-track i {
  background: var(--lp-success);
}
.effect-lift {
  min-width: 150px;
  text-align: center;
}
.effect-lift strong {
  display: block;
  margin: 8px 0;
  font-size: 18px;
}
.effect-conclusion {
  margin-bottom: 18px;
}
.transfer-observation {
  margin-bottom: 20px;
  padding-top: 20px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.transfer-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.transfer-header strong {
  color: var(--lp-text-primary);
  font-size: 15px;
}
.transfer-header p {
  margin: 6px 0 0;
  color: var(--lp-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}
.effect-comparison.is-transfer {
  padding-top: 18px;
}
.effect-detail-grid {
  align-items: stretch;
  gap: 16px;
}
.effect-feedback {
  display: flex;
  width: 180px;
  flex: 0 0 180px;
  flex-direction: column;
  justify-content: center;
  padding: 20px;
  border-radius: 10px;
  background: var(--lp-primary-soft);
}
.effect-feedback strong {
  margin: 10px 0 8px;
  color: var(--lp-primary);
  font-size: 28px;
}
.effect-feedback.is-variant-training {
  background: #f3faf5;
}
.effect-feedback.is-variant-training strong {
  color: var(--lp-success);
}
.effect-feedback.is-variant-accuracy {
  background: #fff7e8;
}
.effect-feedback.is-variant-accuracy strong {
  color: #a96812;
}
.effect-feedback em {
  margin-top: 10px;
  color: var(--lp-text-tertiary);
  font-size: 11px;
  font-style: normal;
  line-height: 1.4;
}
.effect-type-table {
  min-width: 0;
  flex: 1;
}
.asset-type-observation {
  margin-top: 22px;
  padding-top: 20px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.asset-type-effect-table {
  margin-top: 14px;
}
.asset-type-effect-value {
  display: inline-flex;
  align-items: baseline;
  justify-content: flex-end;
  gap: 6px;
}
.asset-type-effect-value strong {
  color: var(--lp-text-primary);
}
.asset-type-effect-value small {
  color: var(--lp-text-tertiary);
  font-size: 11px;
}
.variant-sample-structure {
  margin-top: 22px;
  padding-top: 20px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.variant-sample-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 14px 0;
}
.variant-sample-summary span {
  padding: 7px 10px;
  border-radius: 8px;
  background: #f4f7fa;
  color: var(--lp-text-secondary);
  font-size: 12px;
}
.variant-sample-summary b {
  color: var(--lp-text-primary);
  font-size: 14px;
}
.variant-difficulty-table {
  margin-bottom: 14px;
}
.variant-difficulty-conclusion {
  margin-bottom: 0;
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
.positive {
  color: #67c23a;
}
.negative {
  color: #f56c6c;
}
.neutral {
  color: #909399;
}
.usage-alert + .usage-alert {
  margin-top: 8px;
}
.usage-alert-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}
.usage-alert-period {
  color: #909399;
  font-size: 12px;
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
  .chart-container {
    height: 240px;
  }
  .report-metric {
    border-left: none;
    padding: 8px 0;
  }
  .effect-card-header,
  .effect-context,
  .effect-comparison,
  .effect-detail-grid,
  .transfer-header {
    align-items: stretch;
    flex-direction: column;
  }
  .effect-coverage {
    flex-wrap: wrap;
  }
  .effect-group,
  .effect-feedback {
    width: auto;
  }
  .effect-lift {
    min-width: 0;
  }
}
</style>
