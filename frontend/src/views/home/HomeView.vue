<template>
  <div class="home-container">
    <div class="welcome-section">
      <h2>欢迎回来，{{ userInfo?.nickname || userInfo?.username || '同学' }} 👋</h2>
      <p class="subtitle">今天也要加油学习哦！</p>
    </div>

    <!-- 学习计划进度 -->
    <el-card shadow="hover" class="plan-card" v-loading="planLoading" element-loading-background="rgba(255,255,255,0.8)">
      <div class="plan-header">
        <div class="plan-title">
          <el-icon :size="20" color="#409eff"><Calendar /></el-icon>
          <span>今日学习计划</span>
        </div>
        <el-button type="primary" link @click="showGoalDialog = true">
          <el-icon><Setting /></el-icon> 设置目标
        </el-button>
      </div>
      <div class="plan-content">
        <div class="plan-progress-area">
          <el-progress
            :percentage="plan.progress"
            :stroke-width="18"
            :color="plan.progress >= 100 ? '#67c23a' : '#409eff'"
            :format="(p: number) => p >= 100 ? '✅ 已完成' : p + '%'"
          />
          <div class="plan-detail">
            今日已刷 <strong>{{ plan.todayCount }}</strong> 题 / 目标 <strong>{{ plan.dailyGoal }}</strong> 题
          </div>
        </div>
        <div class="plan-streak">
          <div class="streak-value">{{ plan.streakDays }}</div>
          <div class="streak-label">🔥 连续打卡天数</div>
        </div>
      </div>
      <div v-if="plan.progress >= 100" class="plan-celebrate">
        🎉 恭喜完成今日目标！坚持学习，每天进步一点点！
      </div>
    </el-card>

    <!-- 设置每日目标弹窗 -->
    <el-dialog v-model="showGoalDialog" title="设置每日刷题目标" width="400px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="每日目标">
          <el-input-number v-model="goalInput" :min="1" :max="200" :step="5" />
          <span style="margin-left: 8px; color: #909399;">题/天</span>
        </el-form-item>
        <el-form-item>
          <el-text type="info" size="small">建议根据自身情况设置合理目标，坚持每天完成</el-text>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showGoalDialog = false">取消</el-button>
        <el-button type="primary" :loading="goalSaving" @click="handleSaveGoal">保存</el-button>
      </template>
    </el-dialog>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card" v-loading="statsLoading" element-loading-background="rgba(255,255,255,0.8)">
          <div class="stat-value" style="color: #409eff">{{ stats.totalPractice }}</div>
          <div class="stat-label">总刷题数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card" v-loading="statsLoading" element-loading-background="rgba(255,255,255,0.8)">
          <div class="stat-value" style="color: #67c23a">{{ stats.correctRate }}%</div>
          <div class="stat-label">正确率</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card" v-loading="statsLoading" element-loading-background="rgba(255,255,255,0.8)">
          <div class="stat-value" style="color: #e6a23c">{{ stats.todayPractice }}</div>
          <div class="stat-label">今日刷题</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card" v-loading="statsLoading" element-loading-background="rgba(255,255,255,0.8)">
          <div class="stat-value" style="color: #f56c6c">{{ stats.streakDays }} 天</div>
          <div class="stat-label">连续学习</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <el-row :gutter="16" class="chart-section">
      <el-col :span="14">
        <el-card shadow="hover">
          <template #header><span>近 7 天刷题趋势</span></template>
          <div v-if="!trendEmpty" ref="trendChartRef" class="chart-container"></div>
          <el-empty v-else description="暂无刷题数据" :image-size="100" />
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="hover">
          <template #header><span>课程正确率分布</span></template>
          <div v-if="!courseEmpty" ref="courseChartRef" class="chart-container"></div>
          <el-empty v-else description="暂无课程数据" :image-size="100" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 快捷入口 -->
    <el-row :gutter="16" class="quick-links">
      <el-col :span="6">
        <el-card shadow="hover" class="link-card" @click="$router.push('/practice')">
          <el-icon :size="28" color="#409eff"><Promotion /></el-icon>
          <span>刷题练习</span>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="link-card" @click="$router.push('/wrong-questions')">
          <el-icon :size="28" color="#e6a23c"><WarningFilled /></el-icon>
          <span>错题本 ({{ stats.wrongQuestionCount }})</span>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="link-card" @click="$router.push('/exams')">
          <el-icon :size="28" color="#67c23a"><Trophy /></el-icon>
          <span>考试</span>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="link-card" @click="$router.push('/ai/review')">
          <el-icon :size="28" color="#9b59b6"><MagicStick /></el-icon>
          <span>AI 复习建议</span>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount, computed, nextTick } from 'vue'
import { useUserStore } from '@/stores/user'
import { Promotion, WarningFilled, Trophy, MagicStick, Calendar, Setting } from '@element-plus/icons-vue'
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

const userStore = useUserStore()
const userInfo = computed(() => userStore.userInfo)

const stats = reactive<StatisticsOverview>({
  totalPractice: 0, correctCount: 0, wrongCount: 0, correctRate: 0,
  todayPractice: 0, streakDays: 0, wrongQuestionCount: 0, masteredCount: 0
})

// 学习计划
const plan = reactive<LearningPlanVO>({
  dailyGoal: 20, todayCount: 0, progress: 0, streakDays: 0, lastPracticeDate: null
})
const planLoading = ref(true)
const showGoalDialog = ref(false)
const goalInput = ref(20)
const goalSaving = ref(false)

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

const statsLoading = ref(true)
const trendEmpty = ref(false)
const courseEmpty = ref(false)
const trendChartRef = ref<HTMLElement | null>(null)
const courseChartRef = ref<HTMLElement | null>(null)
let trendChart: ECharts | null = null
let courseChart: ECharts | null = null

onMounted(async () => {
  // 加载学习计划
  try {
    const planRes = await getLearningPlan()
    if (planRes.code === 0 && planRes.data) {
      Object.assign(plan, planRes.data)
      goalInput.value = planRes.data.dailyGoal
    }
  } catch {
    // 学习计划加载失败
  } finally {
    planLoading.value = false
  }

  // 加载统计数据
  try {
    const res = await getStatisticsOverview()
    if (res.code === 0 && res.data) Object.assign(stats, res.data)
  } catch {
    // 统计数据加载失败，保持默认值
  } finally {
    statsLoading.value = false
  }

  // 加载图表数据
  await nextTick()
  await loadTrendChart()
  await loadCourseChart()
})

onBeforeUnmount(() => {
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
      tooltip: { trigger: 'axis' },
      legend: { data: ['答对', '答错'], bottom: 0 },
      grid: { left: 40, right: 20, top: 20, bottom: 40 },
      xAxis: { type: 'category', data: data.map(d => d.date.substring(5)) },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        { name: '答对', type: 'bar', stack: 'total', data: data.map(d => d.correct), itemStyle: { color: '#67c23a' } },
        { name: '答错', type: 'bar', stack: 'total', data: data.map(d => d.wrong), itemStyle: { color: '#f56c6c' } }
      ]
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
      tooltip: { trigger: 'item', formatter: '{b}: {c}%' },
      radar: {
        indicator: data.map(d => ({ name: d.courseName.length > 6 ? d.courseName.substring(0, 6) + '..' : d.courseName, max: 100 })),
        radius: '60%'
      },
      series: [{
        type: 'radar',
        data: [{ value: data.map(d => d.correctRate), name: '正确率', areaStyle: { opacity: 0.2 }, lineStyle: { color: '#409eff' }, itemStyle: { color: '#409eff' } }]
      }]
    })
  } catch {
    courseEmpty.value = true
  }
}
</script>

<style scoped>
.home-container { padding: 24px; }
.welcome-section { margin-bottom: 24px; }
.welcome-section h2 { margin: 0; font-size: 22px; color: #303133; }
.subtitle { color: #909399; margin-top: 4px; }
.stat-cards { margin-bottom: 16px; }
.stat-card { text-align: center; }
.stat-value { font-size: 28px; font-weight: 700; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
.chart-section { margin-bottom: 16px; }
.chart-container { height: 280px; }
.plan-card { margin-bottom: 16px; }
.plan-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.plan-title { display: flex; align-items: center; gap: 8px; font-size: 16px; font-weight: 600; color: #303133; }
.plan-content { display: flex; align-items: center; gap: 32px; }
.plan-progress-area { flex: 1; }
.plan-detail { margin-top: 8px; font-size: 14px; color: #606266; }
.plan-detail strong { color: #409eff; font-size: 16px; }
.plan-streak { text-align: center; min-width: 100px; }
.streak-value { font-size: 36px; font-weight: 700; color: #e6a23c; line-height: 1; }
.streak-label { font-size: 13px; color: #909399; margin-top: 4px; }
.plan-celebrate { margin-top: 12px; padding: 8px 12px; background: #f0f9eb; border-radius: 6px; color: #67c23a; font-size: 14px; text-align: center; }
.quick-links { margin-top: 8px; }
.link-card { display: flex; flex-direction: column; align-items: center; gap: 8px; cursor: pointer; padding: 20px; }
.link-card span { font-size: 14px; color: #606266; }
</style>
