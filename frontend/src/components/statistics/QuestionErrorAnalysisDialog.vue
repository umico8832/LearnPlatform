<template>
  <el-dialog
    :model-value="modelValue"
    title="🔍 单题错因分析"
    width="750px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-if="loading" v-loading="true" class="dialog-loading"></div>
    <template v-else-if="data">
      <div class="error-analysis-header">
        <div class="error-analysis-question">{{ data.questionContent }}</div>
        <div class="error-analysis-tags">
          <el-tag size="small">{{ data.questionType }}</el-tag>
          <el-tag v-if="data.difficulty" size="small" type="warning">{{ '⭐'.repeat(data.difficulty) }}</el-tag>
          <el-tag v-if="data.courseName" size="small" type="info">{{ data.courseName }}</el-tag>
          <el-tag v-if="data.knowledgePointName" size="small" type="info">{{ data.knowledgePointName }}</el-tag>
        </div>
      </div>

      <el-row :gutter="16" class="metrics-row">
        <el-col :span="6"><el-statistic title="总作答" :value="data.totalAttempts" /></el-col>
        <el-col :span="6"><el-statistic title="答对" :value="data.correctCount" /></el-col>
        <el-col :span="6"><el-statistic title="答错" :value="data.wrongCount" /></el-col>
        <el-col :span="6">
          <el-statistic title="正确率">
            <template #default>
              <span :style="{ color: rateColor(data.correctRate), fontWeight: 700 }">{{ data.correctRate }}%</span>
            </template>
          </el-statistic>
        </el-col>
      </el-row>

      <el-alert :title="data.trendDescription" :type="trendType" :closable="false" show-icon class="trend-alert" />

      <div v-if="data.currentMasteryLevel !== null && data.currentMasteryLevel !== undefined" class="mastery-row">
        <span>当前掌握程度：</span>
        <el-tag :type="masteryLevelType(data.currentMasteryLevel)" size="small">
          {{ masteryLevelLabel(data.currentMasteryLevel) }}
        </el-tag>
      </div>

      <div class="error-pattern-box">
        <h4>📋 错误模式分析</h4>
        <p>{{ data.errorPattern }}</p>
      </div>

      <div v-if="data.attempts.length" class="attempt-history">
        <h4>📝 作答历史（共 {{ data.attempts.length }} 次）</h4>
        <el-timeline>
          <el-timeline-item
            v-for="(attempt, index) in data.attempts"
            :key="index"
            :type="attempt.isCorrect === 1 ? 'success' : attempt.isCorrect === 0 ? 'danger' : 'info'"
            :timestamp="attempt.createTime ? attempt.createTime.replace('T', ' ') : ''"
            placement="top"
          >
            <el-card shadow="never" body-style="padding: 10px 14px">
              <div class="attempt-row">
                <span>
                  <el-tag :type="attempt.isCorrect === 1 ? 'success' : 'danger'" size="small">
                    {{ attempt.isCorrect === 1 ? '✓ 答对' : '✗ 答错' }}
                  </el-tag>
                  <span v-if="attempt.userAnswer" class="attempt-answer">答案：{{ attempt.userAnswer }}</span>
                </span>
                <span v-if="attempt.answerTime" class="attempt-time">用时 {{ attempt.answerTime }}s</span>
              </div>
            </el-card>
          </el-timeline-item>
        </el-timeline>
      </div>
    </template>
    <el-empty v-else description="暂无数据" />
    <template #footer><el-button @click="emit('update:modelValue', false)">关闭</el-button></template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { QuestionErrorAnalysis } from '@/api/statistics'
import { masteryLevelLabel, masteryLevelType, rateColor } from './diagnosisDisplay'

const props = defineProps<{
  modelValue: boolean
  loading: boolean
  data: QuestionErrorAnalysis | null
}>()

const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const trendType = computed(() => {
  if (props.data?.masteryTrend === 'IMPROVING') return 'success'
  if (props.data?.masteryTrend === 'DECLINING') return 'error'
  return 'info'
})
</script>

<style scoped>
.dialog-loading {
  height: 200px;
}

.error-analysis-header {
  padding: var(--lp-space-3);
  background: var(--lp-surface-soft);
  border-radius: var(--lp-radius-sm);
}

.error-analysis-question {
  font-size: var(--lp-text-base);
  line-height: var(--lp-leading-body);
  color: var(--lp-text);
  margin-bottom: var(--lp-space-2);
}

.error-analysis-tags {
  display: flex;
  gap: var(--lp-space-2);
  flex-wrap: wrap;
}

.metrics-row,
.trend-alert,
.error-pattern-box,
.attempt-history {
  margin-top: var(--lp-space-4);
}

.mastery-row {
  margin-top: var(--lp-space-3);
  font-size: var(--lp-text-sm);
  color: var(--lp-text-secondary);
}

.error-pattern-box {
  padding: var(--lp-space-3) var(--lp-space-4);
  background: var(--lp-warning-soft);
  border-radius: var(--lp-radius-sm);
  border-left: 3px solid var(--lp-warning);
}

.error-pattern-box h4,
.attempt-history h4 {
  margin: 0 0 var(--lp-space-2);
  font-size: var(--lp-text-base);
  color: var(--lp-text);
}

.error-pattern-box p {
  margin: 0;
  font-size: var(--lp-text-sm);
  line-height: var(--lp-leading-relaxed);
  color: var(--lp-text-secondary);
}

.attempt-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.attempt-answer {
  margin-left: var(--lp-space-2);
  font-size: var(--lp-text-sm);
  color: var(--lp-text-secondary);
}

.attempt-time {
  font-size: var(--lp-text-xs);
  color: var(--lp-text-muted);
}
</style>
