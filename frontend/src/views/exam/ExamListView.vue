<template>
  <div class="lt exam-library" data-appearance="dark">
    <LtPageHeader title="考试与试卷" description="选一份试卷，开始学习或限时考试。"
      ><template #actions
        ><LtButton variant="primary" @click="openImportDialog">导入私有试卷</LtButton></template
      ></LtPageHeader
    >
    <p v-if="examNotice" class="notice" role="status">{{ examNotice }}</p>
    <p v-if="importNotice" class="muted" role="status">{{ importNotice }}</p>
    <div v-if="papersState === 'ready' || recordsTotal !== null" class="exam-stat-row">
      <div v-if="papersState === 'ready'">
        <strong>{{ total }}</strong
        >可用试卷 · {{ papersCountNote }}
      </div>
      <div v-if="recordsTotal !== null">
        <strong>{{ recordsTotal }}</strong
        >考试记录 · 含进行中、已完成与待批阅
      </div>
    </div>
    <nav class="exam-tabs" aria-label="考试内容">
      <LtButton
        :variant="activeTab === 'papers' ? 'primary' : 'plain'"
        :aria-pressed="activeTab === 'papers'"
        @click="activeTab = 'papers'"
        >可用试卷</LtButton
      ><LtButton
        :variant="activeTab === 'records' ? 'primary' : 'plain'"
        :aria-pressed="activeTab === 'records'"
        @click="showRecords"
        >考试记录</LtButton
      >
    </nav>
    <section v-show="activeTab === 'papers'">
      <form class="paper-filters" aria-label="筛选试卷" @submit.prevent="applyFilters">
        <select v-model="paperType" aria-label="试卷类型" @change="applyFilters">
          <option value="ALL">全部类型</option>
          <option value="OFFICIAL_EXAM">官方原题</option>
          <option value="PRACTICE">普通练习</option>
          <option value="USER_PRIVATE">我的私有试卷</option></select
        ><input
          v-model="keyword"
          type="text"
          maxlength="100"
          placeholder="按试卷名称查找"
          aria-label="按试卷名称查找"
        /><LtButton v-if="keyword" :disabled="loading" @click="clearKeyword">清空关键词</LtButton
        ><LtButton type="submit" :disabled="loading">查找试卷</LtButton
        ><LtButton :disabled="!hasFilters || loading" @click="clearFilters">清除筛选</LtButton>
      </form>
      <div v-if="papersState === 'loading'" class="state" role="status" aria-busy="true">正在读取可用试卷</div>
      <div v-else-if="papersState !== 'ready'" class="state" :role="papersState === 'error' ? 'alert' : 'status'">
        <h2>{{ papersStateTitle }}</h2>
        <p>{{ papersStateDescription }}</p>
        <LtButton v-if="papersState === 'error'" @click="loadPapers">重新读取</LtButton
        ><LtButton v-if="hasFilters" @click="clearFilters">清除筛选</LtButton>
      </div>
      <div v-else class="paper-list">
        <article v-for="paper in papers" :key="paper.id" class="exam-card">
          <div class="exam-card-top">
            <div class="exam-card-heading">
              <h2 class="exam-title">{{ paper.title }}</h2>
              <span class="tag" :data-tone="paperTypeTag(paper)">{{ paperTypeLabel(paper) }}</span>
            </div>
            <span class="tag">可参加</span>
          </div>
          <p v-if="paper.description" class="exam-desc">{{ paper.description }}</p>
          <div v-if="isVerifiedOfficial(paper)" class="official-source">
            <strong>{{ paper.examYear }} · {{ paper.examName }}</strong
            ><span>来源：{{ paper.sourceReference }}</span>
          </div>
          <div v-else-if="paper.visibility === 'PRIVATE'" class="private-source">
            <strong>仅你可见 · 已确认导入</strong
            ><LtButton @click="showOriginalSource(paper.id)">查看原始资料</LtButton>
          </div>
          <div class="exam-metrics">
            <span v-if="paper.courseName"><BookOpen aria-hidden="true" />{{ paper.courseName }}</span
            ><span><FileText aria-hidden="true" />{{ paper.questionCount }} 题</span
            ><span><Clock aria-hidden="true" />{{ paper.duration }} 分钟</span><span>{{ paper.totalScore }} 分</span>
          </div>
          <p v-if="actionError?.paperId === paper.id" class="exam-action-error" role="alert">
            {{ actionError.message }} <LtButton @click="retryAction(paper.id, actionError.action)">重试</LtButton>
          </p>
          <div class="exam-actions">
            <LtButton
              v-if="paper.visibility === 'PRIVATE'"
              class="danger"
              :disabled="isPaperBusy(paper.id)"
              @click="deletePaper(paper)"
              >删除试卷</LtButton
            ><LtButton v-if="paper.courseId" :disabled="isPaperBusy(paper.id)" @click="handleStartLearning(paper.id)">{{
              learningId === paper.id ? '正在开始…' : '学习模式'
            }}</LtButton
            ><LtButton variant="primary" :disabled="isPaperBusy(paper.id)" @click="handleStartExam(paper.id)">{{
              startingId === paper.id ? '正在开始…' : '考试模式'
            }}</LtButton>
          </div>
        </article>
      </div>
      <nav v-if="papersState === 'ready' && total > 0" class="pagination-wrapper" aria-label="试卷分页">
        <span>共 {{ total }} 份 · 第 {{ pageNum }} / {{ Math.ceil(total / 10) }} 页</span
        ><LtButton :disabled="pageNum <= 1" @click="changePage(-1)">上一页</LtButton
        ><LtButton :disabled="pageNum * 10 >= total" @click="changePage(1)">下一页</LtButton>
      </nav>
    </section>
    <section v-show="activeTab === 'records'">
      <ExamRecordList ref="examRecordListRef" @total-change="recordsTotal = $event" />
    </section>
    <PrivateExamImportDialog
      ref="importDialogRef"
      v-model="importDialogVisible"
      :default-course-id="Number.isFinite(courseId) && courseId > 0 ? courseId : 0"
      @imported="onImported"
      @open-storage="openStorageDialog"
      @notice="importNotice = $event"
    />
    <PrivateExamSourceManager ref="sourceManagerRef" @content-deleted="onPrivateContentDeleted" />
    <LtSheet v-model:open="deleteOpen" title="删除私有试卷"
      ><p>{{ deletePrompt }}</p>
      <div class="sheet-actions">
        <LtButton @click="deleteSheet.answer(false)">取消</LtButton
        ><LtButton class="danger" @click="deleteSheet.answer(true)">确认删除</LtButton>
      </div></LtSheet
    >
  </div>
</template>

<script setup lang="ts">
import LtPageHeader from '@/components/living/LtPageHeader.vue'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { LtButton, LtSheet } from '@/components/living'
import { useConfirmSheet } from '@/composables/useConfirmSheet'
import '@/components/exam/examLibrary.css'
import { BookOpen, FileText, Clock } from '@lucide/vue'
import { deletePrivateExamPaper, getPublishedPapers, startExam, startExamLearningSession } from '@/api/exam'
import { getAuthSessionVersion, isAuthenticated, onAuthSessionChange } from '@/utils/auth'
import type { ExamPaperVO, PaperType } from '@/api/exam'
import ExamRecordList from '@/components/exam/ExamRecordList.vue'
import { takeExamNotice } from './examNotice'
import PrivateExamImportDialog from '@/components/exam/PrivateExamImportDialog.vue'
import PrivateExamSourceManager from '@/components/exam/PrivateExamSourceManager.vue'

const examNotice = ref('')
const importNotice = ref('')
const deleteSheet = useConfirmSheet()
const deleteOpen = deleteSheet.open
const deletePrompt = ref('')
const router = useRouter()
const route = useRoute()
const activeTab = ref(route.query.tab === 'records' ? 'records' : 'papers')

// 试卷列表
const loading = ref(false)
const papersError = ref('')
const papers = ref<ExamPaperVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const paperType = ref<PaperType | 'ALL'>('ALL')
const keyword = ref('')
const startingId = ref<number | null>(null)
const learningId = ref<number | null>(null)
const courseId = Number(route.query.courseId)
const importDialogVisible = ref(false)
const importDialogRef = ref<InstanceType<typeof PrivateExamImportDialog> | null>(null)
const sourceManagerRef = ref<InstanceType<typeof PrivateExamSourceManager> | null>(null)
const deletingPaperId = ref<number | null>(null)
type PaperAction = 'exam' | 'learning' | 'delete'

const actionError = ref<{ paperId: number; action: PaperAction; message: string } | null>(null)
let alive = true
let requestGeneration = 0

// 考试记录
const recordsTotal = ref<number | null>(null)
const examRecordListRef = ref<InstanceType<typeof ExamRecordList> | null>(null)

const papersState = computed<'ready' | 'loading' | 'error' | 'empty'>(() => {
  if (loading.value) return 'loading'
  if (papersError.value) return 'error'
  return papers.value.length ? 'ready' : 'empty'
})
const hasFilters = computed(() => paperType.value !== 'ALL' || Boolean(keyword.value.trim()))
const papersCountNote = computed(() => (hasFilters.value ? '符合当前筛选条件的试卷' : '已发布试卷与已确认私有试卷'))
const papersStateTitle = computed(() => {
  if (papersState.value === 'empty') return hasFilters.value ? '没有符合筛选条件的试卷' : '暂无可用试卷'
  return '暂时无法读取试卷'
})
const papersStateDescription = computed(() => {
  if (papersState.value === 'empty') {
    return hasFilters.value ? '可调整名称关键词或试卷类型后重新查找。' : '可导入私有试卷，或稍后再试。'
  }
  return papersError.value || '请检查网络后重试。'
})

function current(generation: number, session: number) {
  return alive && generation === requestGeneration && session === getAuthSessionVersion()
}
function isPaperBusy(paperId: number) {
  return startingId.value === paperId || learningId.value === paperId || deletingPaperId.value === paperId
}

onMounted(() => {
  const notice = takeExamNotice()
  if (notice) examNotice.value = notice
  void loadPapers()
})

const unsubscribeAuth = onAuthSessionChange(() => {
  deleteSheet.answer(false)
  requestGeneration++
  papers.value = []
  total.value = 0
  papersError.value = ''
  actionError.value = null
  startingId.value = null
  learningId.value = null
  deletingPaperId.value = null
  recordsTotal.value = null
  if (isAuthenticated()) void loadPapers()
})

onUnmounted(() => {
  deleteSheet.answer(false)
  alive = false
  requestGeneration++
  unsubscribeAuth()
})

const openImportDialog = () => {
  importDialogVisible.value = true
}

const onImported = async () => {
  pageNum.value = 1
  await loadPapers()
}

const openStorageDialog = () => {
  void sourceManagerRef.value?.openStorage()
}

const onPrivateContentDeleted = () => {
  void importDialogRef.value?.reload()
  void loadPapers()
}

const deletePaper = async (paper: ExamPaperVO) => {
  if (isPaperBusy(paper.id)) return
  const generation = requestGeneration
  const session = getAuthSessionVersion()
  actionError.value = null
  deletingPaperId.value = paper.id
  try {
    deletePrompt.value = `仅未产生考试、学习记录或衍生内容的私有试卷可删除。确认删除“${paper.title}”？`
    const confirmed = await deleteSheet.ask()
    if (!confirmed || !current(generation, session) || deletingPaperId.value !== paper.id) return

    const res = await deletePrivateExamPaper(paper.id, { errorDisplay: 'inline' })
    if (!current(generation, session) || deletingPaperId.value !== paper.id) return
    if (res.code !== 0) {
      actionError.value = { paperId: paper.id, action: 'delete', message: res.message || '删除私有试卷失败。' }
      return
    }
    deletingPaperId.value = null
    await loadPapers()
  } catch {
    if (current(generation, session) && deletingPaperId.value === paper.id) {
      actionError.value = { paperId: paper.id, action: 'delete', message: '删除私有试卷失败。' }
    }
  } finally {
    if (current(generation, session) && deletingPaperId.value === paper.id) deletingPaperId.value = null
  }
}

const showOriginalSource = async (paperId: number) => {
  await sourceManagerRef.value?.openPaperSource(paperId)
}

async function loadPapers() {
  const generation = ++requestGeneration
  const session = getAuthSessionVersion()
  loading.value = true
  papersError.value = ''
  try {
    const res = await getPublishedPapers(
      {
        pageNum: pageNum.value,
        pageSize: 10,
        courseId: Number.isFinite(courseId) && courseId > 0 ? courseId : undefined,
        paperType: paperType.value === 'ALL' ? undefined : paperType.value,
        keyword: keyword.value.trim() || undefined,
      },
      { errorDisplay: 'inline' },
    )
    if (!current(generation, session)) return
    if (res.code !== 0 || !res.data) {
      papersError.value = res.message || '试卷列表暂时无法读取。'
      return
    }
    papers.value = res.data.records || []
    total.value = res.data.total || 0
  } catch {
    if (current(generation, session)) papersError.value = '试卷列表暂时无法读取。'
  } finally {
    if (current(generation, session)) loading.value = false
  }
}

function clearKeyword() {
  keyword.value = ''
  applyFilters()
}
function applyFilters() {
  pageNum.value = 1
  void loadPapers()
}

function clearFilters() {
  if (!hasFilters.value) return
  paperType.value = 'ALL'
  keyword.value = ''
  pageNum.value = 1
  void loadPapers()
}

function showRecords() {
  activeTab.value = 'records'
  handleTabChange('records')
}
const handleTabChange = (tab: string | number) => {
  if (tab === 'records') void examRecordListRef.value?.reload()
}

async function handleStartExam(paperId: number) {
  if (isPaperBusy(paperId)) return
  const session = getAuthSessionVersion()
  actionError.value = null
  startingId.value = paperId
  try {
    const startRes = await startExam(paperId, { errorDisplay: 'inline' })
    if (!current(requestGeneration, session) || startingId.value !== paperId) return
    if (startRes.code === 0 && startRes.data) {
      await router.push({ name: 'ExamTake', params: { recordId: String(startRes.data.id) } })
      return
    }
    actionError.value = { paperId, action: 'exam', message: startRes.message || '开始考试失败。' }
  } catch {
    if (current(requestGeneration, session)) actionError.value = { paperId, action: 'exam', message: '开始考试失败。' }
  } finally {
    if (current(requestGeneration, session) && startingId.value === paperId) startingId.value = null
  }
}

async function handleStartLearning(paperId: number) {
  if (isPaperBusy(paperId)) return
  const session = getAuthSessionVersion()
  actionError.value = null
  learningId.value = paperId
  try {
    const res = await startExamLearningSession(paperId, { errorDisplay: 'inline' })
    if (!current(requestGeneration, session) || learningId.value !== paperId) return
    if (res.code === 0 && res.data) {
      await router.push({ name: 'ExamLearning', params: { sessionId: String(res.data.id) } })
      return
    }
    actionError.value = { paperId, action: 'learning', message: res.message || '开始试卷学习失败。' }
  } catch {
    if (current(requestGeneration, session)) {
      actionError.value = { paperId, action: 'learning', message: '开始试卷学习失败，请确认课程已加入课程库。' }
    }
  } finally {
    if (current(requestGeneration, session) && learningId.value === paperId) learningId.value = null
  }
}

function retryAction(paperId: number, action: PaperAction) {
  if (action === 'exam') void handleStartExam(paperId)
  else if (action === 'learning') void handleStartLearning(paperId)
  else {
    const paper = papers.value.find((item) => item.id === paperId)
    if (paper) void deletePaper(paper)
  }
}

const isVerifiedOfficial = (paper: ExamPaperVO) => {
  return paper.paperType === 'OFFICIAL_EXAM' && paper.sourceVerified
}

const paperTypeLabel = (paper: ExamPaperVO) => {
  if (paper.visibility === 'PRIVATE') return '我的私有试卷'
  if (isVerifiedOfficial(paper)) return '官方原题'
  if (paper.paperType === 'OFFICIAL_EXAM') return '来源未核验'
  return '普通练习'
}

const paperTypeTag = (paper: ExamPaperVO) => {
  if (paper.visibility === 'PRIVATE') return 'info'
  if (isVerifiedOfficial(paper)) return 'success'
  if (paper.paperType === 'OFFICIAL_EXAM') return 'warning'
  return 'info'
}
function changePage(delta: number) {
  pageNum.value += delta
  void loadPapers()
}
</script>
