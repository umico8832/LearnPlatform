<template>
  <div class="exam-list-page page-container">
    <LpPageHeader
      kicker="考试测评"
      title="考试与试卷"
      description="先参加已发布试卷，完成后在考试记录中查看得分和答题明细。"
    >
      <template #actions>
        <el-button type="primary" :icon="Upload" @click="openImportDialog">导入私有试卷</el-button>
      </template>
    </LpPageHeader>

    <div class="exam-stat-row">
      <LpStat label="可用试卷" :value="total" note="已发布试卷与已确认私有试卷" tone="emphasis" />
      <LpStat label="考试记录" :value="recordsTotal" note="含进行中、已完成与待批阅" />
    </div>

    <section class="exam-panel">
      <el-tabs v-model="activeTab" class="exam-tabs" @tab-change="handleTabChange">
        <el-tab-pane label="可用试卷" name="papers">
          <div v-loading="loading" class="paper-list">
            <LpEmptyState
              v-if="!loading && papers.length === 0"
              title="暂无可用试卷"
              description="管理员发布试卷，或导入你自己的私有试卷后，就会出现在这里。"
            />

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
                <span v-if="paper.courseName">
                  <el-icon><Reading /></el-icon>{{ paper.courseName }}
                </span>
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

              <div class="exam-actions">
                <el-button
                  v-if="paper.visibility === 'PRIVATE'"
                  type="danger"
                  plain
                  :loading="deletingPaperId === paper.id"
                  @click="deletePaper(paper)"
                >
                  删除试卷
                </el-button>
                <el-button
                  v-if="paper.courseId"
                  :icon="Reading"
                  :loading="learningId === paper.id"
                  @click="handleStartLearning(paper.id)"
                >
                  学习模式
                </el-button>
                <el-button
                  type="primary"
                  :icon="EditPen"
                  :loading="startingId === paper.id"
                  @click="handleStartExam(paper.id)"
                >
                  考试模式
                </el-button>
              </div>
            </article>
          </div>

          <div class="pagination-wrapper" v-if="total > 0">
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

    <el-dialog v-model="storageDialogVisible" title="我的原文件存储" width="min(760px, 92vw)" append-to-body>
      <p class="source-meta">这里只展示原文件元数据；下载和删除始终通过当前关联的草稿或私有试卷处理。</p>
      <div v-loading="storageFilesLoading" class="storage-list">
        <LpEmptyState
          v-if="!storageFilesLoading && !storageFiles.length"
          title="暂无已保存的 PDF 或 DOCX 原文件"
          compact
        />
        <article v-for="item in storageFiles" :key="item.id" class="storage-item">
          <div class="storage-item-main">
            <div class="storage-item-title">
              <strong>{{ item.sourceName }}</strong>
              <el-tag size="small">{{ item.sourceFormat }}</el-tag>
            </div>
            <p>{{ formatStorage(item.sourceSize) }} · {{ formatTime(item.createTime) }}</p>
            <p>{{ storageAssociationLabel(item) }}</p>
          </div>
          <div class="storage-item-actions">
            <el-button
              v-if="item.associationType !== 'UNREFERENCED'"
              plain
              :loading="storageDownloadingId === item.id"
              @click="downloadStorageItem(item)"
            >
              下载
            </el-button>
            <el-button
              v-if="item.associationType !== 'UNREFERENCED'"
              type="danger"
              plain
              :loading="storageDeletingId === item.id"
              @click="deleteStorageItem(item)"
            >
              删除关联内容
            </el-button>
          </div>
        </article>
      </div>
      <div v-if="storageFilesTotal > 10" class="pagination-wrapper">
        <el-pagination
          v-model:current-page="storageFilesPage"
          :total="storageFilesTotal"
          :page-size="10"
          layout="total, prev, pager, next"
          @current-change="loadStorageFiles"
        />
      </div>
    </el-dialog>

    <el-dialog v-model="sourceDialogVisible" title="私有试卷原始资料" width="min(760px, 92vw)">
      <template v-if="privateSource">
        <p class="source-meta">
          {{ privateSource.sourceName }} · {{ privateSource.sourceFormat }} · {{ privateSource.contentHash }}
        </p>
        <el-button
          v-if="privateSource.originalFileAvailable"
          type="primary"
          plain
          :loading="sourceDownloading"
          @click="downloadPaperSource"
        >
          下载原文件
        </el-button>
        <pre class="source-content">{{ privateSource.originalContent }}</pre>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document, EditPen, Medal, Reading, Timer, Upload } from '@element-plus/icons-vue'
import {
  deletePrivateExamDraft,
  deletePrivateExamPaper,
  downloadPrivateExamDraftSourceFile,
  downloadPrivateExamSourceFile,
  getPrivateExamStorageFiles,
  getPrivateExamSource,
  getPublishedPapers,
  startExam,
  startExamLearningSession,
} from '@/api/exam'
import type { ExamPaperVO, PrivateExamSource, PrivateExamSourceStorageItem } from '@/api/exam'
import { formatTime, formatStorage } from '@/utils/format'
import LpPageHeader from '@/components/ui/LpPageHeader.vue'
import LpStat from '@/components/ui/LpStat.vue'
import LpEmptyState from '@/components/ui/LpEmptyState.vue'
import ExamRecordList from '@/components/exam/ExamRecordList.vue'
import PrivateExamImportDialog from '@/components/exam/PrivateExamImportDialog.vue'

const router = useRouter()
const route = useRoute()
const activeTab = ref(route.query.tab === 'records' ? 'records' : 'papers')

// 试卷列表
const loading = ref(false)
const papers = ref<ExamPaperVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const startingId = ref<number | null>(null)
const learningId = ref<number | null>(null)
const courseId = Number(route.query.courseId)
const importDialogVisible = ref(false)
const importDialogRef = ref<InstanceType<typeof PrivateExamImportDialog> | null>(null)
const deletingPaperId = ref<number | null>(null)

// 原始资料
const sourceDialogVisible = ref(false)
const privateSource = ref<PrivateExamSource | null>(null)
const sourceDownloading = ref(false)

// 原文件存储
const storageDialogVisible = ref(false)
const storageFilesLoading = ref(false)
const storageFiles = ref<PrivateExamSourceStorageItem[]>([])
const storageFilesTotal = ref(0)
const storageFilesPage = ref(1)
const storageDownloadingId = ref<number | null>(null)
const storageDeletingId = ref<number | null>(null)

// 考试记录
const recordsTotal = ref(0)
const examRecordListRef = ref<InstanceType<typeof ExamRecordList> | null>(null)

onMounted(() => {
  loadPapers()
})

const openImportDialog = () => {
  importDialogVisible.value = true
}

const onImported = async () => {
  pageNum.value = 1
  await loadPapers()
}

const openStorageDialog = async () => {
  storageFilesPage.value = 1
  storageDialogVisible.value = true
  await loadStorageFiles()
}

async function loadStorageFiles() {
  storageFilesLoading.value = true
  try {
    const res = await getPrivateExamStorageFiles({ pageNum: storageFilesPage.value, pageSize: 10 })
    storageFiles.value = res.code === 0 && res.data ? res.data.records : []
    storageFilesTotal.value = res.code === 0 && res.data ? res.data.total : 0
  } catch {
    storageFiles.value = []
    storageFilesTotal.value = 0
    ElMessage.error('获取原文件清单失败')
  } finally {
    storageFilesLoading.value = false
  }
}

const deletePaper = async (paper: ExamPaperVO) => {
  const confirmed = await ElMessageBox.confirm(
    `仅未产生考试、学习记录或衍生内容的私有试卷可删除。确认删除“${paper.title}”？`,
    '删除私有试卷',
    { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
  )
    .then(() => true)
    .catch(() => false)
  if (!confirmed) return
  deletingPaperId.value = paper.id
  try {
    const res = await deletePrivateExamPaper(paper.id)
    if (res.code === 0) {
      ElMessage.success('私有试卷已删除')
      await loadPapers()
    }
  } finally {
    deletingPaperId.value = null
  }
}

const showOriginalSource = async (paperId: number) => {
  try {
    const res = await getPrivateExamSource(paperId)
    if (res.code === 0 && res.data) {
      privateSource.value = res.data
      sourceDialogVisible.value = true
    }
  } catch {
    ElMessage.error('原始资料不可用')
  }
}

const saveSourceFile = (data: BlobPart, mediaType: string, filename: string) => {
  const url = window.URL.createObjectURL(new Blob([data], { type: mediaType }))
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  window.URL.revokeObjectURL(url)
}

const storageAssociationLabel = (item: PrivateExamSourceStorageItem) => {
  if (item.associationType === 'PAPER') return `关联试卷：${item.associationTitle || '已确认私有试卷'}`
  if (item.associationType === 'DRAFT') return `关联草稿：${item.associationTitle || '待复核草稿'}`
  return '未关联业务内容'
}

const downloadStorageItem = async (item: PrivateExamSourceStorageItem) => {
  if (!item.associationId || item.associationType === 'UNREFERENCED') return
  storageDownloadingId.value = item.id
  try {
    const response =
      item.associationType === 'PAPER'
        ? await downloadPrivateExamSourceFile(item.associationId)
        : await downloadPrivateExamDraftSourceFile(item.associationId)
    saveSourceFile(
      response.data,
      String(response.headers['content-type'] || 'application/octet-stream'),
      item.sourceName,
    )
  } catch {
    ElMessage.error('原文件下载失败')
  } finally {
    storageDownloadingId.value = null
  }
}

const deleteStorageItem = async (item: PrivateExamSourceStorageItem) => {
  if (!item.associationId || item.associationType === 'UNREFERENCED') return
  const target = item.associationType === 'PAPER' ? '私有试卷及其原文件' : '草稿及其原文件'
  const confirmed = await ElMessageBox.confirm(
    `确认删除“${item.associationTitle || item.sourceName}”对应的${target}？受学习或考试记录引用时将无法删除。`,
    '删除关联内容',
    { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
  )
    .then(() => true)
    .catch(() => false)
  if (!confirmed) return
  storageDeletingId.value = item.id
  try {
    const res =
      item.associationType === 'PAPER'
        ? await deletePrivateExamPaper(item.associationId)
        : await deletePrivateExamDraft(item.associationId)
    if (res.code === 0) {
      ElMessage.success(`${target}已删除`)
      void importDialogRef.value?.reload()
      await Promise.all([loadStorageFiles(), loadPapers()])
    }
  } finally {
    storageDeletingId.value = null
  }
}

const downloadPaperSource = async () => {
  if (!privateSource.value) return
  sourceDownloading.value = true
  try {
    const response = await downloadPrivateExamSourceFile(privateSource.value.paperId)
    saveSourceFile(
      response.data,
      String(response.headers['content-type'] || 'application/octet-stream'),
      privateSource.value.sourceName,
    )
  } catch {
    ElMessage.error('原文件下载失败')
  } finally {
    sourceDownloading.value = false
  }
}

const loadPapers = async () => {
  loading.value = true
  try {
    const res = await getPublishedPapers({
      pageNum: pageNum.value,
      pageSize: 10,
      courseId: Number.isFinite(courseId) && courseId > 0 ? courseId : undefined,
    })
    if (res.code === 0 && res.data) {
      papers.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch {
    ElMessage.error('获取试卷列表失败')
  } finally {
    loading.value = false
  }
}

const handleTabChange = (tab: string | number) => {
  if (tab === 'records') void examRecordListRef.value?.reload()
}

const handleStartExam = async (paperId: number) => {
  startingId.value = paperId
  try {
    const startRes = await startExam(paperId)
    if (startRes.code === 0 && startRes.data) {
      router.push({ name: 'ExamTake', params: { recordId: String(startRes.data.id) } })
    } else {
      ElMessage.error(startRes.message || '开始考试失败')
    }
  } catch {
    ElMessage.error('开始考试失败')
  } finally {
    startingId.value = null
  }
}

const handleStartLearning = async (paperId: number) => {
  learningId.value = paperId
  try {
    const res = await startExamLearningSession(paperId)
    if (res.code === 0 && res.data) {
      router.push({ name: 'ExamLearning', params: { sessionId: String(res.data.id) } })
    } else {
      ElMessage.error(res.message || '开始试卷学习失败')
    }
  } catch {
    ElMessage.error('开始试卷学习失败，请确认课程已加入课程库')
  } finally {
    learningId.value = null
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

.source-meta {
  overflow-wrap: anywhere;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}

.storage-list {
  min-height: 120px;
}

.storage-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lp-space-4);
  padding: var(--lp-space-4) 0;
  border-bottom: var(--lp-border-hairline);
}

.storage-item:last-child {
  border-bottom: 0;
}

.storage-item-main {
  min-width: 0;
}

.storage-item-title,
.storage-item-actions {
  display: flex;
  align-items: center;
  gap: var(--lp-space-2);
}

.storage-item-title strong {
  overflow-wrap: anywhere;
}

.storage-item p {
  margin: var(--lp-space-1) 0 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}

.source-content {
  max-height: 56vh;
  overflow: auto;
  padding: var(--lp-space-4);
  white-space: pre-wrap;
  word-break: break-word;
  background: var(--lp-surface-soft);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
  margin-top: var(--lp-space-3);
}

@media (max-width: 860px) {
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

  .storage-item {
    align-items: stretch;
    flex-direction: column;
  }

  .storage-item-actions .el-button {
    min-height: 44px;
  }
}
</style>
