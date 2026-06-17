<template>
  <div class="visual-interactive">
    <!-- 加载状态 -->
    <div v-if="loading" class="vi-loading">
      <el-icon class="is-loading" :size="24"><Loading /></el-icon>
      <span>{{ loadingText }}</span>
    </div>

    <!-- 错误状态：JSON 解析失败，回退为 Markdown 显示 -->
    <div v-else-if="fallbackMode" class="vi-fallback">
      <el-alert type="info" :closable="false" show-icon title="可视化数据解析失败，已切换为文本显示" style="margin-bottom: 12px;" />
      <MarkdownRenderer :content="rawContent" />
    </div>

    <!-- 正常渲染 -->
    <div v-else-if="data" class="vi-content">
      <!-- 标题和摘要 -->
      <div class="vi-header">
        <h3 class="vi-title">📊 {{ data.title }}</h3>
        <p class="vi-summary">{{ data.summary }}</p>
      </div>

      <!-- 逐个渲染元素 -->
      <div v-for="(el, idx) in data.elements" :key="idx" class="vi-element">
        <!-- text -->
        <div v-if="el.type === 'text'" class="vi-block">
          <div class="vi-block-label">{{ el.label }}</div>
          <MarkdownRenderer :content="el.content" />
        </div>

        <!-- step_list -->
        <div v-else-if="el.type === 'step_list'" class="vi-block">
          <div class="vi-block-label">{{ el.label }}</div>
          <div class="vi-steps">
            <div
              v-for="(step, si) in el.steps"
              :key="si"
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

        <!-- table -->
        <div v-else-if="el.type === 'table'" class="vi-block">
          <div class="vi-block-label">{{ el.label }}</div>
          <div class="vi-table-wrapper">
            <table class="vi-table">
              <thead>
                <tr>
                  <th v-for="(h, hi) in el.headers" :key="hi">{{ h }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, ri) in el.rows" :key="ri">
                  <td v-for="(cell, ci) in row" :key="ci">{{ cell }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- state_array -->
        <div v-else-if="el.type === 'state_array'" class="vi-block">
          <div class="vi-block-label">{{ el.label }}</div>
          <div v-if="el.description" class="vi-description">{{ el.description }}</div>
          <div class="vi-state-array">
            <div
              v-for="(cell, ci) in el.cells"
              :key="ci"
              class="vi-cell"
              :class="`vi-cell--${cell.state || 'default'}`"
            >
              <div class="vi-cell-value">{{ cell.value }}</div>
              <div class="vi-cell-index">{{ cell.index }}</div>
            </div>
          </div>
        </div>

        <!-- matrix -->
        <div v-else-if="el.type === 'matrix'" class="vi-block">
          <div class="vi-block-label">{{ el.label }}</div>
          <div v-if="el.description" class="vi-description">{{ el.description }}</div>
          <div class="vi-table-wrapper">
            <table class="vi-matrix">
              <thead>
                <tr>
                  <th v-for="(h, hi) in el.headers" :key="hi">{{ h }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, ri) in el.rows" :key="ri">
                  <td
                    v-for="(cell, ci) in row"
                    :key="ci"
                    :class="getCellClass(cell)"
                  >
                    {{ getCellValue(cell) }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- tree -->
        <div v-else-if="el.type === 'tree'" class="vi-block">
          <div class="vi-block-label">{{ el.label }}</div>
          <div class="vi-tree">
            <TreeNode :node="el.root" :depth="0" />
          </div>
        </div>

        <!-- bar_chart -->
        <div v-else-if="el.type === 'bar_chart'" class="vi-block">
          <div class="vi-block-label">{{ el.label }}</div>
          <div class="vi-bar-chart">
            <div
              v-for="(item, bi) in el.items"
              :key="bi"
              class="vi-bar-row"
            >
              <div class="vi-bar-label">{{ item.label }}</div>
              <div class="vi-bar-track">
                <div
                  class="vi-bar-fill"
                  :style="{ width: getBarWidth(item.value) + '%' }"
                />
              </div>
              <div class="vi-bar-value">{{ item.value }}</div>
            </div>
          </div>
        </div>

        <!-- number_line -->
        <div v-else-if="el.type === 'number_line'" class="vi-block">
          <div class="vi-block-label">{{ el.label }}</div>
          <div class="vi-number-line">
            <div class="vi-nl-track">
              <!-- 标记点 -->
              <div
                v-for="(marker, mi) in el.markers"
                :key="mi"
                class="vi-nl-marker"
                :style="{ left: getMarkerPos(el, marker.position) + '%' }"
              >
                <div class="vi-nl-marker-line" />
                <div class="vi-nl-marker-label">{{ marker.label }}</div>
              </div>
              <!-- 当前位置指针 -->
              <div
                class="vi-nl-current"
                :style="{ left: getMarkerPos(el, el.current) + '%' }"
              >
                <div class="vi-nl-current-dot" />
              </div>
            </div>
            <div class="vi-nl-range">
              <span>{{ el.min }}</span>
              <span>{{ el.max }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else class="vi-empty">
      <el-empty description="暂无可视化数据" :image-size="60" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, defineComponent, h } from 'vue'
import type { PropType, VNode } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import MarkdownRenderer from './MarkdownRenderer.vue'
import type {
  VisualInteractiveData,
  VisualMatrixCell,
  VisualTreeNode,
  VisualBarChartElement,
  VisualNumberLineElement,
} from '@/api/ai'

/** 递归树节点渲染组件 */
const TreeNode = defineComponent({
  name: 'TreeNodeComponent',
  props: {
    node: { type: Object as PropType<VisualTreeNode>, required: true },
    depth: { type: Number, default: 0 },
  },
  setup(props): () => VNode {
    return (): VNode => {
      const stateClass = `vi-tree-node--${props.node.state || 'default'}`
      const children: VNode[] =
        props.node.children && props.node.children.length > 0
          ? props.node.children.map((child, i) =>
              h(TreeNode, { node: child, depth: props.depth + 1, key: i }),
            )
          : []

      return h('div', { class: 'vi-tree-node-wrapper' }, [
        h('div', { class: `vi-tree-node ${stateClass}` }, [
          h('span', { class: 'vi-tree-node-name' }, props.node.name),
        ]),
        children.length > 0 ? h('div', { class: 'vi-tree-children' }, children) : null,
      ])
    }
  },
})

const props = defineProps<{
  content: string
  loading?: boolean
  loadingText?: string
}>()

const data = ref<VisualInteractiveData | null>(null)
const fallbackMode = ref(false)
const rawContent = ref('')

watch(
  () => props.content,
  (val) => {
    if (!val) {
      data.value = null
      fallbackMode.value = false
      return
    }
    parseContent(val)
  },
  { immediate: true },
)

function parseContent(raw: string) {
  // 尝试直接解析 JSON
  const parsed = tryParseJson(raw)
  if (parsed && parsed.elements) {
    data.value = parsed as VisualInteractiveData
    fallbackMode.value = false
    return
  }
  // 尝试从 Markdown 代码块中提取 JSON
  const jsonBlockMatch = raw.match(/```(?:json)?\s*\n?([\s\S]*?)```/)
  if (jsonBlockMatch) {
    const inner = tryParseJson(jsonBlockMatch[1].trim())
    if (inner && inner.elements) {
      data.value = inner as VisualInteractiveData
      fallbackMode.value = false
      return
    }
  }
  // 回退模式
  data.value = null
  fallbackMode.value = true
  rawContent.value = raw
}

function tryParseJson(text: string): VisualInteractiveData | null {
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

// bar_chart 最大宽度计算
function getBarWidth(value: number): number {
  // 找到所有 bar items 中的最大值
  const chartEl = data.value?.elements.find(
    (el): el is VisualBarChartElement => el.type === 'bar_chart',
  )
  if (!chartEl || chartEl.items.length === 0) return 0
  const max = Math.max(...chartEl.items.map((i) => i.value), 1)
  return Math.round((value / max) * 100)
}

// number_line 标记位置计算
function getMarkerPos(el: VisualNumberLineElement, position: number): number {
  const range = el.max - el.min
  if (range === 0) return 50
  return Math.round(((position - el.min) / range) * 100)
}

// matrix cell 处理
function getCellClass(cell: string | VisualMatrixCell): string {
  if (typeof cell === 'string') return ''
  return `vi-cell--${cell.state || 'default'}`
}

function getCellValue(cell: string | VisualMatrixCell): string {
  if (typeof cell === 'string') return cell
  return cell.value
}
</script>

<script lang="ts">
// TreeNode is defined via the filename "QuestionVisualInteractive" for self-reference,
// but for the recursive tree we define a separate internal component.
export default { name: 'QuestionVisualInteractive' }
</script>

<style scoped>
.visual-interactive {
  padding: 4px 0;
}

.vi-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 20px 0;
  color: #909399;
  justify-content: center;
}

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
  margin: 0 0 6px 0;
  font-size: 16px;
  color: #303133;
}

.vi-summary {
  margin: 0;
  font-size: 13px;
  color: #606266;
  line-height: 1.5;
}

.vi-element {
  /* each element */
}

.vi-block {
  background: #f8f9fa;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px 14px;
}

.vi-block-label {
  font-size: 13px;
  font-weight: 600;
  color: #409eff;
  margin-bottom: 8px;
}

.vi-description {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}

/* step_list */
.vi-steps {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.vi-step {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 6px;
  border-left: 3px solid #dcdfe6;
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
  font-size: 13px;
  color: #303133;
}

.vi-step-detail {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

/* table / matrix */
.vi-table-wrapper {
  overflow-x: auto;
}

.vi-table,
.vi-matrix {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
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
  font-weight: 600;
  color: #606266;
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

.vi-matrix td.vi-cell--default {
  /* default */
}

/* state_array */
.vi-state-array {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: center;
}

.vi-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 40px;
  padding: 6px 8px;
  border-radius: 6px;
  border: 2px solid #dcdfe6;
  background: #fff;
}

.vi-cell--default {
  border-color: #dcdfe6;
}

.vi-cell--current {
  border-color: #409eff;
  background: #ecf5ff;
}

.vi-cell--visited {
  border-color: #67c23a;
  background: #f0f9eb;
}

.vi-cell--highlight {
  border-color: #e6a23c;
  background: #fdf6ec;
}

.vi-cell--swapped {
  border-color: #f56c6c;
  background: #fef0f0;
}

.vi-cell--sorted {
  border-color: #67c23a;
  background: #f0f9eb;
}

.vi-cell-value {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.vi-cell-index {
  font-size: 10px;
  color: #909399;
  margin-top: 2px;
}

/* tree */
.vi-tree {
  padding: 8px 0;
}

.vi-tree-node-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
}

.vi-tree-node {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 50px;
  padding: 6px 12px;
  border-radius: 20px;
  border: 2px solid #dcdfe6;
  background: #fff;
  font-size: 13px;
  margin-bottom: 4px;
}

.vi-tree-node--default {
  border-color: #dcdfe6;
}

.vi-tree-node--current {
  border-color: #409eff;
  background: #ecf5ff;
  font-weight: 600;
}

.vi-tree-node--visited {
  border-color: #67c23a;
  background: #f0f9eb;
}

.vi-tree-node-name {
  white-space: nowrap;
}

.vi-tree-children {
  display: flex;
  gap: 16px;
  padding-top: 8px;
  position: relative;
}

.vi-tree-children::before {
  content: '';
  position: absolute;
  top: 0;
  left: 50%;
  width: 1px;
  height: 8px;
  background: #dcdfe6;
}

/* bar_chart */
.vi-bar-chart {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.vi-bar-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.vi-bar-label {
  min-width: 80px;
  font-size: 12px;
  color: #606266;
  text-align: right;
}

.vi-bar-track {
  flex: 1;
  height: 20px;
  background: #f5f7fa;
  border-radius: 4px;
  overflow: hidden;
}

.vi-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #409eff, #66b1ff);
  border-radius: 4px;
  transition: width 0.3s ease;
  min-width: 2px;
}

.vi-bar-value {
  min-width: 30px;
  font-size: 12px;
  font-weight: 600;
  color: #409eff;
}

/* number_line */
.vi-number-line {
  padding: 20px 0 8px;
}

.vi-nl-track {
  position: relative;
  height: 4px;
  background: #dcdfe6;
  border-radius: 2px;
  margin: 0 10px;
}

.vi-nl-marker {
  position: absolute;
  top: -6px;
  transform: translateX(-50%);
}

.vi-nl-marker-line {
  width: 2px;
  height: 16px;
  background: #909399;
}

.vi-nl-marker-label {
  font-size: 10px;
  color: #909399;
  text-align: center;
  margin-top: 2px;
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
  background: #409eff;
  border-radius: 50%;
  border: 2px solid #fff;
  box-shadow: 0 0 0 2px #409eff;
}

.vi-nl-range {
  display: flex;
  justify-content: space-between;
  margin-top: 20px;
  font-size: 12px;
  color: #909399;
  padding: 0 6px;
}

.vi-empty {
  padding: 20px 0;
}

.vi-fallback {
  padding: 4px 0;
}
</style>