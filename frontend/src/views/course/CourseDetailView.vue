<template>
  <div class="course-detail page-container">
    <template v-if="loading">
      <LpSkeleton card :rows="4" />
      <LpSkeleton card :rows="6" />
    </template>

    <template v-else-if="loadFailed || !course">
      <section class="state-panel">
        <LpEmptyState title="无法读取课程详情" description="请刷新重试。">
          <template #actions>
            <el-button @click="router.back()">返回</el-button>
            <el-button type="primary" @click="fetchDetail">重新加载</el-button>
          </template>
        </LpEmptyState>
      </section>
    </template>

    <template v-else>
      <div class="back-row">
        <el-button text :icon="ArrowLeft" @click="router.back()">返回课程库</el-button>
      </div>

      <section class="detail-hero">
        <div class="hero-main">
          <LpKicker>课程详情</LpKicker>
          <h1 class="detail-title">{{ course.name }}</h1>
          <p class="detail-desc">{{ course.description || '暂无课程描述，可先查看知识点结构。' }}</p>
          <div class="detail-meta">
            <span>{{ totalKP }} 个知识点</span>
            <span v-if="isInLibrary" class="in-library">
              <el-icon :size="13"><CircleCheck /></el-icon> 已加入我的课程
            </span>
          </div>
        </div>
        <div class="hero-actions">
          <el-button :icon="Collection" @click="goToQuestions">查看题目</el-button>
          <el-button v-if="isInLibrary" type="primary" :icon="Reading" @click="goToCourseOverview">
            进入课程空间
          </el-button>
          <el-button
            v-else
            type="primary"
            :icon="Plus"
            :loading="addingToLibrary"
            :disabled="loading"
            @click="addToLibrary"
          >
            加入课程库
          </el-button>
        </div>
      </section>

      <section class="knowledge-section" aria-labelledby="knowledge-heading">
        <LpSectionHeading
          kicker="知识结构"
          title="课程目录"
          :description="'共 ' + totalKP + ' 个知识点，按模块组织。已审查的教学内容可以直接开始学习。'"
        >
          <template #aside>
            <span class="tree-hint">加入课程库后可开始已审查内容的 AI 教学</span>
          </template>
        </LpSectionHeading>

        <div class="tree-wrap">
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
                  <span
                    class="node-icon"
                    :class="{ leaf: !data.children || data.children.length === 0 }"
                    aria-hidden="true"
                  >
                    <el-icon v-if="data.children && data.children.length > 0"><Folder /></el-icon>
                    <el-icon v-else><Document /></el-icon>
                  </span>
                  <span class="node-name">{{ data.name }}</span>
                </div>
                <div class="node-right">
                  <span v-if="data.description" class="node-desc">{{ data.description }}</span>
                  <span v-if="data.children && data.children.length > 0" class="node-count">
                    {{ data.children.length }} 项
                  </span>
                  <el-button
                    v-if="isReviewedTutorContent(data) && isInLibrary"
                    size="small"
                    type="primary"
                    plain
                    @click.stop="openTutor(data.id)"
                  >
                    开始学习
                  </el-button>
                  <span v-else-if="isReviewedTutorContent(data)" class="join-hint">加入课程库后可学习</span>
                </div>
              </div>
            </template>
          </el-tree>

          <LpEmptyState v-else title="暂无知识点" description="这门课程还没有录入知识结构。">
            <template #actions>
              <el-button type="primary" @click="goToQuestions">先看课程题目</el-button>
            </template>
          </LpEmptyState>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, CircleCheck, Collection, Document, Folder, Plus, Reading } from '@element-plus/icons-vue'
import { addCourseToLibrary, getCourseById, getMyCourses, type CourseVO } from '@/api/course'
import { getKnowledgeTree, type KnowledgePointVO } from '@/api/knowledgePoint'

const route = useRoute()
const router = useRouter()

const course = ref<CourseVO | null>(null)
const treeData = ref<KnowledgePointVO[]>([])
const loading = ref(false)
const loadFailed = ref(false)
const addingToLibrary = ref(false)
const isInLibrary = ref(false)

const courseId = computed(() => Number(route.params.id))

/** 只有服务端标记为已审查的内容才能开始 AI 教学。 */
function isReviewedTutorContent(node: KnowledgePointVO) {
  return node.contentKey !== undefined && node.contentKey !== null && node.contentReviewStatus === 'REVIEWED'
}

function countNodes(nodes: KnowledgePointVO[]): number {
  return nodes.reduce((sum, node) => sum + 1 + countNodes(node.children || []), 0)
}

const totalKP = computed(() => countNodes(treeData.value))

async function fetchDetail() {
  loading.value = true
  loadFailed.value = false
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
    loadFailed.value = true
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
    ElMessage.success('已加入课程库，正在进入课程空间。')
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
  gap: var(--lp-space-6);
}

.back-row {
  display: flex;
  align-items: center;
}

.state-panel {
  padding: var(--lp-space-6) 0;
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}

.detail-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--lp-space-6);
  padding: var(--lp-space-6) var(--lp-space-8);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}

.hero-main {
  min-width: 0;
}

.detail-title {
  margin-top: var(--lp-space-2);
  font-family: var(--lp-font-display);
  font-size: var(--lp-text-4xl);
  font-weight: var(--lp-weight-bold);
  line-height: var(--lp-leading-display);
  color: var(--lp-text);
}

.detail-desc {
  margin: var(--lp-space-3) 0 0;
  max-width: 720px;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-md);
  line-height: var(--lp-leading-relaxed);
}

.detail-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--lp-space-2) var(--lp-space-4);
  margin-top: var(--lp-space-4);
  color: var(--lp-text-muted);
  font-size: var(--lp-text-sm);
}

.in-library {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--lp-success);
  font-weight: var(--lp-weight-medium);
}

.hero-actions {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  flex-shrink: 0;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.knowledge-section {
  display: grid;
  gap: var(--lp-space-4);
  padding: var(--lp-space-6);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}

.tree-hint {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-sm);
}

.tree-wrap {
  min-height: 200px;
}

.tree-wrap :deep(.el-tree-node__content) {
  min-height: 44px;
  border-radius: var(--lp-radius-sm);
}

.tree-wrap :deep(.el-tree-node__content:hover) {
  background: var(--lp-surface-soft);
}

.tree-wrap :deep(.el-tree-node:focus > .el-tree-node__content) {
  background: var(--lp-surface-soft);
}

.tree-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: 1;
  min-width: 0;
  gap: var(--lp-space-3);
  padding: 4px 8px 4px 0;
}

.node-left,
.node-right {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: var(--lp-space-2);
}

.node-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: var(--lp-primary);
  background: var(--lp-primary-soft);
  border-radius: var(--lp-radius-sm);
  flex: 0 0 auto;
}

.node-icon.leaf {
  color: var(--lp-success);
  background: var(--lp-success-soft);
}

.node-name {
  color: var(--lp-text);
  font-weight: var(--lp-weight-semibold);
  font-size: var(--lp-text-base);
}

.node-desc {
  overflow: hidden;
  max-width: 340px;
  color: var(--lp-text-muted);
  font-size: var(--lp-text-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-count {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
}

.join-hint {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
  white-space: nowrap;
}

@media (max-width: 900px) {
  .detail-hero {
    align-items: stretch;
    flex-direction: column;
  }
  .hero-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 767px) {
  .detail-hero {
    padding: var(--lp-space-5);
  }
  .detail-title {
    font-size: var(--lp-text-3xl);
  }
  .knowledge-section {
    padding: var(--lp-space-4);
  }
  .tree-node {
    align-items: flex-start;
    flex-direction: column;
  }
  .node-right {
    width: 100%;
    padding-left: 36px;
    flex-wrap: wrap;
  }
  .node-desc {
    max-width: 100%;
    white-space: normal;
  }
}
</style>
