<template>
  <section class="variant-sample-structure">
    <div class="observation-header">
      <div>
        <strong>结构化变式难度样本</strong>
        <p>
          只按服务端首次判分归档；每档至少 {{ effect.variantDifficultyMinimumSample }} 条且覆盖
          {{ effect.minimumDistinctUsers }} 位学习者，至少两个难度档达标后才提示可开始分层观察。
        </p>
      </div>
      <el-tag :type="ready ? 'success' : 'info'" effect="plain">
        {{ ready ? '可开始分层观察' : '样本积累中' }}
      </el-tag>
    </div>
    <div class="variant-sample-summary">
      <span
        >已覆盖 <b>{{ effect.variantDifficultyCoveredCount }}</b> / 5 个难度档</span
      >
      <span
        >达标 <b>{{ effect.variantDifficultySufficientCount }}</b> 个难度档</span
      >
    </div>
    <el-table :data="effect.variantDifficultyStats" stripe size="small" class="variant-difficulty-table">
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
      :title="effect.variantDifficultyConclusion"
      :type="ready ? 'success' : 'info'"
      :closable="false"
      show-icon
      class="variant-difficulty-conclusion"
    />
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AiLearningEffect } from '@/api/aiUsage'
import { formatRate } from './aiUsageDisplay'

const props = defineProps<{ effect: AiLearningEffect }>()
const ready = computed(() => props.effect.variantDifficultyReadiness === 'READY')
</script>

<style scoped>
.variant-sample-structure {
  margin-top: 22px;
  padding-top: 20px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.observation-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.observation-header strong {
  color: var(--lp-text-primary);
  font-size: 15px;
}
.observation-header p {
  margin: 6px 0 0;
  color: var(--lp-text-secondary);
  font-size: 12px;
  line-height: 1.6;
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
@media (max-width: 768px) {
  .observation-header {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
