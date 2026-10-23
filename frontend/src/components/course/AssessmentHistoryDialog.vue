<script setup lang="ts">
import { computed } from 'vue'
import { LtButton, LtSheet } from '@/components/living'
import type { CourseStageAssessmentSummaryVO } from '@/api/course'
import { formatDateTime } from '@/utils/format'

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
  detailError: string
  detailLoading: boolean
}>()
const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'filter-change', knowledgePointId: number): void
  (e: 'load', page: number): void
  (e: 'open-detail', assessmentId: number): void
}>()
const sheetOpen = computed({ get: () => props.visible, set: (value: boolean) => emit('update:visible', value) })
const hasPreviousPage = computed(() => props.page > 1)
const hasNextPage = computed(() => props.page * props.pageSize < props.total)
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
  <LtSheet v-model:open="sheetOpen" title="阶段测评历史">
    <div class="assessment-history" :aria-busy="loading || detailLoading">
      <label class="history-filter"
        ><span>按知识点筛选</span
        ><select
          :value="filterKnowledgePointId"
          :disabled="loading || detailLoading"
          @change="emit('filter-change', Number(($event.target as HTMLSelectElement).value))"
        >
          <option :value="0">全部知识点</option>
          <option v-for="item in knowledgePointOptions" :key="item.id" :value="item.id">{{ item.title }}</option>
        </select></label
      >
      <p v-if="detailError" class="history-error" role="alert">{{ detailError }}</p>
      <section v-if="failed" class="history-state" role="alert">
        <h3>暂时无法读取测评历史</h3>
        <p>请稍后重试。</p>
        <LtButton variant="primary" @click="emit('load', page)">重新加载</LtButton>
      </section>
      <p v-else-if="!loading && !records.length" class="history-empty">还没有已完成的阶段测评。</p>
      <div v-else class="assessment-history-list" :aria-live="loading ? 'polite' : 'off'">
        <p v-if="loading" class="history-loading">正在读取测评历史…</p>
        <article v-for="item in records" :key="item.id" class="assessment-history-item">
          <div>
            <strong>答对 {{ item.correctCount }} / {{ item.questionCount }} 题</strong>
            <p>{{ formatDateTime(item.completeTime) }}</p>
            <small>{{ strategyText(item.selectionStrategy) }}</small
            ><small>范围：{{ item.targetKnowledgePointName || '课程整体' }}</small
            ><small>题源：{{ sourceCompositionText(item.sourceComposition) }}</small>
          </div>
          <LtButton :disabled="loading || detailLoading" @click="emit('open-detail', item.id)">{{
            detailLoading ? '正在打开…' : '查看复盘'
          }}</LtButton>
        </article>
      </div>
      <nav v-if="total > pageSize" class="history-pagination" aria-label="测评历史分页">
        <LtButton :disabled="loading || detailLoading || !hasPreviousPage" @click="emit('load', page - 1)"
          >上一页</LtButton
        ><span>第 {{ page }} 页</span
        ><LtButton :disabled="loading || detailLoading || !hasNextPage" @click="emit('load', page + 1)"
          >下一页</LtButton
        >
      </nav>
    </div>
  </LtSheet>
</template>
<style scoped>
.assessment-history {
  display: grid;
  gap: var(--lt-space-4);
}
.history-filter {
  display: grid;
  gap: var(--lt-space-2);
  color: var(--lt-label);
  font-size: var(--lt-text-footnote);
  font-weight: 600;
}
.history-filter select {
  min-height: var(--lt-control-lg);
  padding: 0 var(--lt-space-3);
  border: calc(var(--lt-space-1) / 4) solid var(--lt-separator);
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
  color: var(--lt-label);
  font: inherit;
}
.history-error,
.history-state {
  margin: 0;
  padding: var(--lt-space-3);
  border-radius: var(--lt-radius-sm);
  background: var(--lt-red-soft);
  color: var(--lt-red-text);
}
.history-state h3,
.history-state p {
  margin: 0;
}
.history-state p {
  margin-top: var(--lt-space-1);
}
.history-state .lt-button {
  margin-top: var(--lt-space-3);
}
.assessment-history-list {
  display: grid;
  gap: var(--lt-space-3);
}
.history-empty,
.history-loading,
.assessment-history-item p {
  margin: 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.assessment-history-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lt-space-4);
  padding: var(--lt-space-4);
  border-radius: var(--lt-radius-md);
  background: var(--lt-fill-3);
  box-shadow: 0 0 0 calc(var(--lt-space-1) / 4) var(--lt-hairline);
}
.assessment-history-item small {
  display: block;
  margin-top: var(--lt-space-1);
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
}
.history-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--lt-space-2);
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
</style>
