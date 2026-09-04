<template>
  <div class="vi-content">
    <div class="vi-header">
      <h3 class="vi-title">📊 {{ data.title }}</h3>
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
            <div class="vi-step-icon">
              <span v-if="step.status === 'done'">✅</span>
              <span v-else-if="step.status === 'current'">🔵</span>
              <span v-else>⏳</span>
            </div>
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
                <td v-for="(cell, cellIndex) in row" :key="cellIndex" :class="getCellClass(cell)">
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
              <div class="vi-bar-fill" :style="{ width: getBarWidth(item.value) + '%' }" />
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
        <div class="vi-block-label">▶ {{ element.label }}</div>
        <CodeAnimationViewer :element="element" />
      </div>

      <div v-else-if="element.type === 'sql_execution'" class="vi-block">
        <div class="vi-block-label">🗃️ {{ element.label }}</div>
        <SqlExecutionViewer :element="element" />
      </div>

      <div v-else-if="element.type === 'network_protocol'" class="vi-block">
        <div class="vi-block-label">🌐 {{ element.label }}</div>
        <NetworkProtocolViewer :element="element" />
      </div>

      <div v-else-if="element.type === 'os_process'" class="vi-block">
        <div class="vi-block-label">⚙️ {{ element.label }}</div>
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
import type { VisualBarChartElement, VisualInteractiveData, VisualMatrixCell, VisualNumberLineElement } from '@/api/ai'
import QuestionVisualMermaid from './QuestionVisualMermaid.vue'
import QuestionVisualTree from './QuestionVisualTree.vue'

const props = defineProps<{
  data: VisualInteractiveData
}>()

function getBarWidth(value: number): number {
  const chartElement = props.data.elements.find(
    (element): element is VisualBarChartElement => element.type === 'bar_chart',
  )
  if (!chartElement || chartElement.items.length === 0) return 0

  const max = Math.max(...chartElement.items.map((item) => item.value), 1)
  return Math.round((value / max) * 100)
}

function getMarkerPos(element: VisualNumberLineElement, position: number): number {
  const range = element.max - element.min
  if (range === 0) return 50
  return Math.round(((position - element.min) / range) * 100)
}

function getCellClass(cell: string | VisualMatrixCell): string {
  return typeof cell === 'string' ? '' : `vi-cell--${cell.state || 'default'}`
}

function getCellValue(cell: string | VisualMatrixCell): string {
  return typeof cell === 'string' ? cell : cell.value
}
</script>

<style scoped>
.vi-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.vi-header {
  border-bottom: 1px solid #ebeef5;
  padding-bottom: 12px;
}

.vi-title {
  color: #303133;
  font-size: 16px;
  margin: 0 0 6px;
}

.vi-summary {
  color: #606266;
  font-size: 13px;
  line-height: 1.5;
  margin: 0;
}

.vi-block {
  background: #f8f9fa;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px 14px;
}

.vi-block-label {
  color: #409eff;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}

.vi-description {
  color: #909399;
  font-size: 12px;
  margin-bottom: 8px;
}

.vi-steps {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.vi-step {
  align-items: flex-start;
  border-left: 3px solid #dcdfe6;
  border-radius: 6px;
  display: flex;
  gap: 10px;
  padding: 8px 10px;
}

.vi-step--done {
  background: #f0f9ff;
  border-left-color: #67c23a;
}

.vi-step--current {
  background: #ecf5ff;
  border-left-color: #409eff;
}

.vi-step--pending {
  background: #fafafa;
  border-left-color: #dcdfe6;
}

.vi-step-icon {
  flex-shrink: 0;
  font-size: 14px;
  margin-top: 1px;
}

.vi-step-content {
  color: #303133;
  font-size: 13px;
}

.vi-step-detail {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}

.vi-table-wrapper {
  overflow-x: auto;
}

.vi-table,
.vi-matrix {
  border-collapse: collapse;
  font-size: 12px;
  width: 100%;
}

.vi-table th,
.vi-table td,
.vi-matrix th,
.vi-matrix td {
  border: 1px solid #ebeef5;
  padding: 6px 10px;
  text-align: center;
  white-space: nowrap;
}

.vi-table th,
.vi-matrix th {
  background: #f5f7fa;
  color: #606266;
  font-weight: 600;
}

.vi-matrix td.vi-cell--visited {
  background: #ecf5ff;
  color: #409eff;
}

.vi-matrix td.vi-cell--current {
  background: #409eff;
  color: #fff;
  font-weight: 600;
}

.vi-matrix td.vi-cell--highlight {
  background: #fdf6ec;
  color: #e6a23c;
}

.vi-state-array {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: center;
}

.vi-cell {
  align-items: center;
  background: #fff;
  border: 2px solid #dcdfe6;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  min-width: 40px;
  padding: 6px 8px;
}

.vi-cell--default {
  border-color: #dcdfe6;
}

.vi-cell--current {
  background: #ecf5ff;
  border-color: #409eff;
}

.vi-cell--visited,
.vi-cell--sorted {
  background: #f0f9eb;
  border-color: #67c23a;
}

.vi-cell--highlight {
  background: #fdf6ec;
  border-color: #e6a23c;
}

.vi-cell--swapped {
  background: #fef0f0;
  border-color: #f56c6c;
}

.vi-cell-value {
  color: #303133;
  font-size: 14px;
  font-weight: 600;
}

.vi-cell-index {
  color: #909399;
  font-size: 10px;
  margin-top: 2px;
}

.vi-bar-chart {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.vi-bar-row {
  align-items: center;
  display: flex;
  gap: 10px;
}

.vi-bar-label {
  color: #606266;
  font-size: 12px;
  min-width: 80px;
  text-align: right;
}

.vi-bar-track {
  background: #f5f7fa;
  border-radius: 4px;
  flex: 1;
  height: 20px;
  overflow: hidden;
}

.vi-bar-fill {
  background: linear-gradient(90deg, #409eff, #66b1ff);
  border-radius: 4px;
  height: 100%;
  min-width: 2px;
  transition: width 0.3s ease;
}

.vi-bar-value {
  color: #409eff;
  font-size: 12px;
  font-weight: 600;
  min-width: 30px;
}

.vi-number-line {
  padding: 20px 0 8px;
}

.vi-nl-track {
  background: #dcdfe6;
  border-radius: 2px;
  height: 4px;
  margin: 0 10px;
  position: relative;
}

.vi-nl-marker {
  position: absolute;
  top: -6px;
  transform: translateX(-50%);
}

.vi-nl-marker-line {
  background: #909399;
  height: 16px;
  width: 2px;
}

.vi-nl-marker-label {
  color: #909399;
  font-size: 10px;
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
  background: #409eff;
  border: 2px solid #fff;
  border-radius: 50%;
  box-shadow: 0 0 0 2px #409eff;
  height: 12px;
  width: 12px;
}

.vi-nl-range {
  color: #909399;
  display: flex;
  font-size: 12px;
  justify-content: space-between;
  margin-top: 20px;
  padding: 0 6px;
}
</style>
