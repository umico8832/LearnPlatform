<template>
  <div class="knowledge-graph page-container">
    <section class="page-hero">
      <div>
        <span class="section-kicker">KNOWLEDGE MAP</span>
        <h2>知识图谱</h2>
        <p>把课程知识点、先后关系和你的掌握状态放在同一张图里，找到最值得优先补强的位置。</p>
      </div>
      <div class="hero-actions">
        <el-select v-model="selectedCourseId" placeholder="全部课程" clearable @change="fetchData">
          <el-option label="全部课程" :value="0" />
          <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-button type="primary" :icon="Refresh" @click="fetchData" :loading="loading"> 刷新 </el-button>
      </div>
    </section>

    <div v-if="loading && !graphData" class="loading-container">
      <el-icon class="is-loading" :size="32"><Loading /></el-icon>
      <span>正在整理知识结构...</span>
    </div>

    <template v-else-if="graphData">
      <section class="summary-grid">
        <el-card v-for="item in summaryCards" :key="item.label" shadow="never" class="summary-card">
          <span>{{ item.label }}</span>
          <strong :class="item.tone">{{ item.value }}</strong>
          <small>{{ item.note }}</small>
        </el-card>
      </section>

      <section class="graph-layout">
        <el-card shadow="never" class="map-guide-card">
          <div class="card-heading">
            <div>
              <span class="section-kicker">HOW TO READ</span>
              <h3>图谱阅读方式</h3>
            </div>
          </div>
          <p>节点颜色代表当前掌握程度，连线表示知识点的上下级关系；点击任一节点即可查看练习数据并开始针对练习。</p>
          <div class="legend-list">
            <span v-for="item in legendItems" :key="item.label" class="legend-item">
              <i class="dot" :class="item.className"></i>{{ item.label }}
            </span>
          </div>
          <div class="node-scale">
            <i class="dot large"></i> 大节点含有子知识点，<i class="dot small"></i> 小节点为末级知识点
          </div>
        </el-card>

        <el-card shadow="never" class="layout-card">
          <div class="card-heading">
            <div>
              <span class="section-kicker">VIEW MODE</span>
              <h3>图谱布局</h3>
            </div>
          </div>
          <div class="layout-switch" role="group" aria-label="图谱布局">
            <button type="button" :class="{ active: layout === 'force' }" @click="layout = 'force'">探索关系</button>
            <button type="button" :class="{ active: layout === 'circular' }" @click="layout = 'circular'">
              环形总览
            </button>
          </div>
          <small>可拖动节点，使用滚轮缩放查看细节。</small>
        </el-card>
      </section>

      <el-card shadow="never" class="graph-card">
        <div class="graph-toolbar">
          <div>
            <span class="section-kicker">INTERACTIVE MAP</span>
            <h3>{{ courseScopeLabel }}</h3>
          </div>
          <span>{{ graphData.nodes.length }} 个知识点 · {{ graphData.edges.length }} 条关系</span>
        </div>
        <div ref="chartRef" class="graph-container" v-loading="loading" element-loading-text="更新图谱中..."></div>
        <el-empty v-if="!loading && graphData.nodes.length === 0" description="暂无知识点数据，请先创建课程和知识点">
          <el-button type="primary" @click="router.push('/courses')">去课程中心</el-button>
        </el-empty>
      </el-card>
    </template>

    <!-- 节点详情抽屉 -->
    <el-drawer v-model="drawerVisible" :title="selectedNode?.name || ''" size="360px">
      <template v-if="selectedNode">
        <div class="node-detail">
          <div class="detail-header">
            <el-tag :type="masteryTagType(selectedNode.masteryLevel)" size="large" effect="light">
              {{ masteryLabel(selectedNode.masteryLevel) }}
            </el-tag>
            <span class="detail-course">{{ selectedNode.courseName }}</span>
          </div>

          <div class="mastery-meter">
            <div>
              <span>掌握进度</span><strong>{{ selectedNode.accuracy }}%</strong>
            </div>
            <el-progress
              :percentage="clampPercent(selectedNode.accuracy)"
              :color="masteryColors[selectedNode.masteryLevel]"
              :show-text="false"
              :stroke-width="10"
            />
          </div>

          <el-descriptions :column="1" border class="detail-desc">
            <el-descriptions-item label="正确率">
              <span :class="accuracyClass(selectedNode.accuracy)"> {{ selectedNode.accuracy }}% </span>
            </el-descriptions-item>
            <el-descriptions-item label="练习次数"> {{ selectedNode.practiceCount }} 次 </el-descriptions-item>
            <el-descriptions-item label="错题数量">
              <span :class="selectedNode.wrongCount > 0 ? 'text-warn' : ''"> {{ selectedNode.wrongCount }} 题 </span>
            </el-descriptions-item>
            <el-descriptions-item label="节点类型">
              {{ nodeTypeLabel(selectedNode.nodeType) }}
            </el-descriptions-item>
            <el-descriptions-item label="所属课程">
              {{ selectedNode.courseName }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="detail-actions">
            <el-button type="primary" @click="goToPractice(selectedNode)"> 去刷题 </el-button>
            <el-button @click="goToLearningPath(selectedNode)"> 查看学习路径 </el-button>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Loading, Refresh } from '@element-plus/icons-vue'
import * as echarts from 'echarts/core'
import { GraphChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getKnowledgeGraph } from '@/api/statistics'
import type { KnowledgeGraph, KnowledgeGraphNode } from '@/api/statistics'
import { getAllCourses } from '@/api/course'
import { ElMessage } from 'element-plus'

echarts.use([GraphChart, TooltipComponent, LegendComponent, CanvasRenderer])

const router = useRouter()
const route = useRoute()
const chartRef = ref<HTMLElement>()
const loading = ref(false)
const graphData = ref<KnowledgeGraph | null>(null)
const selectedCourseId = ref(0)
const layout = ref<'force' | 'circular'>('force')
const drawerVisible = ref(false)
const selectedNode = ref<KnowledgeGraphNode | null>(null)

let chart: echarts.ECharts | null = null

interface CourseOption {
  id: number
  name: string
}

const courses = ref<CourseOption[]>([])

// 掌握程度颜色映射
const masteryColors: Record<number, string> = {
  0: '#C0C4CC', // 未练习 - 灰色
  1: '#F56C6C', // 薄弱 - 红色
  2: '#E6A23C', // 需复习 - 橙色
  3: '#67C23A', // 已掌握 - 绿色
}

const legendItems = [
  { label: '已掌握', className: 'mastered' },
  { label: '需复习', className: 'review' },
  { label: '薄弱', className: 'weak' },
  { label: '未练习', className: 'not-practiced' },
]

const summaryCards = computed(() => {
  const nodes = graphData.value?.nodes ?? []
  const weakCount = nodes.filter((node) => node.masteryLevel === 1).length
  const reviewCount = nodes.filter((node) => node.masteryLevel === 2).length
  const masteredCount = nodes.filter((node) => node.masteryLevel === 3).length
  return [
    {
      label: '知识点总数',
      value: nodes.length,
      note: `覆盖 ${graphData.value?.courses.length ?? 0} 门课程`,
      tone: 'tone-primary',
    },
    { label: '优先补强', value: weakCount, note: '薄弱知识点', tone: 'tone-danger' },
    { label: '等待复习', value: reviewCount, note: '建议安排复习', tone: 'tone-warning' },
    { label: '已掌握', value: masteredCount, note: '掌握状态稳定', tone: 'tone-success' },
  ]
})

const courseScopeLabel = computed(() => {
  if (!selectedCourseId.value) return '全部课程知识结构'
  return courses.value.find((course) => course.id === selectedCourseId.value)?.name || '当前课程知识结构'
})

// 掌握程度标签
function masteryLabel(level: number): string {
  const labels: Record<number, string> = { 0: '未练习', 1: '薄弱', 2: '需复习', 3: '已掌握' }
  return labels[level] || '未知'
}

function masteryTagType(level: number): 'success' | 'warning' | 'danger' | 'info' {
  const types: Record<number, 'success' | 'warning' | 'danger' | 'info'> = {
    0: 'info',
    1: 'danger',
    2: 'warning',
    3: 'success',
  }
  return types[level] || 'info'
}

function accuracyClass(accuracy: number): string {
  if (accuracy >= 70) return 'text-success'
  if (accuracy >= 50) return 'text-warning'
  if (accuracy > 0) return 'text-danger'
  return ''
}

function clampPercent(value: number) {
  return Math.max(0, Math.min(100, value))
}

function escapeHtml(value: string): string {
  return value.replace(
    /[&<>'"]/g,
    (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[char] || char,
  )
}

function nodeTypeLabel(type: string): string {
  const labels: Record<string, string> = { parent: '有子知识点', leaf: '叶子节点' }
  return labels[type] || type
}

function resizeChart() {
  chart?.resize()
}

async function fetchCourses() {
  try {
    const res = await getAllCourses()
    if (res.code === 0 && res.data) {
      courses.value = res.data.map((c) => ({ id: c.id, name: c.name }))
    }
  } catch {
    // 静默失败
  }
}

async function fetchData() {
  loading.value = true
  try {
    const courseId = selectedCourseId.value || undefined
    const res = await getKnowledgeGraph(courseId)
    if (res.code === 0 && res.data) {
      graphData.value = res.data
      await nextTick()
      renderGraph()
    }
  } catch {
    ElMessage.error('加载知识图谱数据失败')
  } finally {
    loading.value = false
  }
}

function renderGraph() {
  if (!chartRef.value || !graphData.value || graphData.value.nodes.length === 0) return

  if (!chart) {
    chart = echarts.init(chartRef.value)
    chart.on('click', (params: unknown) => {
      const event = params as { dataType?: string; data?: { id?: number } }
      if (event.dataType === 'node' && event.data?.id != null) {
        const nodeId = event.data.id
        selectedNode.value = graphData.value?.nodes.find((n) => n.id === nodeId) || null
        drawerVisible.value = true
      }
    })
  }

  const data = graphData.value

  // 构建 ECharts graph 数据
  const categories = data.courses.map((c) => ({ name: c.name }))

  const nodes = data.nodes.map((node) => ({
    id: String(node.id),
    name: node.name,
    category: categories.findIndex((c) => c.name === node.category),
    symbolSize: node.nodeType === 'parent' ? 45 : 28,
    itemStyle: {
      color: masteryColors[node.masteryLevel],
      borderColor: '#fff',
      borderWidth: 2,
      shadowBlur: 10,
      shadowColor: masteryColors[node.masteryLevel] + '80',
    },
    label: {
      show: true,
      fontSize: node.nodeType === 'parent' ? 13 : 11,
      fontWeight: node.nodeType === 'parent' ? 'bold' : 'normal',
    },
    // 存储原始数据用于 tooltip
    rawData: node,
  }))

  const edges = data.edges.map((edge) => ({
    source: String(edge.source),
    target: String(edge.target),
    lineStyle: {
      color: '#DCDFE6',
      width: 2,
      curveness: 0.1,
    },
  }))

  const option: echarts.EChartsCoreOption = {
    tooltip: {
      trigger: 'item',
      formatter: (params: unknown) => {
        const item = params as { dataType?: string; data?: { rawData?: KnowledgeGraphNode } }
        if (item.dataType === 'node' && item.data?.rawData) {
          const d = item.data.rawData
          const color = masteryColors[d.masteryLevel]
          return `
            <div style="padding: 4px 0;">
              <strong style="font-size: 14px;">${escapeHtml(d.name)}</strong>
              <br/><span style="color: #909399;">${escapeHtml(d.courseName)}</span>
              <hr style="margin: 6px 0; border-color: #EBEEF5;"/>
              <span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${color};margin-right:6px;"></span>
              ${masteryLabel(d.masteryLevel)}
              <br/>正确率：<strong>${d.accuracy}%</strong>
              <br/>练习：${d.practiceCount} 次 | 错题：${d.wrongCount} 题
            </div>
          `
        }
        return ''
      },
    },
    legend:
      categories.length > 1
        ? [
            {
              data: categories.map((c) => c.name),
              bottom: 10,
              textStyle: { fontSize: 12 },
            },
          ]
        : undefined,
    series: [
      {
        type: 'graph',
        layout: layout.value,
        roam: true,
        draggable: true,
        force:
          layout.value === 'force'
            ? {
                repulsion: 300,
                gravity: 0.1,
                edgeLength: [80, 200],
                layoutAnimation: true,
              }
            : undefined,
        circular:
          layout.value === 'circular'
            ? {
                rotateLabel: true,
              }
            : undefined,
        data: nodes,
        links: edges,
        categories,
        scaleLimit: { min: 0.4, max: 3 },
        emphasis: {
          focus: 'adjacency',
          lineStyle: { width: 4, color: '#409EFF' },
          itemStyle: {
            borderWidth: 3,
            borderColor: '#409EFF',
          },
        },
        label: {
          position: 'bottom',
          distance: 5,
        },
      },
    ],
  }

  chart.setOption(option, true)
}

function goToPractice(node: KnowledgeGraphNode) {
  router.push({ path: '/practice', query: { knowledgePointId: String(node.id) } })
}

function goToLearningPath(node: KnowledgeGraphNode) {
  router.push({ path: '/learning-path', query: { courseId: String(node.courseId) } })
}

onMounted(() => {
  const courseId = Number(route.query.courseId)
  if (Number.isFinite(courseId) && courseId > 0) {
    selectedCourseId.value = courseId
  }
  fetchCourses()
  fetchData()
  window.addEventListener('resize', resizeChart)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  chart?.dispose()
  chart = null
})

watch(layout, () => {
  renderGraph()
})
</script>

<style scoped>
.knowledge-graph {
  padding: 24px;
}

.page-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 18px;
  padding: 24px;
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  background: linear-gradient(135deg, rgba(23, 105, 170, 0.09), rgba(47, 133, 90, 0.1)), var(--lp-surface);
}

.section-kicker {
  display: inline-block;
  margin-bottom: 8px;
  color: var(--lp-primary);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.page-hero h2,
.card-heading h3,
.graph-toolbar h3 {
  margin: 0;
  color: var(--lp-text);
  font-weight: 850;
}

.page-hero h2 {
  font-size: 24px;
}

.page-hero p {
  max-width: 680px;
  margin: 8px 0 0;
  color: var(--lp-text-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.hero-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.hero-actions .el-select {
  width: 240px;
}

.loading-container {
  display: flex;
  min-height: 360px;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--lp-text-muted);
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}

.summary-card :deep(.el-card__body) {
  min-height: 108px;
}

.summary-card span,
.summary-card small {
  display: block;
  color: var(--lp-text-muted);
  font-size: 12px;
}

.summary-card strong {
  display: block;
  margin: 8px 0 6px;
  color: var(--lp-text);
  font-size: 28px;
  font-weight: 850;
  line-height: 1.1;
}

.tone-primary {
  color: var(--lp-primary) !important;
}
.tone-danger {
  color: var(--lp-danger) !important;
}
.tone-warning {
  color: var(--lp-warning) !important;
}
.tone-success {
  color: var(--lp-success) !important;
}

.graph-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(260px, 0.7fr);
  gap: 16px;
  margin-bottom: 16px;
}

.card-heading,
.graph-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-heading {
  margin-bottom: 14px;
}

.card-heading h3,
.graph-toolbar h3 {
  font-size: 18px;
}

.map-guide-card p {
  margin: 0;
  color: var(--lp-text-secondary);
  font-size: 14px;
  line-height: 1.75;
}

.legend-list {
  display: flex;
  flex-wrap: wrap;
  gap: 9px 16px;
  margin-top: 16px;
}

.legend-item,
.node-scale {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--lp-text-secondary);
  font-size: 13px;
}

.node-scale {
  margin-top: 12px;
  color: var(--lp-text-muted);
  font-size: 12px;
}

.dot {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 50%;
}

.dot.mastered {
  background: var(--lp-success);
}
.dot.review {
  background: var(--lp-warning);
}
.dot.weak {
  background: var(--lp-danger);
}
.dot.not-practiced {
  background: var(--lp-text-muted);
}
.dot.large {
  width: 16px;
  height: 16px;
  margin-left: 4px;
  background: var(--lp-primary);
}
.dot.small {
  width: 8px;
  height: 8px;
  background: var(--lp-primary);
}

.layout-card small {
  display: block;
  margin-top: 14px;
  color: var(--lp-text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.layout-switch {
  display: flex;
  padding: 4px;
  border-radius: 9px;
  background: var(--lp-surface-soft);
}

.layout-switch button {
  flex: 1;
  padding: 8px 10px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--lp-text-secondary);
  cursor: pointer;
  font-size: 13px;
  font-weight: 650;
}

.layout-switch button.active {
  background: var(--lp-surface);
  box-shadow: 0 2px 6px rgba(29, 61, 92, 0.12);
  color: var(--lp-primary);
}

.graph-card {
  overflow: hidden;
}

.graph-card :deep(.el-card__body) {
  padding: 0;
}

.graph-toolbar {
  padding: 16px 20px;
  border-bottom: 1px solid var(--lp-border);
  background: var(--lp-surface-soft);
}

.graph-toolbar span:last-child {
  color: var(--lp-text-muted);
  font-size: 13px;
}

.graph-container {
  width: 100%;
  height: 560px;
}

.node-detail {
  padding: 0 4px;
}

.detail-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.detail-course {
  color: #909399;
  font-size: 14px;
}

.mastery-meter {
  margin-bottom: 20px;
  padding: 14px;
  border-radius: 8px;
  background: var(--lp-surface-soft);
}

.mastery-meter > div {
  display: flex;
  justify-content: space-between;
  margin-bottom: 9px;
  color: var(--lp-text-secondary);
  font-size: 13px;
}

.mastery-meter strong {
  color: var(--lp-text);
  font-size: 15px;
}

.detail-desc {
  margin-bottom: 20px;
}

.detail-actions {
  display: flex;
  gap: 12px;
}

.text-success {
  color: var(--lp-success);
  font-weight: 600;
}
.text-warning {
  color: var(--lp-warning);
  font-weight: 600;
}
.text-danger {
  color: var(--lp-danger);
  font-weight: 600;
}
.text-warn {
  color: var(--lp-danger);
}

@media (max-width: 768px) {
  .knowledge-graph {
    padding: 16px;
  }

  .page-hero,
  .graph-layout {
    grid-template-columns: 1fr;
  }

  .page-hero {
    display: grid;
    padding: 20px;
  }

  .hero-actions,
  .hero-actions .el-select,
  .hero-actions .el-button {
    width: 100%;
  }

  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .graph-container {
    height: 400px;
  }

  .graph-toolbar {
    align-items: flex-start;
    flex-direction: column;
    padding: 16px;
  }

  .detail-actions .el-button {
    flex: 1;
  }
}
</style>
