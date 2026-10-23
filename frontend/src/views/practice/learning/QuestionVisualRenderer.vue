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

      <QuestionVisualMermaidLazy v-else-if="element.type === 'mermaid'" :element="element" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { defineComponent, h, onBeforeUnmount, onMounted, ref, shallowRef, type Component, type PropType } from 'vue'
import MarkdownRenderer from '@/views/question/QuestionProse.vue'
import CodeAnimationViewer from './CodeAnimationViewer.vue'
import SqlExecutionViewer from './SqlExecutionViewer.vue'
import NetworkProtocolViewer from './NetworkProtocolViewer.vue'
import OsProcessViewer from './OsProcessViewer.vue'
import type {
  VisualBarChartElement,
  VisualElementState,
  VisualInteractiveData,
  VisualMatrixCell,
  VisualMermaidElement,
  VisualNumberLineElement,
} from '@/api/ai'
import QuestionVisualTree from './QuestionVisualTree.vue'

const QuestionVisualMermaidLazy = defineComponent({
  name: 'QuestionVisualMermaidLazy',
  props: {
    element: { type: Object as PropType<VisualMermaidElement>, required: true },
  },
  setup(props) {
    const component = shallowRef<Component | null>(null)
    const loading = ref(true)
    const error = ref('')
    const canRetry = ref(true)
    let alive = true
    let generation = 0

    async function load() {
      const current = ++generation
      loading.value = true
      error.value = ''
      try {
        // Browsers cache a rejected ESM import. The retry query creates a distinct,
        // Vite-built module identity for one in-place recovery attempt.
        const module =
          current === 1
            ? await import('./QuestionVisualMermaid.vue')
            : await import('./QuestionVisualMermaid.vue?retry=1')
        if (!alive || current !== generation) return
        component.value = module.default
      } catch {
        if (alive && current === generation) {
          canRetry.value = current === 1
          error.value =
            current === 1
              ? '图形渲染器暂时无法加载，已保留源代码。'
              : '图形暂时无法加载，已保留源代码；刷新页面后可再试。'
        }
      } finally {
        if (alive && current === generation) loading.value = false
      }
    }

    onMounted(() => void load())
    onBeforeUnmount(() => {
      alive = false
      generation++
    })

    return () => {
      if (component.value) return h(component.value, { element: props.element })
      if (loading.value)
        return h('div', { class: 'vi-block vi-mermaid-load-state', role: 'status' }, '正在加载图形渲染器…')
      return h('div', { class: 'vi-block vi-mermaid-load-error', role: 'alert' }, [
        h('div', { class: 'vi-block-label' }, props.element.label),
        h('p', error.value),
        h('pre', { class: 'vi-mermaid-source' }, props.element.code),
        canRetry.value ? h('button', { type: 'button', onClick: load }, '重试加载') : null,
      ])
    }
  },
})

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
  gap: var(--lt-space-4);
}
.vi-header {
  padding-bottom: var(--lt-space-3);
  border-bottom: thin solid var(--lt-separator);
}
.vi-title {
  margin: 0 0 var(--lt-space-1);
  color: var(--lt-label);
  font-size: var(--lt-text-title3);
}
.vi-summary {
  margin: 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
  line-height: var(--lt-leading-ui);
}
.vi-block {
  padding: var(--lt-space-4);
  background: var(--lt-well);
  border: thin solid var(--lt-separator);
  border-radius: var(--lt-radius-md);
}
.vi-block-label {
  margin-bottom: var(--lt-space-2);
  color: var(--lt-label);
  font-size: var(--lt-text-footnote);
  font-weight: 600;
}
.vi-mermaid-load-state,
.vi-mermaid-load-error {
  display: grid;
  gap: var(--lt-space-2);
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.vi-mermaid-load-error p {
  margin: 0;
}
.vi-mermaid-source {
  margin: 0;
  padding: var(--lt-space-3);
  overflow-x: auto;
  border: thin solid var(--lt-separator);
  border-radius: var(--lt-radius-sm);
  background: var(--lt-well);
  color: var(--lt-label);
  font-family: var(--lt-font-mono);
  font-size: var(--lt-text-caption);
  line-height: var(--lt-leading-ui);
  white-space: pre-wrap;
}
.vi-mermaid-load-error button {
  justify-self: start;
  min-height: var(--lt-control-sm);
  padding: 0 var(--lt-space-3);
  border: calc(var(--lt-space-1) * 0.25) solid var(--lt-separator);
  border-radius: var(--lt-radius-pill);
  background: var(--lt-surface);
  color: var(--lt-accent);
  font: inherit;
  cursor: pointer;
}
.vi-description,
.vi-step-detail,
.vi-cell-index,
.vi-cell-state,
.vi-nl-marker-label,
.vi-nl-range {
  color: var(--lt-label-2);
  font-size: var(--lt-text-caption);
}
.vi-description {
  margin-bottom: var(--lt-space-2);
}
.vi-steps,
.vi-bar-chart {
  display: grid;
  gap: var(--lt-space-2);
}
.vi-step {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: var(--lt-space-3);
  align-items: start;
  padding: var(--lt-space-2) var(--lt-space-3);
  border-inline-start: calc(var(--lt-space-1) * 0.75) solid var(--lt-separator);
  border-radius: var(--lt-radius-sm);
}
.vi-step--done {
  border-inline-start-color: var(--lt-green-text);
  background: var(--lt-green-soft);
}
.vi-step--current {
  border-inline-start-color: var(--lt-accent);
  background: var(--lt-accent-soft);
}
.vi-step--pending {
  background: var(--lt-surface);
}
.vi-step-status {
  color: var(--lt-label-2);
  font-size: var(--lt-text-caption);
  font-weight: 600;
  white-space: nowrap;
}
.vi-step-content,
.vi-cell-value {
  color: var(--lt-label);
  font-size: var(--lt-text-footnote);
}
.vi-cell-value {
  font-weight: 600;
}
.vi-step-detail {
  margin-top: var(--lt-space-1);
}
.vi-table-wrapper {
  overflow-x: auto;
}
.vi-table,
.vi-matrix {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--lt-text-caption);
}
.vi-table th,
.vi-table td,
.vi-matrix th,
.vi-matrix td {
  padding: var(--lt-space-2) var(--lt-space-3);
  border: thin solid var(--lt-separator);
  text-align: center;
  white-space: nowrap;
}
.vi-table th,
.vi-matrix th {
  background: var(--lt-well);
  color: var(--lt-label-2);
  font-weight: 600;
}
.vi-matrix td.vi-cell--visited {
  background: var(--lt-green-soft);
  color: var(--lt-label);
}
.vi-matrix td.vi-cell--current {
  background: var(--lt-accent-fill);
  color: var(--lt-on-accent);
  font-weight: 600;
}
.vi-matrix td.vi-cell--highlight {
  background: var(--lt-orange-soft);
  color: var(--lt-label);
}
.vi-state-array {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--lt-space-1);
}
.vi-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: var(--lt-space-8);
  padding: var(--lt-space-2);
  background: var(--lt-surface);
  border: calc(var(--lt-space-1) * 0.5) solid var(--lt-separator);
  border-radius: var(--lt-radius-sm);
}
.vi-cell--current {
  background: var(--lt-accent-soft);
  border-color: var(--lt-accent);
}
.vi-cell--visited,
.vi-cell--sorted {
  background: var(--lt-green-soft);
  border-color: var(--lt-green-text);
}
.vi-cell--highlight {
  background: var(--lt-orange-soft);
  border-color: var(--lt-orange-text);
}
.vi-cell--swapped {
  background: var(--lt-orange-soft);
  border-color: var(--lt-orange-text);
}
.vi-cell-index {
  margin-top: calc(var(--lt-space-1) * 0.5);
}
.vi-cell-state {
  color: var(--lt-label-2);
  font-size: var(--lt-text-caption);
}
.vi-bar-row {
  display: grid;
  grid-template-columns: minmax(calc(var(--lt-text-body) * 5), auto) minmax(0, 1fr) auto;
  gap: var(--lt-space-3);
  align-items: center;
}
.vi-bar-label,
.vi-bar-value {
  color: var(--lt-label-2);
  font-size: var(--lt-text-caption);
}
.vi-bar-label {
  text-align: end;
}
.vi-bar-value {
  color: var(--lt-label);
  font-weight: 600;
}
.vi-bar-track {
  height: calc(var(--lt-space-1) * 4.5);
  overflow: hidden;
  background: var(--lt-well);
  border-radius: var(--lt-radius-sm);
}
.vi-bar-fill {
  min-width: calc(var(--lt-space-1) * 0.5);
  height: 100%;
  background: var(--lt-accent-fill);
  border-radius: inherit;
}
.vi-number-line {
  padding: var(--lt-space-5) 0 var(--lt-space-2);
}
.vi-nl-track {
  position: relative;
  height: var(--lt-space-1);
  margin: 0 var(--lt-space-3);
  background: var(--lt-separator);
  border-radius: var(--lt-radius-pill);
}
.vi-nl-marker {
  position: absolute;
  top: calc(var(--lt-space-1) * -1.5);
  transform: translateX(-50%);
}
.vi-nl-marker-line {
  width: calc(var(--lt-space-1) * 0.5);
  height: var(--lt-space-4);
  background: var(--lt-separator);
}
.vi-nl-marker-label {
  margin-top: calc(var(--lt-space-1) * 0.5);
  text-align: center;
  white-space: nowrap;
}
.vi-nl-current {
  position: absolute;
  top: calc(var(--lt-space-1) * -2.5);
  transform: translateX(-50%);
}
.vi-nl-current-dot {
  width: var(--lt-space-3);
  height: var(--lt-space-3);
  background: var(--lt-accent-fill);
  border: calc(var(--lt-space-1) * 0.5) solid var(--lt-surface);
  border-radius: 50%;
  box-shadow: 0 0 0 calc(var(--lt-space-1) * 0.5) var(--lt-accent);
}
.vi-nl-range {
  display: flex;
  justify-content: space-between;
  margin-top: var(--lt-space-5);
  padding: 0 var(--lt-space-2);
}
@media (max-width: 640px) {
  .vi-bar-row {
    grid-template-columns: minmax(calc(var(--lt-text-body) * 4), auto) minmax(0, 1fr) auto;
    gap: var(--lt-space-2);
  }
}
</style>
