<template>
  <div class="lt course-page" data-appearance="dark">
    <CoursePageState v-if="loading" state="loading" title="正在读取课程详情" />
    <CoursePageState
      v-else-if="loadFailed || !course"
      state="error"
      title="无法读取课程详情"
      description="课程目录尚未加载，请重试。"
      @retry="fetchDetail"
    />
    <template v-else>
      <LtPageHeader
        class="detail-hero"
        :title="course.name"
        :description="course.description || '暂无课程描述'"
        title-id="course-title"
      >
        <template #lead><LtBackLink :to="backTarget" :label="backLabel" /></template>
        <template #actions>
          <div class="course-actions">
            <LtButton @click="goToQuestions">查看题目</LtButton>
            <LtButton v-if="membershipState === 'ready' && isInLibrary" variant="primary" @click="goToCourseOverview"
              >进入课程空间</LtButton
            >
            <LtButton
              v-else
              variant="primary"
              :disabled="membershipState !== 'ready' || addingToLibrary"
              @click="addToLibrary"
              >{{ addingToLibrary ? '正在加入…' : '加入课程库' }}</LtButton
            >
          </div>
        </template>
        <template #default>
          <p class="detail-meta">
            {{ totalKP }} 个知识点<template v-if="membershipState === 'ready' && isInLibrary">
              · 已加入我的课程</template
            ><template v-else-if="membershipState === 'loading'"> · 正在确认加入状态</template>
          </p>
          <p v-if="membershipState === 'error'" class="course-error" role="alert">
            暂时无法确认是否已加入课程。<LtButton variant="quiet" @click="() => loadMembership()">重新确认</LtButton>
          </p>
          <p v-if="joinError" class="course-error" role="alert">{{ joinError }}</p>
        </template>
      </LtPageHeader>
      <section class="knowledge-section" aria-labelledby="knowledge-heading">
        <div class="section-head">
          <h2 id="knowledge-heading">课程目录</h2>
          <span class="course-muted">共 {{ totalKP }} 个知识点</span>
        </div>
        <LtCard v-if="treeData.length" radius="xl" padding="none">
          <ul class="outline-list">
            <CourseOutlineNode
              v-for="node in treeData"
              :key="node.id"
              :node="node"
              :can-study="membershipState === 'ready' && isInLibrary"
              @study="openTutor"
            />
          </ul>
        </LtCard>
        <CoursePageState v-else state="empty" title="暂无知识点" description="这门课程还没有录入知识结构。"
          ><LtButton @click="goToQuestions">查看课程题目</LtButton></CoursePageState
        >
      </section>
    </template>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { LtBackLink, LtButton, LtCard, LtPageHeader } from '@/components/living'
import CoursePageState from './CoursePageState.vue'
import CourseOutlineNode from './CourseOutlineNode.vue'
import './course-baseline.css'
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
const backTarget = computed(() =>
  fromLearningSpace.value ? { name: 'CourseOverview', params: { id: courseId.value } } : { name: 'CourseList' },
)
const backLabel = computed(() => (fromLearningSpace.value ? '返回课程空间' : '返回课程库'))
const totalKP = computed(() => countNodes(treeData.value))

function isCurrent(requestGeneration: number, session: number) {
  return alive && requestGeneration === generation && session === getAuthSessionVersion()
}
function countNodes(nodes: KnowledgePointVO[]): number {
  return nodes.reduce((sum, node) => sum + 1 + countNodes(node.children || []), 0)
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
      getKnowledgeTree(courseId.value, { errorDisplay: 'inline' }),
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
.detail-meta {
  margin: var(--lt-space-4) 0 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.knowledge-section {
  display: grid;
  gap: var(--lt-space-4);
}
.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--lt-space-3);
}
.section-head h2 {
  margin: 0;
}
.outline-list {
  list-style: none;
  margin: 0;
  padding: var(--lt-space-2) var(--lt-space-6);
}
</style>
