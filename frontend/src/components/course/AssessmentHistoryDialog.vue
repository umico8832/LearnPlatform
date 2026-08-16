<script setup lang="ts">
import { computed } from 'vue'
import type { CourseStageAssessmentSummaryVO } from '@/api/course'
import { formatDateTime } from '@/utils/format'

/**
 * 阶段测评历史弹窗：分页列出已完成的测评，支持按知识点筛选。
 * 行为契约与 CourseOverviewView 保持一致（E2E 依赖弹窗标题、选择器与按钮文案）。
 */
const props = defineProps<{
  visible: boolean
  loading: boolean
  failed: boolean
  records: CourseStageAssessmentSummaryVO[]
  page: number
  pageSize: number
  total: number
  filterKnowledgePointId: number
  knowledgePointOptions: { id: number; title: string }[]
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'filter-change', knowledgePointId: number): void
  (e: 'load', page: number): void
  (e: 'open-detail', assessmentId: number): void
}>()

/** el-dialog 使用 v-model 需要可写代理。 */
const dialogVisible = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
})

function strategyText(strategy: CourseStageAssessmentSummaryVO['selectionStrategy']) {
  return strategy === 'LEARNING_STATE_PRIORITY' ? '按当前学习事实优先选题' : '确定性课程题序'
}

function sourceCompositionText(composition: CourseStageAssessmentSummaryVO['sourceComposition']) {
  if (!composition) return '暂无题源快照'
  return [
    ['官方原题', composition.officialExamCount],
    ['平台人工题', composition.manualCount],
    ['用户私有题', composition.userPrivateCount],
    ['AI 生成题', composition.aiGeneratedCount],
  ]
    .filter(([, count]) => Number(count) > 0)
    .map(([label, count]) => `${label} ${count}`)
    .join(' · ')
}
</script>

<template>
  <el-dialog v-model="dialogVisible" title="阶段测评历史" width="min(680px, 94vw)">
    <div class="history-filter-row">
      <el-select
        :model-value="filterKnowledgePointId"
        placeholder="按知识点筛选"
        clearable
        @change="(value: number | undefined) => emit('filter-change', value ?? 0)"
      >
        <el-option :value="0" label="全部知识点" />
        <el-option v-for="item in knowledgePointOptions" :key="item.id" :value="item.id" :label="item.title" />
      </el-select>
    </div>
    <el-result v-if="failed" icon="error" title="暂时无法读取测评历史" sub-title="请稍后重试。">
      <template #extra><el-button type="primary" @click="emit('load', page)">重新加载</el-button></template>
    </el-result>
    <p v-else-if="!loading && !records.length" class="history-empty">还没有已完成的阶段测评。</p>
    <div v-else v-loading="loading" class="assessment-history-list">
      <article v-for="item in records" :key="item.id" class="assessment-history-item">
        <div>
          <strong>答对 {{ item.correctCount }} / {{ item.questionCount }} 题</strong>
          <p>{{ formatDateTime(item.completeTime) }}</p>
          <small>{{ strategyText(item.selectionStrategy) }}</small>
          <small>范围：{{ item.targetKnowledgePointName || '课程整体' }}</small>
          <small>题源：{{ sourceCompositionText(item.sourceComposition) }}</small>
        </div>
        <el-button @click="emit('open-detail', item.id)">查看复盘</el-button>
      </article>
    </div>
    <el-pagination
      v-if="total > pageSize"
      layout="prev, pager, next"
      :current-page="page"
      :page-size="pageSize"
      :total="total"
      @current-change="(value: number) => emit('load', value)"
    />
  </el-dialog>
</template>

<style scoped>
.history-filter-row {
  margin-bottom: var(--lp-space-3);
}
.history-filter-row :deep(.el-select) {
  width: min(280px, 100%);
}
.assessment-history-list {
  display: grid;
  gap: var(--lp-space-3);
  min-height: 80px;
}
.assessment-history-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lp-space-4);
  padding: var(--lp-space-4);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
  background: var(--lp-surface-soft);
}
.assessment-history-item p,
.history-empty {
  margin: 4px 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}
.assessment-history-item small {
  display: block;
  margin-top: 2px;
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
}
@media (max-width: 767px) {
  .assessment-history-item {
    align-items: stretch;
    flex-direction: column;
  }
  .assessment-history-item .el-button {
    width: 100%;
  }
}
</style>
