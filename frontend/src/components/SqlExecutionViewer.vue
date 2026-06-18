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
        >{{ line }}</div>
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
      <div class="sql-final-header">✅ 最终结果</div>
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
        <el-button size="small" :icon="DArrowLeft" circle :disabled="currentStepIndex <= 0" @click="goToStep(0)" />
        <el-button size="small" :icon="ArrowLeft" circle :disabled="currentStepIndex <= 0" @click="goToStep(currentStepIndex - 1)" />
        <el-button size="small" :type="playing ? 'warning' : 'primary'" :icon="playing ? VideoPause : VideoPlay" circle @click="togglePlay" />
        <el-button size="small" :icon="ArrowRight" circle :disabled="currentStepIndex >= element.steps.length - 1" @click="goToStep(currentStepIndex + 1)" />
        <el-button size="small" :icon="DArrowRight" circle :disabled="currentStepIndex >= element.steps.length - 1" @click="goToStep(element.steps.length - 1)" />
      </div>
      <!-- 速度调节 -->
      <div class="sql-speed-control">
        <span class="sql-speed-label">速度</span>
        <el-slider v-model="speed" :min="300" :max="3000" :step="300" :show-tooltip="false" style="width: 100px;" />
      </div>
      <!-- 进度条 -->
      <div class="sql-progress-bar">
        <div
          v-for="(_step, si) in element.steps"
          :key="si"
          class="sql-progress-dot"
          :class="{ 'sql-progress-dot--active': si === currentStepIndex, 'sql-progress-dot--done': si < currentStepIndex }"
          @click="goToStep(si)"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import {
  ArrowLeft,
  ArrowRight,
  DArrowLeft,
  DArrowRight,
  VideoPause,
  VideoPlay,
} from '@element-plus/icons-vue'
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
  const clauseKeywords = ['SELECT', 'FROM', 'WHERE', 'JOIN', 'INNER JOIN', 'LEFT JOIN', 'RIGHT JOIN', 'CROSS JOIN', 'FULL JOIN', 'ON', 'GROUP BY', 'HAVING', 'ORDER BY', 'LIMIT', 'OFFSET', 'UNION', 'INSERT', 'INTO', 'VALUES', 'UPDATE', 'SET', 'DELETE', 'CREATE', 'ALTER', 'DROP', 'INDEX', 'AS']
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
  gap: 12px;
}

/* SQL 查询面板 */
.sql-query-panel {
  background: #1e1e1e;
  border-radius: 8px;
  overflow: hidden;
}

.sql-query-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 14px;
  background: #2d2d2d;
  font-size: 12px;
  color: #cccccc;
}

.sql-query-title {
  font-weight: 600;
}

.sql-step-indicator {
  color: #569cd6;
  font-weight: 600;
}

.sql-query-code {
  padding: 12px 14px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  overflow-x: auto;
}

.sql-line {
  color: #d4d4d4;
  padding: 1px 6px;
  border-radius: 3px;
  transition: background 0.3s ease, color 0.3s ease;
  white-space: pre;
}

.sql-line--active {
  background: rgba(86, 156, 214, 0.25);
  color: #9cdcfe;
  font-weight: 600;
}

/* 步骤面板 */
.sql-step-panel {
  background: #ecf5ff;
  border: 1px solid #b3d8ff;
  border-radius: 8px;
  padding: 12px 14px;
}

.sql-step-clause {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.sql-step-order {
  display: inline-block;
  background: #409eff;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 10px;
}

.sql-step-clause-name {
  font-size: 15px;
  font-weight: 700;
  color: #303133;
}

.sql-step-desc {
  margin: 0;
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}

/* 结果面板 */
.sql-result-panel {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  overflow: hidden;
}

.sql-result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 14px;
  background: #f5f7fa;
  font-size: 13px;
  font-weight: 600;
  color: #606266;
}

.sql-row-count {
  font-size: 11px;
  color: #909399;
  font-weight: 400;
}

.sql-result-table-wrap {
  overflow-x: auto;
  padding: 4px 0;
}

.sql-result-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.sql-result-table th,
.sql-result-table td {
  border: 1px solid #ebeef5;
  padding: 6px 12px;
  text-align: center;
  white-space: nowrap;
}

.sql-result-table th {
  background: #f5f7fa;
  font-weight: 600;
  color: #606266;
}

.sql-result-table--final th {
  background: #f0f9eb;
  color: #67c23a;
}

.sql-result-table--final td {
  background: #fafafa;
}

/* 最终结果面板 */
.sql-final-panel {
  border: 2px solid #67c23a;
  border-radius: 8px;
  overflow: hidden;
}

.sql-final-header {
  padding: 8px 14px;
  background: #f0f9eb;
  font-size: 13px;
  font-weight: 700;
  color: #67c23a;
}

/* 控制栏 */
.sql-controls {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  padding: 8px 0;
}

.sql-controls-buttons {
  display: flex;
  gap: 4px;
}

.sql-speed-control {
  display: flex;
  align-items: center;
  gap: 6px;
}

.sql-speed-label {
  font-size: 12px;
  color: #909399;
}

.sql-progress-bar {
  display: flex;
  gap: 4px;
  flex: 1;
  justify-content: center;
}

.sql-progress-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #dcdfe6;
  cursor: pointer;
  transition: background 0.2s, transform 0.2s;
}

.sql-progress-dot:hover {
  transform: scale(1.3);
}

.sql-progress-dot--done {
  background: #67c23a;
}

.sql-progress-dot--active {
  background: #409eff;
  transform: scale(1.4);
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.3);
}

/* 响应式 */
@media (max-width: 768px) {
  .sql-controls {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>