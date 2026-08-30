<template>
  <el-card class="advice-card" shadow="hover">
    <template #header>
      <div class="card-header">
        <span>💡 每日学习建议</span>
        <el-button
          type="primary"
          size="small"
          :loading="aiAdviceLoading"
          :disabled="aiAdviceStreaming"
          @click="emit('generate-ai-advice')"
        >
          🤖 AI 个性化建议
        </el-button>
      </div>
    </template>
    <div class="advice-content">
      <p v-for="(line, index) in adviceLines" :key="index">{{ line }}</p>
    </div>
  </el-card>

  <el-card v-if="aiAdviceContent || aiAdviceLoading" class="ai-advice-card" shadow="hover">
    <template #header>
      <div class="card-header">
        <span>🤖 AI 个性化学习建议</span>
        <el-tag v-if="aiAdviceStreaming" type="success" size="small" effect="light">生成中...</el-tag>
        <el-tag v-else-if="aiAdviceContent" type="info" size="small" effect="light">AI 生成</el-tag>
      </div>
    </template>
    <div v-if="aiAdviceLoading && !aiAdviceContent" v-loading="true" class="ai-loading"></div>
    <div v-else class="ai-advice-content">
      <MarkdownRenderer :content="aiAdviceContent" />
    </div>
  </el-card>

  <el-row :gutter="16" class="stat-row">
    <el-col :xs="12" :sm="6">
      <el-card shadow="hover" class="stat-card">
        <div class="stat-value">{{ data.totalPractice }}</div>
        <div class="stat-label">总刷题数</div>
      </el-card>
    </el-col>
    <el-col :xs="12" :sm="6">
      <el-card shadow="hover" class="stat-card">
        <div class="stat-value">{{ data.overallCorrectRate }}%</div>
        <div class="stat-label">总正确率</div>
      </el-card>
    </el-col>
    <el-col :xs="12" :sm="6">
      <el-card shadow="hover" class="stat-card">
        <div class="stat-value">{{ data.activeDaysLast30 }}天</div>
        <div class="stat-label">近30天活跃</div>
      </el-card>
    </el-col>
    <el-col :xs="12" :sm="6">
      <el-card shadow="hover" class="stat-card">
        <div class="stat-value">{{ data.streakDays }}天</div>
        <div class="stat-label">连续刷题</div>
      </el-card>
    </el-col>
  </el-row>

  <el-card v-if="data.weakPoints.length" class="section-card" shadow="hover">
    <template #header><span>📚 知识点薄弱诊断</span></template>
    <el-table :data="data.weakPoints" stripe>
      <el-table-column label="知识点" min-width="160">
        <template #default="{ row }">
          <div>
            <strong>{{ row.knowledgePointName }}</strong>
            <el-tag size="small" type="info" class="course-tag">{{ row.courseName }}</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="正确率" width="120">
        <template #default="{ row }">
          <el-progress
            :percentage="row.correctRate >= 0 ? Math.round(row.correctRate) : 0"
            :color="rateColor(row.correctRate)"
            :stroke-width="18"
            :text-inside="true"
          />
        </template>
      </el-table-column>
      <el-table-column prop="totalAttempts" label="练习数" width="80" align="center" />
      <el-table-column prop="wrongCount" label="错题数" width="80" align="center" />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusType(row.masteryStatus)" size="small">
            {{ statusLabel(row.masteryStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="diagnosis" label="诊断" min-width="260" show-overflow-tooltip />
    </el-table>
  </el-card>

  <el-card v-if="data.learningHabit" class="section-card" shadow="hover">
    <template #header><span>📊 学习习惯分析</span></template>
    <el-row :gutter="24">
      <el-col :xs="24" :sm="12">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="日均刷题">{{ data.learningHabit.avgDailyPractice }} 道</el-descriptions-item>
          <el-descriptions-item label="偏好题型">{{ data.learningHabit.preferredQuestionType }}</el-descriptions-item>
          <el-descriptions-item label="偏好课程">{{ data.learningHabit.preferredCourse }}</el-descriptions-item>
          <el-descriptions-item label="学习频次">
            <el-tag :type="frequencyType" size="small">
              {{ data.learningHabit.frequencyDescription }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </el-col>
      <el-col :xs="24" :sm="12">
        <div class="chart-container">
          <h4>近 7 天刷题趋势</h4>
          <div class="mini-chart">
            <div v-for="(day, index) in data.learningHabit.weeklyTrend" :key="index" class="chart-bar-group">
              <div class="chart-bar-wrapper">
                <div class="chart-bar correct" :style="{ height: barHeight(day.correct) }"></div>
                <div class="chart-bar wrong" :style="{ height: barHeight(day.wrong) }"></div>
              </div>
              <div class="chart-date">{{ day.date.slice(5) }}</div>
              <div class="chart-total">{{ day.total }}</div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { LearningDiagnosis } from '@/api/statistics'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import { rateColor, statusLabel, statusType } from './diagnosisDisplay'

const props = defineProps<{
  data: LearningDiagnosis
  aiAdviceLoading: boolean
  aiAdviceStreaming: boolean
  aiAdviceContent: string
}>()

const emit = defineEmits<{ 'generate-ai-advice': [] }>()

const adviceLines = computed(() => props.data.dailyAdvice.split('\n').filter((line) => line.trim()))
const maxBarValue = computed(() => {
  const max = Math.max(...props.data.learningHabit.weeklyTrend.map((day) => day.total))
  return max || 1
})
const frequencyType = computed(() => {
  if (props.data.learningHabit.frequencyLevel === 'ACTIVE') return 'success'
  if (props.data.learningHabit.frequencyLevel === 'MODERATE') return 'warning'
  return 'danger'
})

function barHeight(value: number): string {
  return Math.max(0, (value / maxBarValue.value) * 100) + 'px'
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.advice-card {
  margin-top: var(--lp-space-5);
}

.advice-content {
  font-size: var(--lp-text-md);
  line-height: var(--lp-leading-relaxed);
  color: var(--lp-text);
}

.advice-content p {
  margin: 0 0 var(--lp-space-1);
}

.ai-loading {
  height: 100px;
}

.stat-row {
  margin-top: var(--lp-space-4);
}

.stat-card {
  text-align: center;
  margin-bottom: var(--lp-space-2);
}

.stat-value {
  font-size: var(--lp-text-4xl);
  font-weight: var(--lp-weight-bold);
  color: var(--lp-primary);
}

.stat-label {
  font-size: var(--lp-text-sm);
  color: var(--lp-text-muted);
  margin-top: var(--lp-space-1);
}

.section-card,
.ai-advice-card {
  margin-top: var(--lp-space-4);
}

.course-tag {
  margin-left: var(--lp-space-2);
}

.chart-container h4 {
  margin: 0 0 var(--lp-space-3);
  font-size: var(--lp-text-base);
  color: var(--lp-text-secondary);
}

.mini-chart {
  display: flex;
  align-items: flex-end;
  justify-content: space-around;
  height: 160px;
  padding: 0 var(--lp-space-2);
}

.chart-bar-group {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
}

.chart-bar-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 2px;
  height: 100px;
}

.chart-bar {
  width: 16px;
  min-height: 2px;
  border-radius: var(--lp-radius-xs) var(--lp-radius-xs) 0 0;
  transition: height var(--lp-duration-slow) var(--lp-ease-out);
}

.chart-bar.correct {
  background: var(--lp-success);
}

.chart-bar.wrong {
  background: var(--lp-danger);
}

.chart-date {
  font-size: var(--lp-text-xs);
  color: var(--lp-text-muted);
  margin-top: var(--lp-space-1);
}

.chart-total {
  font-size: var(--lp-text-xs);
  font-weight: var(--lp-weight-semibold);
  color: var(--lp-text);
}

.ai-advice-card {
  border-left: 3px solid var(--lp-primary);
}

.ai-advice-content {
  font-size: var(--lp-text-base);
  line-height: var(--lp-leading-relaxed);
  color: var(--lp-text);
}

.ai-advice-content :deep(h1),
.ai-advice-content :deep(h2),
.ai-advice-content :deep(h3) {
  margin-top: var(--lp-space-4);
  margin-bottom: var(--lp-space-2);
  color: var(--lp-text);
}

.ai-advice-content :deep(ul),
.ai-advice-content :deep(ol) {
  padding-left: var(--lp-space-5);
}

.ai-advice-content :deep(p) {
  margin: var(--lp-space-2) 0;
}

.ai-advice-content :deep(code) {
  background: var(--lp-surface-soft);
  padding: var(--lp-space-1) var(--lp-space-2);
  border-radius: var(--lp-radius-xs);
  font-size: var(--lp-text-sm);
}

@media (max-width: 768px) {
  .stat-value {
    font-size: var(--lp-text-2xl);
  }

  .mini-chart {
    height: 120px;
  }

  .chart-bar-wrapper {
    height: 70px;
  }

  .chart-bar {
    width: 10px;
  }
}
</style>
