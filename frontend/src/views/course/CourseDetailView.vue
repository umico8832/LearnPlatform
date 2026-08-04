<template>
  <div class="course-detail page-container">
    <section class="detail-hero">
      <div class="hero-main">
        <el-button :icon="ArrowLeft" text @click="router.back()">返回课程</el-button>
        <div class="course-title-row">
          <span class="course-icon">
            <el-icon><Reading /></el-icon>
          </span>
          <div>
            <span class="section-kicker">课程详情</span>
            <h2>{{ course?.name || '课程详情' }}</h2>
          </div>
        </div>
        <p>{{ course?.description || '暂无课程描述，可先从知识点树了解当前课程结构。' }}</p>
      </div>
      <div class="hero-actions">
        <el-button :icon="Collection" @click="goToQuestions">查看题目</el-button>
        <el-button v-if="isInLibrary" type="primary" :icon="Reading" @click="goToCourseOverview">
          进入课程总览
        </el-button>
        <el-button
          v-else
          type="primary"
          :icon="Collection"
          :loading="addingToLibrary"
          :disabled="loading"
          @click="addToLibrary"
        >
          加入课程库
        </el-button>
      </div>
    </section>

    <section class="detail-summary">
      <div class="summary-card">
        <span>知识点总数</span>
        <strong>{{ totalKP }}</strong>
      </div>
      <div class="summary-card">
        <span>顶级节点</span>
        <strong>{{ rootCount }}</strong>
      </div>
      <div class="summary-card">
        <span>叶子节点</span>
        <strong>{{ leafCount }}</strong>
      </div>
      <div class="summary-card">
        <span>最大层级</span>
        <strong>{{ maxDepth }}</strong>
      </div>
    </section>

    <section class="knowledge-panel">
      <div class="panel-header">
        <div>
          <span class="section-kicker">知识结构</span>
          <h3>知识点树</h3>
        </div>
        <el-tag type="info" effect="plain">{{ totalKP }} 个知识点</el-tag>
      </div>

      <div v-loading="loading" class="tree-wrap">
        <el-tree
          v-if="treeData.length > 0"
          :data="treeData"
          :props="{ children: 'children', label: 'name' }"
          node-key="id"
          default-expand-all
          :expand-on-click-node="false"
        >
          <template #default="{ data }">
            <div class="tree-node">
              <div class="node-left">
                <span class="node-icon" :class="{ leaf: !data.children || data.children.length === 0 }">
                  <el-icon v-if="data.children && data.children.length > 0"><Folder /></el-icon>
                  <el-icon v-else><Document /></el-icon>
                </span>
                <span class="node-name">{{ data.name }}</span>
              </div>
              <div class="node-right">
                <span v-if="data.description" class="node-desc">{{ data.description }}</span>
                <el-tag v-if="data.children && data.children.length > 0" size="small" type="info" effect="plain">
                  {{ data.children.length }} 子项
                </el-tag>
                <el-button
                  v-if="isAvailableTutorContent(data.contentKey) && isInLibrary"
                  size="small"
                  type="primary"
                  @click.stop="openTutor(data.id)"
                  >开始 AI 教学</el-button
                >
                <el-tag v-else-if="isAvailableTutorContent(data.contentKey)" size="small" type="info" effect="plain">
                  加入课程库后可学习
                </el-tag>
              </div>
            </div>
          </template>
        </el-tree>

        <el-empty v-else-if="!loading" description="暂无知识点">
          <el-button type="primary" @click="goToQuestions">先看课程题目</el-button>
        </el-empty>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Collection, Document, Folder, Reading } from '@element-plus/icons-vue'
import { addCourseToLibrary, getCourseById, getMyCourses, type CourseVO } from '@/api/course'
import { getKnowledgeTree, type KnowledgePointVO } from '@/api/knowledgePoint'

const route = useRoute()
const router = useRouter()

const course = ref<CourseVO | null>(null)
const treeData = ref<KnowledgePointVO[]>([])
const loading = ref(false)
const addingToLibrary = ref(false)
const isInLibrary = ref(false)

const courseId = computed(() => Number(route.params.id))
const availableTutorContentKeys = new Set([
  'ods-array-size-capacity',
  'ods-arraystack-insertion',
  'ods-arraystack-removal',
  'ods-arraystack-resize',
  'ods-arraystack-amortized-resize',
  'ods-arraystack-performance',
  'ods-fastarraystack-block-copy',
])

function isAvailableTutorContent(contentKey?: string) {
  return !!contentKey && availableTutorContentKeys.has(contentKey)
}

function countNodes(nodes: KnowledgePointVO[]): number {
  return nodes.reduce((sum, node) => sum + 1 + countNodes(node.children || []), 0)
}

function countLeaves(nodes: KnowledgePointVO[]): number {
  return nodes.reduce((sum, node) => {
    if (!node.children || node.children.length === 0) return sum + 1
    return sum + countLeaves(node.children)
  }, 0)
}

function getMaxDepth(nodes: KnowledgePointVO[], depth = 1): number {
  if (nodes.length === 0) return 0
  return Math.max(...nodes.map((node) => getMaxDepth(node.children || [], depth + 1) || depth))
}

const totalKP = computed(() => countNodes(treeData.value))
const rootCount = computed(() => treeData.value.length)
const leafCount = computed(() => countLeaves(treeData.value))
const maxDepth = computed(() => getMaxDepth(treeData.value))

async function fetchDetail() {
  loading.value = true
  try {
    const [courseRes, treeRes, libraryRes] = await Promise.all([
      getCourseById(courseId.value),
      getKnowledgeTree(courseId.value),
      getMyCourses(),
    ])
    course.value = courseRes.data
    treeData.value = treeRes.data || []
    isInLibrary.value = (libraryRes.data || []).some((item) => item.courseId === courseId.value)
  } catch {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}

function goToQuestions() {
  router.push({ name: 'QuestionList', query: { courseId: String(courseId.value) } })
}

function goToCourseOverview() {
  router.push({ name: 'CourseOverview', params: { id: courseId.value } })
}

function openTutor(knowledgePointId: number) {
  router.push({
    name: 'TutorSession',
    params: { id: courseId.value },
    query: { knowledgePointId: String(knowledgePointId) },
  })
}

async function addToLibrary() {
  if (addingToLibrary.value) return
  addingToLibrary.value = true
  try {
    await addCourseToLibrary(courseId.value)
    isInLibrary.value = true
    ElMessage.success('已加入课程库，正在进入课程总览。')
    await router.push({ name: 'CourseOverview', params: { id: courseId.value } })
  } catch {
    // 错误已在拦截器中处理
  } finally {
    addingToLibrary.value = false
  }
}

onMounted(() => {
  fetchDetail()
})
</script>

<style scoped>
.course-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-hero,
.knowledge-panel {
  background: var(--lp-surface);
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  box-shadow: var(--lp-shadow-sm);
}

.detail-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  padding: 22px;
}

.hero-main {
  min-width: 0;
}

.course-title-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 14px;
}

.course-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 50px;
  height: 50px;
  color: var(--lp-primary);
  background: var(--lp-primary-soft);
  border-radius: 8px;
  font-size: 25px;
  flex: 0 0 auto;
}

.section-kicker {
  color: var(--lp-primary);
  font-size: 12px;
  font-weight: 800;
}

.detail-hero h2 {
  margin: 4px 0 0;
  color: var(--lp-text);
  font-size: 24px;
  line-height: 1.25;
}

.detail-hero p {
  max-width: 760px;
  margin: 14px 0 0;
  color: var(--lp-text-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.hero-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

.detail-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.summary-card {
  min-height: 92px;
  padding: 16px;
  background: var(--lp-surface);
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  box-shadow: var(--lp-shadow-sm);
}

.summary-card span {
  display: block;
  color: var(--lp-text-muted);
  font-size: 13px;
}

.summary-card strong {
  display: block;
  margin-top: 8px;
  color: var(--lp-primary);
  font-size: 30px;
  line-height: 1;
}

.knowledge-panel {
  padding: 18px;
}

.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.panel-header h3 {
  margin: 4px 0 0;
  color: var(--lp-text);
  font-size: 18px;
}

.tree-wrap {
  min-height: 260px;
}

.tree-wrap :deep(.el-tree-node__content) {
  min-height: 42px;
  border-radius: 7px;
}

.tree-wrap :deep(.el-tree-node__content:hover) {
  background: var(--lp-surface-soft);
}

.tree-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: 1;
  min-width: 0;
  gap: 12px;
  padding: 4px 8px 4px 0;
}

.node-left,
.node-right {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 8px;
}

.node-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: var(--lp-primary);
  background: var(--lp-primary-soft);
  border-radius: 7px;
  flex: 0 0 auto;
}

.node-icon.leaf {
  color: var(--lp-success);
  background: #edf7f1;
}

.node-name {
  color: var(--lp-text);
  font-weight: 700;
}

.node-desc {
  overflow: hidden;
  max-width: 360px;
  color: var(--lp-text-muted);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 900px) {
  .detail-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 767px) {
  .detail-hero,
  .panel-header {
    align-items: stretch;
    flex-direction: column;
  }

  .hero-actions,
  .hero-actions .el-button {
    width: 100%;
  }

  .course-title-row {
    align-items: flex-start;
  }

  .knowledge-panel {
    padding: 14px;
  }

  .tree-node {
    align-items: flex-start;
    flex-direction: column;
  }

  .node-right {
    width: 100%;
    padding-left: 36px;
  }

  .node-desc {
    max-width: 100%;
    white-space: normal;
  }
}

@media (max-width: 480px) {
  .detail-summary {
    grid-template-columns: 1fr;
  }
}
</style>
