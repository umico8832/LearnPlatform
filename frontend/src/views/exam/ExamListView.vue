<template>
  <div class="exam-list-page page-container">
    <LpPageHeader title="考试与试卷">
      <template #actions>
        <el-button type="primary" :icon="Upload" @click="openImportDialog">导入私有试卷</el-button>
      </template>
    </LpPageHeader>

    <div v-if="papersState === 'ready' || recordsTotal !== null" class="exam-stat-row">
      <LpStat v-if="papersState === 'ready'" label="可用试卷" :value="total" :note="papersCountNote" tone="emphasis" />
      <LpStat v-if="recordsTotal !== null" label="考试记录" :value="recordsTotal" note="含进行中、已完成与待批阅" />
    </div>

    <section class="exam-panel">
      <el-tabs v-model="activeTab" class="exam-tabs" @tab-change="handleTabChange">
        <el-tab-pane label="可用试卷" name="papers">
          <div class="paper-filters" aria-label="筛选试卷">
            <el-select v-model="paperType" aria-label="试卷类型" @change="applyFilters">
              <el-option label="全部类型" value="ALL" />
              <el-option label="官方原题" value="OFFICIAL_EXAM" />
              <el-option label="普通练习" value="PRACTICE" />
              <el-option label="我的私有试卷" value="USER_PRIVATE" />
            </el-select>
            <el-input
              v-model="keyword"
              clearable
              maxlength="100"
              show-word-limit
              placeholder="按试卷名称查找"
              aria-label="按试卷名称查找"
              @clear="applyFilters"
              @keyup.enter="applyFilters"
            />
            <el-button type="primary" :loading="loading" @click="applyFilters">查找试卷</el-button>
            <el-button plain :disabled="!hasFilters || loading" @click="clearFilters">清除筛选</el-button>
          </div>

          <LpStatePanel
            :state="papersState"
            :title="papersStateTitle"
            :description="papersStateDescription"
            loading-label="正在读取可用试卷"
            retry-label="重新读取"
            :retrying="loading"
            @retry="loadPapers"
          >
            <template #default>
              <div class="paper-list">
                <article v-for="paper in papers" :key="paper.id" class="exam-card">
                  <div class="exam-card-top">
                    <div class="exam-card-heading">
                      <h3 class="exam-title">{{ paper.title }}</h3>
                      <el-tag :type="paperTypeTag(paper)" size="small">{{ paperTypeLabel(paper) }}</el-tag>
                    </div>
                    <el-tag type="success" size="small" class="exam-avail-tag">可参加</el-tag>
                  </div>

                  <p v-if="paper.description" class="exam-desc">{{ paper.description }}</p>

                  <div v-if="isVerifiedOfficial(paper)" class="official-source">
                    <strong>{{ paper.examYear }} · {{ paper.examName }}</strong>
                    <span>来源：{{ paper.sourceReference }}</span>
                  </div>
                  <div v-else-if="paper.visibility === 'PRIVATE'" class="private-source">
                    <strong>仅你可见 · 已确认导入</strong>
                    <el-button link type="primary" @click="showOriginalSource(paper.id)">查看原始资料</el-button>
                  </div>

                  <div class="exam-metrics">
                    <span v-if="paper.courseName"
                      ><el-icon><Reading /></el-icon>{{ paper.courseName }}</span
                    >
                    <span
                      ><el-icon><Document /></el-icon>{{ paper.questionCount }} 题</span
                    >
                    <span
                      ><el-icon><Timer /></el-icon>{{ paper.duration }} 分钟</span
                    >
                    <span
                      ><el-icon><Medal /></el-icon>{{ paper.totalScore }} 分</span
                    >
                  </div>

                  <p v-if="actionError?.paperId === paper.id" class="exam-action-error" role="alert">
                    {{ actionError.message }}
                    <button type="button" @click="retryAction(paper.id, actionError.action)">重试</button>
                  </p>
                  <div class="exam-actions">
                    <el-button
                      v-if="paper.visibility === 'PRIVATE'"
                      type="danger"
                      plain
                      :loading="deletingPaperId === paper.id"
                      :disabled="isPaperBusy(paper.id)"
                      @click="deletePaper(paper)"
                      >删除试卷</el-button
                    >
                    <el-button
                      v-if="paper.courseId"
                      :icon="Reading"
                      :loading="learningId === paper.id"
                      :disabled="isPaperBusy(paper.id) && learningId !== paper.id"
                      @click="handleStartLearning(paper.id)"
                      >学习模式</el-button
                    >
                    <el-button
                      type="primary"
                      :icon="EditPen"
                      :loading="startingId === paper.id"
                      :disabled="isPaperBusy(paper.id) && startingId !== paper.id"
                      @click="handleStartExam(paper.id)"
                      >考试模式</el-button
                    >
                  </div>
                </article>
              </div>
            </template>
            <template #actions>
              <el-button v-if="hasFilters" plain @click="clearFilters">清除筛选</el-button>
            </template>
          </LpStatePanel>

          <div v-if="papersState === 'ready' && total > 0" class="pagination-wrapper">
            <el-pagination
              v-model:current-page="pageNum"
              :total="total"
              :page-size="10"
              layout="total, prev, pager, next"
              @current-change="loadPapers"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane label="考试记录" name="records">
          <ExamRecordList ref="examRecordListRef" @total-change="recordsTotal = $event" />
        </el-tab-pane>
      </el-tabs>
    </section>

    <PrivateExamImportDialog
      ref="importDialogRef"
      v-model="importDialogVisible"
      :default-course-id="Number.isFinite(courseId) && courseId > 0 ? courseId : 0"
      @imported="onImported"
      @open-storage="openStorageDialog"
    />
    <PrivateExamSourceManager ref="sourceManagerRef" @content-deleted="onPrivateContentDeleted" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { Document, EditPen, Medal, Reading, Timer, Upload } from '@element-plus/icons-vue'
import { deletePrivateExamPaper, getPublishedPapers, startExam, startExamLearningSession } from '@/api/exam'
import { getAuthSessionVersion, isAuthenticated, onAuthSessionChange } from '@/utils/auth'
import type { ExamPaperVO, PaperType } from '@/api/exam'
import LpPageHeader from '@/components/ui/LpPageHeader.vue'
import LpStat from '@/components/ui/LpStat.vue'
import LpStatePanel from '@/components/ui/LpStatePanel.vue'
import ExamRecordList from '@/components/exam/ExamRecordList.vue'
import PrivateExamImportDialog from '@/components/exam/PrivateExamImportDialog.vue'
import PrivateExamSourceManager from '@/components/exam/PrivateExamSourceManager.vue'

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
  void loadPapers()
})

const unsubscribeAuth = onAuthSessionChange(() => {
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
    const confirmed = await ElMessageBox.confirm(
      `仅未产生考试、学习记录或衍生内容的私有试卷可删除。确认删除“${paper.title}”？`,
      '删除私有试卷',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
      .then(() => true)
      .catch(() => false)
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
  if (paper.visibility === 'PRIVATE') return 'warning'
  if (isVerifiedOfficial(paper)) return 'success'
  if (paper.paperType === 'OFFICIAL_EXAM') return 'warning'
  return 'info'
}
</script>

<style scoped>
.exam-list-page {
  display: flex;
  flex-direction: column;
  gap: var(--lp-space-5);
}

.exam-stat-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--lp-space-4);
}

.exam-panel {
  padding: var(--lp-space-2) var(--lp-space-5) var(--lp-space-5);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}

.exam-tabs :deep(.el-tabs__header) {
  margin-bottom: var(--lp-space-4);
}

.paper-filters {
  display: grid;
  grid-template-columns: minmax(150px, 0.36fr) minmax(220px, 1fr) auto auto;
  gap: var(--lp-space-3);
  align-items: center;
  margin-bottom: var(--lp-space-4);
}

.paper-list {
  min-height: 180px;
}

.exam-card {
  padding: var(--lp-space-5);
  margin-bottom: var(--lp-space-3);
  background: var(--lp-surface-subtle);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  transition:
    border-color var(--lp-duration-fast) var(--lp-ease-out),
    box-shadow var(--lp-duration-fast) var(--lp-ease-out);
}

.exam-card:hover {
  border-color: var(--lp-border-strong);
  box-shadow: var(--lp-shadow-sm);
}

.exam-card-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--lp-space-3);
  margin-bottom: var(--lp-space-3);
}

.exam-card-heading {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  flex-wrap: wrap;
  min-width: 0;
}

.exam-title {
  margin: 0;
  color: var(--lp-text);
  font-size: var(--lp-text-xl);
  line-height: var(--lp-leading-snug);
}

.exam-avail-tag {
  flex-shrink: 0;
}

.official-source,
.private-source {
  display: flex;
  flex-direction: column;
  gap: var(--lp-space-1);
  margin-bottom: var(--lp-space-4);
  padding: var(--lp-space-3) var(--lp-space-4);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  border-radius: var(--lp-radius-md);
}

.official-source {
  background: var(--lp-success-soft);
  border: 1px solid color-mix(in srgb, var(--lp-success) 24%, transparent);
}

.private-source {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  gap: var(--lp-space-3);
  background: var(--lp-warning-soft);
  border: 1px solid color-mix(in srgb, var(--lp-warning) 24%, transparent);
}

.official-source strong,
.private-source strong {
  color: var(--lp-text);
  font-weight: var(--lp-weight-semibold);
}

.exam-desc {
  margin: 0 0 var(--lp-space-3);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-base);
  line-height: var(--lp-leading-body);
}

.exam-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--lp-space-2);
  margin-bottom: var(--lp-space-4);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}

.exam-metrics span {
  display: flex;
  align-items: center;
  gap: var(--lp-space-2);
  min-height: 36px;
  padding: var(--lp-space-2) var(--lp-space-3);
  background: var(--lp-surface-soft);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-sm);
}

.exam-metrics .el-icon {
  color: var(--lp-primary);
  flex-shrink: 0;
}

.exam-action-error {
  display: flex;
  align-items: baseline;
  gap: var(--lp-space-2);
  margin: 0 0 var(--lp-space-3);
  color: var(--lp-danger);
  font-size: var(--lp-text-sm);
}

.exam-action-error button {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--lp-primary);
  font: inherit;
  text-decoration: underline;
  cursor: pointer;
}

.exam-action-error button:focus-visible {
  outline: var(--lp-focus-width) solid var(--lp-focus-ring);
  outline-offset: var(--lp-focus-offset);
  border-radius: var(--lp-radius-sm);
}

.exam-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--lp-space-2);
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--lp-space-4);
}

@media (max-width: 860px) {
  .paper-filters {
    grid-template-columns: 1fr;
  }

  .exam-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .exam-stat-row {
    grid-template-columns: 1fr;
  }

  .exam-panel {
    padding: var(--lp-space-3) var(--lp-space-4) var(--lp-space-4);
  }

  .exam-card-top {
    align-items: stretch;
    flex-direction: column;
  }

  .exam-metrics {
    grid-template-columns: 1fr;
  }

  .exam-actions .el-button {
    width: 100%;
    margin-left: 0;
  }
}
</style>
