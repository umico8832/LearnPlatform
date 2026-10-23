<template>
  <div class="sql-exec-viewer">
    <!-- SQL 查询展示 -->
    <div class="sql-query-panel">
      <div class="sql-query-header">
        <span class="sql-query-title">SQL 语句</span>
        <span class="sql-step-indicator">{{ currentStepIndex + 1 }} / {{ element.steps.length }}</span>
      </div>
      <div class="sql-query-code">
        <div
          v-for="(line, li) in queryLines"
          :key="li"
          class="sql-line"
          :class="{ 'sql-line--active': isLineInClause(line, currentStep) }"
        >
          {{ line }}
        </div>
      </div>
    </div>

    <!-- 当前步骤信息 -->
    <div class="sql-step-panel">
      <div class="sql-step-clause">
        <span class="sql-step-order">Step {{ currentStepIndex + 1 }}</span>
        <span class="sql-step-clause-name">{{ currentStep.clause }}</span>
      </div>
      <p class="sql-step-desc">{{ currentStep.description }}</p>
    </div>

    <!-- 中间结果预览 -->
    <div v-if="currentStep.resultHeaders && currentStep.resultRows" class="sql-result-panel">
      <div class="sql-result-header">
        <span>中间结果</span>
        <span v-if="currentStep.rowCount !== undefined" class="sql-row-count">{{ currentStep.rowCount }} 行</span>
      </div>
      <div class="sql-result-table-wrap">
        <table class="sql-result-table">
          <thead>
            <tr>
              <th v-for="(h, hi) in currentStep.resultHeaders" :key="hi">{{ h }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, ri) in currentStep.resultRows" :key="ri">
              <td v-for="(cell, ci) in row" :key="ci">{{ cell }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 最终结果 -->
    <div v-if="currentStepIndex === element.steps.length - 1 && element.finalResult" class="sql-final-panel">
      <div class="sql-final-header">最终结果</div>
      <div class="sql-result-table-wrap">
        <table class="sql-result-table sql-result-table--final">
          <thead>
            <tr>
              <th v-for="(h, hi) in element.finalResult.headers" :key="hi">{{ h }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, ri) in element.finalResult.rows" :key="ri">
              <td v-for="(cell, ci) in row" :key="ci">{{ cell }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 控制栏 -->
    <div class="sql-controls">
      <div class="sql-controls-buttons">
        <LtButton aria-label="第一步" size="sm" :disabled="currentStepIndex <= 0" @click="goToStep(0)"
          ><component :is="DArrowLeft" aria-hidden="true"
        /></LtButton>
        <LtButton
          size="sm"
          aria-label="上一步"
          :disabled="currentStepIndex <= 0"
          @click="goToStep(currentStepIndex - 1)"
          ><component :is="ArrowLeft" aria-hidden="true"
        /></LtButton>
        <LtButton
          size="sm"
          variant="primary"
          :aria-label="playing ? '暂停' : '播放'"
          :aria-pressed="playing"
          @click="togglePlay"
          ><component :is="playing ? VideoPause : VideoPlay" aria-hidden="true"
        /></LtButton>
        <LtButton
          size="sm"
          aria-label="下一步"
          :disabled="currentStepIndex >= element.steps.length - 1"
          @click="goToStep(currentStepIndex + 1)"
          ><component :is="ArrowRight" aria-hidden="true"
        /></LtButton>
        <LtButton
          size="sm"
          aria-label="最后一步"
          :disabled="currentStepIndex >= element.steps.length - 1"
          @click="goToStep(element.steps.length - 1)"
          ><component :is="DArrowRight" aria-hidden="true"
        /></LtButton>
      </div>
      <!-- 速度调节 -->
      <div class="sql-speed-control">
        <span class="sql-speed-label">速度</span>
        <input
          type="range"
          v-model.number="speed"
          aria-label="每步停留时间（毫秒）"
          :min="300"
          :max="3000"
          :step="300"
        />
      </div>
      <!-- 进度条 -->
      <div class="sql-progress-bar">
        <button
          v-for="(_step, si) in element.steps"
          :key="si"
          type="button"
          :aria-label="`跳到第 ${si + 1} 步`"
          :aria-current="si === currentStepIndex ? 'step' : undefined"
          class="sql-progress-dot"
          :class="{
            'sql-progress-dot--active': si === currentStepIndex,
            'sql-progress-dot--done': si < currentStepIndex,
          }"
          @click="goToStep(si)"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { LtButton } from '@/components/living'
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import {
  ArrowLeft,
  ArrowRight,
  ChevronsLeft as DArrowLeft,
  ChevronsRight as DArrowRight,
  Pause as VideoPause,
  Play as VideoPlay,
} from '@lucide/vue'
import type { SqlExecutionElement } from '@/api/ai'

const props = defineProps<{
  element: SqlExecutionElement
}>()

const currentStepIndex = ref(0)
const playing = ref(false)
const speed = ref(1500)
let timer: ReturnType<typeof setTimeout> | null = null

const currentStep = computed(() => props.element.steps[currentStepIndex.value])

const queryLines = computed(() => {
  if (!props.element.query) return []
  return props.element.query.split('\n').filter((l) => l.trim().length > 0)
})

function isLineInClause(line: string, step: typeof currentStep.value): boolean {
  if (!step) return false
  const clause = step.clause.trim().toUpperCase()
  const lineUpper = line.trim().toUpperCase()
  // 匹配 SQL 子句关键字：SELECT, FROM, WHERE, JOIN, ON, GROUP BY, HAVING, ORDER BY, LIMIT 等
  const clauseKeywords = [
    'SELECT',
    'FROM',
    'WHERE',
    'JOIN',
    'INNER JOIN',
    'LEFT JOIN',
    'RIGHT JOIN',
    'CROSS JOIN',
    'FULL JOIN',
    'ON',
    'GROUP BY',
    'HAVING',
    'ORDER BY',
    'LIMIT',
    'OFFSET',
    'UNION',
    'INSERT',
    'INTO',
    'VALUES',
    'UPDATE',
    'SET',
    'DELETE',
    'CREATE',
    'ALTER',
    'DROP',
    'INDEX',
    'AS',
  ]
  for (const kw of clauseKeywords) {
    if (clause.startsWith(kw) && lineUpper.startsWith(kw)) return true
  }
  // 额外：如果 clause 是子查询等，尝试部分匹配
  if (lineUpper.includes(clause)) return true
  return false
}

function goToStep(idx: number) {
  if (idx < 0 || idx >= props.element.steps.length) return
  currentStepIndex.value = idx
  if (playing.value) {
    stopAutoPlay()
    startAutoPlay()
  }
}

function togglePlay() {
  if (playing.value) {
    stopAutoPlay()
  } else {
    playing.value = true
    startAutoPlay()
  }
}

function startAutoPlay() {
  timer = setTimeout(() => {
    if (currentStepIndex.value < props.element.steps.length - 1) {
      currentStepIndex.value++
      startAutoPlay()
    } else {
      playing.value = false
    }
  }, speed.value)
}

function stopAutoPlay() {
  playing.value = false
  if (timer) {
    clearTimeout(timer)
    timer = null
  }
}

// 切换内容时重置
watch(
  () => props.element,
  () => {
    currentStepIndex.value = 0
    stopAutoPlay()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  stopAutoPlay()
})
</script>

<style scoped>
.sql-exec-viewer {
  display: flex;
  flex-direction: column;
  gap: var(--lt-space-3);
}

/* SQL 查询面板 */
.sql-query-panel {
  background: var(--lt-code-bg);
  border-radius: var(--lt-space-2);
  overflow: hidden;
}

.sql-query-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--lt-space-2) calc(var(--lt-space-1) * 3.5);
  background: var(--lt-fill-2);
  font-size: var(--lt-text-footnote);
  color: var(--lt-label-2);
}

.sql-query-title {
  font-weight: 600;
}

.sql-step-indicator {
  color: var(--lt-syn-keyword);
  font-weight: 600;
}

.sql-query-code {
  padding: var(--lt-space-3) calc(var(--lt-space-1) * 3.5);
  font-family: var(--lt-font-mono);
  font-size: var(--lt-text-footnote);
  line-height: 1.6;
  overflow-x: auto;
}

.sql-line {
  color: var(--lt-label);
  padding: calc(var(--lt-space-1) * 0.25) calc(var(--lt-space-1) * 1.5);
  border-radius: calc(var(--lt-space-1) * 0.75);
  transition:
    background 0.3s ease,
    color 0.3s ease;
  white-space: pre;
}

.sql-line--active {
  background: var(--lt-fill-2);
  color: var(--lt-syn-key);
  font-weight: 600;
}

/* 步骤面板 */
.sql-step-panel {
  background: var(--lt-accent-soft);
  border: calc(var(--lt-space-1) * 0.25) solid var(--lt-accent-soft);
  border-radius: var(--lt-space-2);
  padding: var(--lt-space-3) calc(var(--lt-space-1) * 3.5);
}

.sql-step-clause {
  display: flex;
  align-items: center;
  gap: calc(var(--lt-space-1) * 2.5);
  margin-bottom: calc(var(--lt-space-1) * 1.5);
}

.sql-step-order {
  display: inline-block;
  background: var(--lt-accent-fill);
  color: var(--lt-on-accent);
  font-size: var(--lt-text-footnote);
  font-weight: 700;
  padding: calc(var(--lt-space-1) * 0.5) var(--lt-space-2);
  border-radius: calc(var(--lt-space-1) * 2.5);
}

.sql-step-clause-name {
  font-size: var(--lt-text-footnote);
  font-weight: 700;
  color: var(--lt-label);
}

.sql-step-desc {
  margin: 0;
  font-size: var(--lt-text-footnote);
  color: var(--lt-label-2);
  line-height: 1.6;
}

/* 结果面板 */
.sql-result-panel {
  border: calc(var(--lt-space-1) * 0.25) solid var(--lt-separator);
  border-radius: var(--lt-space-2);
  overflow: hidden;
}

.sql-result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--lt-space-2) calc(var(--lt-space-1) * 3.5);
  background: var(--lt-well);
  font-size: var(--lt-text-footnote);
  font-weight: 600;
  color: var(--lt-label-2);
}

.sql-row-count {
  font-size: var(--lt-text-footnote);
  color: var(--lt-label-2);
  font-weight: 400;
}

.sql-result-table-wrap {
  overflow-x: auto;
  padding: var(--lt-space-1) 0;
}

.sql-result-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--lt-text-footnote);
}

.sql-result-table th,
.sql-result-table td {
  border: calc(var(--lt-space-1) * 0.25) solid var(--lt-separator);
  padding: calc(var(--lt-space-1) * 1.5) var(--lt-space-3);
  text-align: center;
  white-space: nowrap;
}

.sql-result-table th {
  background: var(--lt-well);
  font-weight: 600;
  color: var(--lt-label-2);
}

.sql-result-table--final th {
  background: var(--lt-green-soft);
  color: var(--lt-green-text);
}

.sql-result-table--final td {
  background: var(--lt-well);
}

/* 最终结果面板 */
.sql-final-panel {
  border: calc(var(--lt-space-1) * 0.5) solid var(--lt-green-text);
  border-radius: var(--lt-space-2);
  overflow: hidden;
}

.sql-final-header {
  padding: var(--lt-space-2) calc(var(--lt-space-1) * 3.5);
  background: var(--lt-green-soft);
  font-size: var(--lt-text-footnote);
  font-weight: 700;
  color: var(--lt-green-text);
}

/* 控制栏 */
.sql-controls {
  display: flex;
  align-items: center;
  gap: var(--lt-space-4);
  flex-wrap: wrap;
  padding: var(--lt-space-2) 0;
}

.sql-controls-buttons {
  display: flex;
  gap: var(--lt-space-1);
}

.sql-speed-control {
  display: flex;
  align-items: center;
  gap: calc(var(--lt-space-1) * 1.5);
}

.sql-speed-label {
  font-size: var(--lt-text-footnote);
  color: var(--lt-label-2);
}

.sql-progress-bar {
  display: flex;
  gap: var(--lt-space-1);
  flex: 1;
  justify-content: center;
}

.sql-progress-dot {
  width: calc(var(--lt-space-1) * 2.5);
  height: calc(var(--lt-space-1) * 2.5);
  border-radius: 50%;
  padding: 0;
  border: 0;
  background: var(--lt-separator);
  cursor: pointer;
  transition:
    background 0.2s,
    transform 0.2s;
}

.sql-progress-dot:hover {
  transform: scale(1.3);
}

.sql-progress-dot--done {
  background: var(--lt-green-text);
}

.sql-progress-dot--active {
  background: var(--lt-accent-fill);
  transform: scale(1.4);
  box-shadow: 0 0 0 calc(var(--lt-space-1) * 0.5) var(--lt-fill-2);
}

/* 响应式 */
@media (max-width: 768px) {
  .sql-controls {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
