<template>
  <div class="course-detail page-container">
    <LpStatePanel v-if="loading" state="loading" loading-label="正在读取课程详情" />
    <LpStatePanel
      v-else-if="loadFailed || !course"
      state="error"
      title="无法读取课程详情"
      description="课程目录尚未加载，请重试。"
      @retry="fetchDetail"
    >
      <template #retry
        ><el-button @click="returnToSource">{{ backLabel }}</el-button
        ><el-button type="primary" @click="fetchDetail">重新加载</el-button></template
      >
    </LpStatePanel>

    <template v-else>
      <div class="back-row">
        <el-button text :icon="ArrowLeft" @click="returnToSource">{{ backLabel }}</el-button>
      </div>
      <section class="detail-hero" aria-labelledby="course-title">
        <div class="hero-main">
          <h1 id="course-title" class="detail-title">{{ course.name }}</h1>
          <p class="detail-desc">{{ course.description || '暂无课程描述' }}</p>
          <p class="detail-meta">
            {{ totalKP }} 个知识点
            <template v-if="membershipState === 'ready' && isInLibrary"> · 已加入我的课程</template>
            <template v-else-if="membershipState === 'loading'"> · 正在确认加入状态</template>
          </p>
          <p v-if="membershipState === 'error'" class="membership-error" role="alert">
            暂时无法确认是否已加入课程。
            <button type="button" @click="() => loadMembership()">重新确认</button>
          </p>
          <p v-if="joinError" class="membership-error" role="alert">{{ joinError }}</p>
        </div>
        <div class="hero-actions">
          <el-button :icon="Collection" @click="goToQuestions">查看题目</el-button>
          <el-button
            v-if="membershipState === 'ready' && isInLibrary"
            type="primary"
            :icon="Reading"
            @click="goToCourseOverview"
            >进入课程空间</el-button
          >
          <el-button
            v-else
            type="primary"
            :icon="Plus"
            :loading="addingToLibrary"
            :disabled="membershipState !== 'ready'"
            @click="addToLibrary"
            >加入课程库</el-button
          >
        </div>
      </section>

      <section class="knowledge-section" aria-labelledby="knowledge-heading">
        <LpSectionHeading heading-id="knowledge-heading" title="课程目录" :description="`共 ${totalKP} 个知识点`" />
        <div v-if="treeData.length" class="tree-wrap">
          <el-tree
            :data="treeData"
            :props="{ children: 'children', label: 'name' }"
            node-key="id"
            :expand-on-click-node="false"
          >
            <template #default="{ data }">
              <div class="tree-node">
                <div class="node-left">
                  <span class="node-icon" :class="{ leaf: !data.children?.length }" aria-hidden="true">
                    <el-icon v-if="data.children?.length"><Folder /></el-icon><el-icon v-else><Document /></el-icon>
                  </span>
                  <span class="node-name">{{ data.name }}</span>
                </div>
                <div class="node-right">
                  <span v-if="data.description" class="node-desc">{{ data.description }}</span>
                  <el-button
                    v-if="isReviewedTutorContent(data) && membershipState === 'ready' && isInLibrary"
                    size="small"
                    plain
                    @click.stop="openTutor(data.id)"
                    >开始学习</el-button
                  >
                </div>
              </div>
            </template>
          </el-tree>
        </div>
        <LpEmptyState v-else title="暂无知识点" description="这门课程还没有录入知识结构。">
          <template #actions><el-button @click="goToQuestions">查看课程题目</el-button></template>
        </LpEmptyState>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Collection, Document, Folder, Plus, Reading } from '@element-plus/icons-vue'
import { addCourseToLibrary, getCourseById, getMyCourses, type CourseVO } from '@/api/course'
import { getKnowledgeTree, type KnowledgePointVO } from '@/api/knowledgePoint'
import { getAuthSessionVersion, isAuthenticated, onAuthSessionChange } from '@/utils/auth'
import { errorMessage } from '@/utils/errors'

const route = useRoute()
const router = useRouter()
const course = ref<CourseVO | null>(null)
const treeData = ref<KnowledgePointVO[]>([])
const loading = ref(false)
const loadFailed = ref(false)
const addingToLibrary = ref(false)
const isInLibrary = ref(false)
const membershipState = ref<'loading' | 'ready' | 'error'>('loading')
const joinError = ref('')
let alive = true
let generation = 0

const courseId = computed(() => Number(route.params.id))
const fromLearningSpace = computed(() => route.query?.from === 'learning-space')
const backLabel = computed(() => (fromLearningSpace.value ? '返回课程空间' : '返回课程库'))
const totalKP = computed(() => countNodes(treeData.value))

function isCurrent(requestGeneration: number, session: number) {
  return alive && requestGeneration === generation && session === getAuthSessionVersion()
}
function countNodes(nodes: KnowledgePointVO[]): number {
  return nodes.reduce((sum, node) => sum + 1 + countNodes(node.children || []), 0)
}
function isReviewedTutorContent(node: KnowledgePointVO) {
  return !!node.contentKey && node.contentReviewStatus === 'REVIEWED'
}

async function loadMembership(requestGeneration = generation, session = getAuthSessionVersion()) {
  membershipState.value = 'loading'
  try {
    const response = await getMyCourses({ errorDisplay: 'inline' })
    if (!isCurrent(requestGeneration, session)) return
    isInLibrary.value = (response.data || []).some((item) => item.courseId === courseId.value)
    membershipState.value = 'ready'
  } catch {
    if (isCurrent(requestGeneration, session)) membershipState.value = 'error'
  }
}

async function fetchDetail() {
  const requestGeneration = ++generation
  const session = getAuthSessionVersion()
  loading.value = true
  loadFailed.value = false
  joinError.value = ''
  membershipState.value = 'loading'
  try {
    const [courseResponse, treeResponse] = await Promise.all([
      getCourseById(courseId.value, { errorDisplay: 'inline' }),
      getKnowledgeTree(courseId.value),
    ])
    if (!isCurrent(requestGeneration, session)) return
    course.value = courseResponse.data
    treeData.value = treeResponse.data || []
    void loadMembership(requestGeneration, session)
  } catch {
    if (isCurrent(requestGeneration, session)) loadFailed.value = true
  } finally {
    if (isCurrent(requestGeneration, session)) loading.value = false
  }
}

function returnToSource() {
  router.push(
    fromLearningSpace.value ? { name: 'CourseOverview', params: { id: courseId.value } } : { name: 'CourseList' },
  )
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
  if (addingToLibrary.value || membershipState.value !== 'ready') return
  const requestGeneration = generation
  const session = getAuthSessionVersion()
  addingToLibrary.value = true
  joinError.value = ''
  try {
    await addCourseToLibrary(courseId.value, { errorDisplay: 'inline' })
    if (!isCurrent(requestGeneration, session)) return
    isInLibrary.value = true
    await router.push({ name: 'CourseOverview', params: { id: courseId.value } })
  } catch (error) {
    if (isCurrent(requestGeneration, session)) joinError.value = errorMessage(error, '加入课程失败，请重试。')
  } finally {
    if (isCurrent(requestGeneration, session)) addingToLibrary.value = false
  }
}

const unsubscribeSession = onAuthSessionChange(() => {
  generation++
  course.value = null
  treeData.value = []
  membershipState.value = 'loading'
  joinError.value = ''
  if (isAuthenticated()) void fetchDetail()
})
watch(courseId, () => void fetchDetail())
onMounted(() => void fetchDetail())
onUnmounted(() => {
  alive = false
  generation++
  unsubscribeSession()
})
</script>

<style scoped>
.course-detail {
  display: grid;
  gap: var(--lp-space-6);
}
.back-row {
  display: flex;
}
.detail-hero,
.knowledge-section {
  display: grid;
  gap: var(--lp-space-5);
  padding: var(--lp-space-6);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  background: var(--lp-surface);
  box-shadow: var(--lp-shadow-xs);
}
.detail-hero {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
}
.detail-title {
  margin: 0;
  color: var(--lp-text);
  font-size: var(--lp-text-3xl);
  line-height: var(--lp-leading-display);
}
.detail-desc {
  max-width: 720px;
  margin: var(--lp-space-3) 0 0;
  color: var(--lp-text-secondary);
  line-height: var(--lp-leading-relaxed);
}
.detail-meta,
.membership-error {
  margin: var(--lp-space-3) 0 0;
  color: var(--lp-text-muted);
  font-size: var(--lp-text-sm);
}
.membership-error {
  color: var(--lp-danger);
}
.membership-error button {
  padding: 0;
  border: 0;
  color: inherit;
  background: transparent;
  font: inherit;
  text-decoration: underline;
  cursor: pointer;
}
.hero-actions {
  display: flex;
  gap: var(--lp-space-3);
  flex-wrap: wrap;
  justify-content: flex-end;
}
.tree-wrap {
  min-height: 120px;
}
.tree-wrap :deep(.el-tree-node__content) {
  min-height: var(--lp-control-height-large);
  border-radius: var(--lp-radius-sm);
}
.tree-wrap :deep(.el-tree-node__content:hover),
.tree-wrap :deep(.el-tree-node:focus > .el-tree-node__content) {
  background: var(--lp-surface-hover);
}
.tree-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: 1;
  min-width: 0;
  gap: var(--lp-space-3);
  padding-right: var(--lp-space-2);
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
}
.node-icon.leaf {
  color: var(--lp-success);
  background: var(--lp-success-soft);
}
.node-name {
  color: var(--lp-text);
  font-weight: var(--lp-weight-semibold);
}
.node-desc {
  overflow: hidden;
  max-width: 320px;
  color: var(--lp-text-muted);
  font-size: var(--lp-text-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
}
@media (max-width: 900px) {
  .detail-hero {
    grid-template-columns: 1fr;
    align-items: stretch;
  }
  .hero-actions {
    justify-content: flex-start;
  }
}
</style>
