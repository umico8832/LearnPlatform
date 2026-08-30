<template>
  <el-dialog
    :model-value="modelValue"
    title="🔍 相似题推荐"
    width="800px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-if="loading" v-loading="true" class="dialog-loading"></div>
    <template v-else-if="data">
      <div class="similar-source"><strong>原题：</strong>{{ sourceContent }}</div>
      <el-table :data="data.similarQuestions" stripe class="similar-table">
        <el-table-column label="题目内容" min-width="240" show-overflow-tooltip>
          <template #default="{ row }"
            ><span>{{ row.questionContent }}</span></template
          >
        </el-table-column>
        <el-table-column label="相似度" width="100" align="center">
          <template #default="{ row }">
            <el-progress
              :percentage="row.similarityScore"
              :stroke-width="14"
              :text-inside="true"
              :color="similarityColor(row.similarityScore)"
            />
          </template>
        </el-table-column>
        <el-table-column label="相似原因" width="140">
          <template #default="{ row }"
            ><el-tag size="small" type="info">{{ row.reason }}</el-tag></template
          >
        </el-table-column>
        <el-table-column label="题型" width="80" align="center">
          <template #default="{ row }">{{ row.questionType }}</template>
        </el-table-column>
        <el-table-column label="难度" width="80" align="center">
          <template #default="{ row }"
            ><span v-if="row.difficulty">{{ '⭐'.repeat(row.difficulty) }}</span></template
          >
        </el-table-column>
        <el-table-column label="已练过" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.alreadyAttempted ? 'success' : 'info'" size="small">
              {{ row.alreadyAttempted ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </template>
    <el-empty v-else description="暂无相似题目" />
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      <el-button type="primary" :disabled="!data?.similarQuestions?.length" @click="emit('start-practice')">
        开始练习相似题
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import type { SimilarQuestions } from '@/api/statistics'
import { similarityColor } from './diagnosisDisplay'

defineProps<{
  modelValue: boolean
  loading: boolean
  data: SimilarQuestions | null
  sourceContent: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'start-practice': []
}>()
</script>

<style scoped>
.dialog-loading {
  height: 200px;
}

.similar-source {
  padding: var(--lp-space-3);
  background: var(--lp-surface-soft);
  border-radius: var(--lp-radius-sm);
  font-size: var(--lp-text-sm);
  color: var(--lp-text-secondary);
  line-height: var(--lp-leading-body);
}

.similar-table {
  margin-top: var(--lp-space-3);
}
</style>
