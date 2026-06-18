<template>
  <div class="knowledge-graph">
    <div class="page-header">
      <h2>🕸️ 知识图谱</h2>
      <p class="subtitle">可视化知识点关系与你的掌握程度，薄弱环节一目了然</p>
    </div>

    <!-- 筛选栏 -->
    <el-card shadow="never" class="filter-card">
      <div class="filter-row">
        <span class="filter-label">选择课程：</span>
        <el-select v-model="selectedCourseId" placeholder="全部课程" clearable @change="fetchData" style="width: 240px">
          <el-option label="全部课程" :value="0" />
          <el-option
            v-for="c in courses"
            :key="c.id"
            :label="c.name"
            :value="c.id"
          />
        </el-select>
        <el-button type="primary" :icon="Refresh" @click="fetchData" :loading="loading" style="margin-left: 12px">
          刷新
        </el-button>
        <el-button-group style="margin-left: 12px">
          <el-button :type="layout === 'force' ? 'primary' : 'default'" @click="layout = 'force'; renderGraph()">
            力导向
          </el-button>
          <el-button :type="layout === 'circular' ? 'primary' : 'default'" @click="layout = 'circular'; renderGraph()">
            环形
          </el-button>
        </el-button-group>
      </div>
    </el-card>

    <!-- 图例和统计 -->
    <el-row :gutter="16" class="legend-row" v-if="graphData">
      <el-col :xs="24" :sm="16">
        <el-card shadow="hover" class="legend-card">
          <div class="legend-items">
            <span class="legend-title">图例：</span>
            <span class="legend-item"><span class="dot mastered"></span> 已掌握</span>
            <span class="legend-item"><span class="dot review"></span> 需复习</span>
            <span class="legend-item"><span class="dot weak"></span> 薄弱</span>
            <span class="legend-item"><span class="dot not-practiced"></span> 未练习</span>
            <span class="legend-divider">|</span>
            <span class="legend-item"><span class="dot large"></span> 有子知识点</span>
            <span class="legend-item"><span class="dot small"></span> 叶子节点</span>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card shadow="hover" class="summary-card">
          <div class="summary-items">
            <div class="summary-item">
              <span class="summary-value">{{ graphData.nodes.length }}</span>
              <span class="summary-label">知识点</span>
            </div>
            <div class="summary-item">
              <span class="summary-value">{{ graphData.edges.length }}</span>
              <span class="summary-label">关系</span>
            </div>
            <div class="summary-item">
              <span class="summary-value">{{ graphData.courses.length }}</span>
              <span class="summary-label">课程</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图谱主体 -->
    <el-card shadow="never" class="graph-card">
      <div ref="chartRef" class="graph-container" v-loading="loading" element-loading-text="加载中..."></div>
      <el-empty v-if="!loading && graphData && graphData.nodes.length === 0" description="暂无知识点数据，请先创建课程和知识点" />
    </el-card>

    <!-- 节点详情抽屉 -->
    <el-drawer v-model="drawerVisible" :title="selectedNode?.name || ''" size="360px">
      <template v-if="selectedNode">
        <div class="node-detail">
          <div class="detail-header">
            <el-tag :type="masteryTagType(selectedNode.masteryLevel)" size="large">
              {{ masteryLabel(selectedNode.masteryLevel) }}
            </el-tag>
            <span class="detail-course">{{ selectedNode.courseName }}</span>
          </div>

          <el-descriptions :column="1" border class="detail-desc">
            <el-descriptions-item label="正确率">
              <span :class="accuracyClass(selectedNode.accuracy)">
                {{ selectedNode.accuracy }}%
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="练习次数">
              {{ selectedNode.practiceCount }} 次
            </el-descriptions-item>
            <el-descriptions-item label="错题数量">
              <span :class="selectedNode.wrongCount > 0 ? 'text-warn' : ''">
                {{ selectedNode.wrongCount }} 题
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="节点类型">
              {{ nodeTypeLabel(selectedNode.nodeType) }}
            </el-descriptions-item>
            <el-descriptions-item label="所属课程">
              {{ selectedNode.courseName }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="detail-actions">
            <el-button type="primary" @click="goToPractice(selectedNode)" :disabled="selectedNode.practiceCount === 0">
              去刷题
            </el-button>
            <el-button @click="goToLearningPath(selectedNode)">
              查看学习路径
            </el-button>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
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

// 掌握程度标签
function masteryLabel(level: number): string {
  const labels: Record<number, string> = { 0: '未练习', 1: '薄弱', 2: '需复习', 3: '已掌握' }
  return labels[level] || '未知'
}

function masteryTagType(level: number): 'success' | 'warning' | 'danger' | 'info' {
  const types: Record<number, 'success' | 'warning' | 'danger' | 'info'> = {
    0: 'info', 1: 'danger', 2: 'warning', 3: 'success'
  }
  return types[level] || 'info'
}

function accuracyClass(accuracy: number): string {
  if (accuracy >= 70) return 'text-success'
  if (accuracy >= 50) return 'text-warning'
  if (accuracy > 0) return 'text-danger'
  return ''
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
    chart.on('click', (params: any) => {
      if (params.dataType === 'node') {
        const nodeId = params.data.id as number
        selectedNode.value = graphData.value?.nodes.find(n => n.id === nodeId) || null
        drawerVisible.value = true
      }
    })
  }

  const data = graphData.value

  // 构建 ECharts graph 数据
  const categories = data.courses.map(c => ({ name: c.name }))

  const nodes = data.nodes.map(node => ({
    id: String(node.id),
    name: node.name,
    category: categories.findIndex(c => c.name === node.category),
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

  const edges = data.edges.map(edge => ({
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
      formatter: (params: any) => {
        if (params.dataType === 'node' && params.data.rawData) {
          const d = params.data.rawData as KnowledgeGraphNode
          const color = masteryColors[d.masteryLevel]
          return `
            <div style="padding: 4px 0;">
              <strong style="font-size: 14px;">${d.name}</strong>
              <br/><span style="color: #909399;">${d.courseName}</span>
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
    legend: categories.length > 1 ? [{
      data: categories.map(c => c.name),
      bottom: 10,
      textStyle: { fontSize: 12 },
    }] : undefined,
    series: [{
      type: 'graph',
      layout: layout.value,
      roam: true,
      draggable: true,
      force: layout.value === 'force' ? {
        repulsion: 300,
        gravity: 0.1,
        edgeLength: [80, 200],
        layoutAnimation: true,
      } : undefined,
      circular: layout.value === 'circular' ? {
        rotateLabel: true,
      } : undefined,
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
    }],
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
  padding: 4px 0;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0 0 4px;
  font-size: 22px;
}

.page-header .subtitle {
  margin: 0;
  color: #909399;
  font-size: 14px;
}

.filter-card {
  margin-bottom: 16px;
}

.filter-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.filter-label {
  font-size: 14px;
  color: #606266;
  white-space: nowrap;
}

.legend-row {
  margin-bottom: 16px;
}

.legend-card {
  height: 100%;
}

.legend-items {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 13px;
  color: #606266;
}

.legend-title {
  font-weight: 600;
  color: #303133;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.legend-divider {
  color: #DCDFE6;
}

.dot {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 50%;
}

.dot.mastered { background: #67C23A; }
.dot.review { background: #E6A23C; }
.dot.weak { background: #F56C6C; }
.dot.not-practiced { background: #C0C4CC; }
.dot.large { width: 16px; height: 16px; background: #409EFF; }
.dot.small { width: 8px; height: 8px; background: #409EFF; }

.summary-card {
  height: 100%;
}

.summary-items {
  display: flex;
  justify-content: space-around;
  text-align: center;
}

.summary-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.summary-value {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}

.summary-label {
  font-size: 12px;
  color: #909399;
}

.graph-card {
  margin-bottom: 16px;
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

.detail-desc {
  margin-bottom: 20px;
}

.detail-actions {
  display: flex;
  gap: 12px;
}

.text-success { color: #67C23A; font-weight: 600; }
.text-warning { color: #E6A23C; font-weight: 600; }
.text-danger { color: #F56C6C; font-weight: 600; }
.text-warn { color: #F56C6C; }

@media (max-width: 768px) {
  .graph-container {
    height: 400px;
  }

  .filter-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .summary-items {
    justify-content: space-between;
  }
}
</style>
