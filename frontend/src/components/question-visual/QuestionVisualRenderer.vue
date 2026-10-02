<template>
  <div class="vi-content">
    <div class="vi-header">
      <h3 class="vi-title">{{ data.title }}</h3>
      <p class="vi-summary">{{ data.summary }}</p>
    </div>

    <div v-for="(element, index) in data.elements" :key="index" class="vi-element">
      <div v-if="element.type === 'text'" class="vi-block">
        <div class="vi-block-label">{{ element.label }}</div>
        <MarkdownRenderer :content="element.content" />
      </div>

      <div v-else-if="element.type === 'step_list'" class="vi-block">
        <div class="vi-block-label">{{ element.label }}</div>
        <div class="vi-steps">
          <div
            v-for="(step, stepIndex) in element.steps"
            :key="stepIndex"
            class="vi-step"
            :class="`vi-step--${step.status}`"
          >
            <span class="vi-step-status">{{ stepStatusLabel(step.status) }}</span>
            <div class="vi-step-body">
              <div class="vi-step-content">{{ step.content }}</div>
              <div v-if="step.detail" class="vi-step-detail">{{ step.detail }}</div>
            </div>
          </div>
        </div>
      </div>

      <div v-else-if="element.type === 'table'" class="vi-block">
        <div class="vi-block-label">{{ element.label }}</div>
        <div class="vi-table-wrapper">
          <table class="vi-table">
            <thead>
              <tr>
                <th v-for="(header, headerIndex) in element.headers" :key="headerIndex">{{ header }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, rowIndex) in element.rows" :key="rowIndex">
                <td v-for="(cell, cellIndex) in row" :key="cellIndex">{{ cell }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div v-else-if="element.type === 'state_array'" class="vi-block">
        <div class="vi-block-label">{{ element.label }}</div>
        <div v-if="element.description" class="vi-description">{{ element.description }}</div>
        <div class="vi-state-array">
          <div
            v-for="(cell, cellIndex) in element.cells"
            :key="cellIndex"
            class="vi-cell"
            :class="`vi-cell--${cell.state || 'default'}`"
          >
            <div class="vi-cell-value">{{ cell.value }}</div>
            <div class="vi-cell-index">{{ cell.index }}</div>
            <span v-if="cell.state && cell.state !== 'default'" class="vi-cell-state">{{
              stateLabel(cell.state)
            }}</span>
          </div>
        </div>
      </div>

      <div v-else-if="element.type === 'matrix'" class="vi-block">
        <div class="vi-block-label">{{ element.label }}</div>
        <div v-if="element.description" class="vi-description">{{ element.description }}</div>
        <div class="vi-table-wrapper">
          <table class="vi-matrix">
            <thead>
              <tr>
                <th v-for="(header, headerIndex) in element.headers" :key="headerIndex">{{ header }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, rowIndex) in element.rows" :key="rowIndex">
                <td
                  v-for="(cell, cellIndex) in row"
                  :key="cellIndex"
                  :class="getCellClass(cell)"
                  :aria-label="getCellAriaLabel(cell)"
                >
                  {{ getCellValue(cell) }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <QuestionVisualTree v-else-if="element.type === 'tree'" :element="element" />

      <div v-else-if="element.type === 'bar_chart'" class="vi-block">
        <div class="vi-block-label">{{ element.label }}</div>
        <div class="vi-bar-chart">
          <div v-for="(item, itemIndex) in element.items" :key="itemIndex" class="vi-bar-row">
            <div class="vi-bar-label">{{ item.label }}</div>
            <div class="vi-bar-track">
              <div class="vi-bar-fill" :style="{ width: getBarWidth(element, item.value) + '%' }" />
            </div>
            <div class="vi-bar-value">{{ item.value }}</div>
          </div>
        </div>
      </div>

      <div v-else-if="element.type === 'number_line'" class="vi-block">
        <div class="vi-block-label">{{ element.label }}</div>
        <div class="vi-number-line">
          <div class="vi-nl-track">
            <div
              v-for="(marker, markerIndex) in element.markers"
              :key="markerIndex"
              class="vi-nl-marker"
              :style="{ left: getMarkerPos(element, marker.position) + '%' }"
            >
              <div class="vi-nl-marker-line" />
              <div class="vi-nl-marker-label">{{ marker.label }}</div>
            </div>
            <div class="vi-nl-current" :style="{ left: getMarkerPos(element, element.current) + '%' }">
              <div class="vi-nl-current-dot" />
            </div>
          </div>
          <div class="vi-nl-range">
            <span>{{ element.min }}</span>
            <span>{{ element.max }}</span>
          </div>
        </div>
      </div>

      <div v-else-if="element.type === 'code_animation'" class="vi-block">
        <div class="vi-block-label">{{ element.label }}</div>
        <CodeAnimationViewer :element="element" />
      </div>

      <div v-else-if="element.type === 'sql_execution'" class="vi-block">
        <div class="vi-block-label">{{ element.label }}</div>
        <SqlExecutionViewer :element="element" />
      </div>

      <div v-else-if="element.type === 'network_protocol'" class="vi-block">
        <div class="vi-block-label">{{ element.label }}</div>
        <NetworkProtocolViewer :element="element" />
      </div>

      <div v-else-if="element.type === 'os_process'" class="vi-block">
        <div class="vi-block-label">{{ element.label }}</div>
        <OsProcessViewer :element="element" />
      </div>

      <QuestionVisualMermaid v-else-if="element.type === 'mermaid'" :element="element" />
    </div>
  </div>
</template>

<script setup lang="ts">
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import CodeAnimationViewer from '@/components/CodeAnimationViewer.vue'
import SqlExecutionViewer from '@/components/SqlExecutionViewer.vue'
import NetworkProtocolViewer from '@/components/NetworkProtocolViewer.vue'
import OsProcessViewer from '@/components/OsProcessViewer.vue'
import type {
  VisualBarChartElement,
  VisualElementState,
  VisualInteractiveData,
  VisualMatrixCell,
  VisualNumberLineElement,
} from '@/api/ai'
import QuestionVisualMermaid from './QuestionVisualMermaid.vue'
import QuestionVisualTree from './QuestionVisualTree.vue'

defineProps<{
  data: VisualInteractiveData
}>()

function getBarWidth(element: VisualBarChartElement, value: number): number {
  if (!element.items.length || !Number.isFinite(value)) return 0
  const max = Math.max(...element.items.map((item) => item.value).filter(Number.isFinite), 1)
  return Math.max(0, Math.min(100, Math.round((value / max) * 100)))
}

function stepStatusLabel(status: 'done' | 'current' | 'pending'): string {
  if (status === 'done') return '已完成'
  if (status === 'current') return '当前'
  return '待进行'
}

function getMarkerPos(element: VisualNumberLineElement, position: number): number {
  const range = element.max - element.min
  if (range === 0) return 50
  return Math.round(((position - element.min) / range) * 100)
}

function stateLabel(state?: VisualElementState): string {
  const labels: Partial<Record<VisualElementState, string>> = {
    current: '当前',
    visited: '已访问',
    sorted: '已排序',
    highlight: '重点',
    swapped: '已交换',
    done: '已完成',
    pending: '待处理',
  }
  return labels[state ?? 'default'] ?? ''
}

function getCellClass(cell: string | VisualMatrixCell): string {
  return typeof cell === 'string' ? '' : `vi-cell--${cell.state || 'default'}`
}

function getCellValue(cell: string | VisualMatrixCell): string {
  return typeof cell === 'string' ? cell : cell.value
}

function getCellAriaLabel(cell: string | VisualMatrixCell): string | undefined {
  if (typeof cell === 'string') return undefined
  const label = stateLabel(cell.state)
  return label ? `${cell.value}，${label}` : cell.value
}
</script>

<style scoped>
.vi-content {
  display: grid;
  gap: var(--lp-space-4);
}
.vi-header {
  padding-bottom: var(--lp-space-3);
  border-bottom: var(--lp-border-hairline);
}
.vi-title {
  margin: 0 0 var(--lp-space-1);
  color: var(--lp-text);
  font-size: var(--lp-text-lg);
}
.vi-summary {
  margin: 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  line-height: var(--lp-leading-body);
}
.vi-block {
  padding: var(--lp-space-4);
  background: var(--lp-surface-subtle);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
}
.vi-block-label {
  margin-bottom: var(--lp-space-2);
  color: var(--lp-text);
  font-size: var(--lp-text-sm);
  font-weight: var(--lp-weight-semibold);
}
.vi-description,
.vi-step-detail,
.vi-cell-index,
.vi-cell-state,
.vi-nl-marker-label,
.vi-nl-range {
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-xs);
}
.vi-description {
  margin-bottom: var(--lp-space-2);
}
.vi-steps,
.vi-bar-chart {
  display: grid;
  gap: var(--lp-space-2);
}
.vi-step {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: var(--lp-space-3);
  align-items: start;
  padding: var(--lp-space-2) var(--lp-space-3);
  border-inline-start: 3px solid var(--lp-border-strong);
  border-radius: var(--lp-radius-sm);
}
.vi-step--done {
  border-inline-start-color: var(--lp-success);
  background: var(--lp-success-soft);
}
.vi-step--current {
  border-inline-start-color: var(--lp-primary);
  background: var(--lp-primary-soft);
}
.vi-step--pending {
  background: var(--lp-surface);
}
.vi-step-status {
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-xs);
  font-weight: var(--lp-weight-semibold);
  white-space: nowrap;
}
.vi-step-content,
.vi-cell-value {
  color: var(--lp-text);
  font-size: var(--lp-text-sm);
}
.vi-cell-value {
  font-weight: var(--lp-weight-semibold);
}
.vi-step-detail {
  margin-top: var(--lp-space-1);
}
.vi-table-wrapper {
  overflow-x: auto;
}
.vi-table,
.vi-matrix {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--lp-text-xs);
}
.vi-table th,
.vi-table td,
.vi-matrix th,
.vi-matrix td {
  padding: var(--lp-space-2) var(--lp-space-3);
  border: var(--lp-border-hairline);
  text-align: center;
  white-space: nowrap;
}
.vi-table th,
.vi-matrix th {
  background: var(--lp-surface-inset);
  color: var(--lp-text-secondary);
  font-weight: var(--lp-weight-semibold);
}
.vi-matrix td.vi-cell--visited {
  background: var(--lp-success-soft);
  color: var(--lp-text);
}
.vi-matrix td.vi-cell--current {
  background: var(--lp-primary);
  color: var(--lp-on-primary);
  font-weight: var(--lp-weight-semibold);
}
.vi-matrix td.vi-cell--highlight {
  background: var(--lp-warning-soft);
  color: var(--lp-text);
}
.vi-state-array {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--lp-space-1);
}
.vi-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 40px;
  padding: var(--lp-space-2);
  background: var(--lp-surface);
  border: 2px solid var(--lp-border-strong);
  border-radius: var(--lp-radius-sm);
}
.vi-cell--current {
  background: var(--lp-primary-soft);
  border-color: var(--lp-primary);
}
.vi-cell--visited,
.vi-cell--sorted {
  background: var(--lp-success-soft);
  border-color: var(--lp-success);
}
.vi-cell--highlight {
  background: var(--lp-warning-soft);
  border-color: var(--lp-warning);
}
.vi-cell--swapped {
  background: var(--lp-danger-soft);
  border-color: var(--lp-danger);
}
.vi-cell-index {
  margin-top: 2px;
}
.vi-cell-state {
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-xs);
}
.vi-bar-row {
  display: grid;
  grid-template-columns: minmax(5rem, auto) minmax(0, 1fr) auto;
  gap: var(--lp-space-3);
  align-items: center;
}
.vi-bar-label,
.vi-bar-value {
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-xs);
}
.vi-bar-label {
  text-align: end;
}
.vi-bar-value {
  color: var(--lp-text);
  font-weight: var(--lp-weight-semibold);
}
.vi-bar-track {
  height: 18px;
  overflow: hidden;
  background: var(--lp-surface-inset);
  border-radius: var(--lp-radius-xs);
}
.vi-bar-fill {
  min-width: 2px;
  height: 100%;
  background: var(--lp-primary);
  border-radius: inherit;
}
.vi-number-line {
  padding: var(--lp-space-5) 0 var(--lp-space-2);
}
.vi-nl-track {
  position: relative;
  height: 4px;
  margin: 0 var(--lp-space-3);
  background: var(--lp-border-strong);
  border-radius: var(--lp-radius-full);
}
.vi-nl-marker {
  position: absolute;
  top: -6px;
  transform: translateX(-50%);
}
.vi-nl-marker-line {
  width: 2px;
  height: 16px;
  background: var(--lp-text-muted);
}
.vi-nl-marker-label {
  margin-top: 2px;
  text-align: center;
  white-space: nowrap;
}
.vi-nl-current {
  position: absolute;
  top: -10px;
  transform: translateX(-50%);
}
.vi-nl-current-dot {
  width: 12px;
  height: 12px;
  background: var(--lp-primary);
  border: 2px solid var(--lp-surface);
  border-radius: 50%;
  box-shadow: 0 0 0 2px var(--lp-primary);
}
.vi-nl-range {
  display: flex;
  justify-content: space-between;
  margin-top: var(--lp-space-5);
  padding: 0 var(--lp-space-2);
}
@media (max-width: 640px) {
  .vi-bar-row {
    grid-template-columns: minmax(4rem, auto) minmax(0, 1fr) auto;
    gap: var(--lp-space-2);
  }
}
</style>
