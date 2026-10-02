<template>
  <el-dialog
    v-model="storageDialogVisible"
    title="我的原文件存储"
    width="min(760px, 92vw)"
    append-to-body
    @close="invalidateStorage"
  >
    <div v-loading="storageFilesLoading" class="storage-list">
      <el-alert v-if="storageError" :title="storageError" type="error" :closable="false" show-icon>
        <template #default><el-button link type="primary" @click="loadStorageFiles">重试读取</el-button></template>
      </el-alert>
      <LpEmptyState
        v-else-if="!storageFilesLoading && storageLoaded && !storageFiles.length"
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
          <p v-if="storageDownloadErrorId === item.id" class="action-error" role="alert">
            {{ storageDownloadError }}
          </p>
          <p v-if="storageDeleteErrorId === item.id" class="action-error" role="alert">{{ storageDeleteError }}</p>
        </div>
        <div class="storage-item-actions">
          <el-button
            v-if="item.associationType !== 'UNREFERENCED'"
            plain
            :loading="storageDownloadingId === item.id"
            :disabled="storageDownloadingId !== null"
            @click="downloadStorageItem(item)"
          >
            下载
          </el-button>
          <el-button
            v-if="item.associationType !== 'UNREFERENCED'"
            type="danger"
            plain
            :loading="storageDeletingId === item.id"
            :disabled="storageDeletingId !== null"
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

  <el-dialog v-model="sourceDialogVisible" title="私有试卷原始资料" width="min(760px, 92vw)" @close="invalidateSource">
    <div v-if="sourceLoading" v-loading="true" class="source-loading" aria-label="正在读取原始资料" />
    <el-alert v-else-if="sourceError" :title="sourceError" type="error" :closable="false" show-icon>
      <template #default><el-button link type="primary" @click="retryPaperSource">重试读取</el-button></template>
    </el-alert>
    <template v-else-if="privateSource">
      <p class="source-meta">{{ privateSource.sourceName }} · {{ privateSource.sourceFormat }}</p>
      <el-button
        v-if="privateSource.originalFileAvailable"
        type="primary"
        plain
        :loading="sourceDownloading"
        :disabled="sourceDownloading"
        @click="downloadPaperSource"
      >
        下载原文件
      </el-button>
      <el-alert
        v-if="sourceDownloadError"
        :title="sourceDownloadError"
        type="error"
        :closable="false"
        class="source-action-error"
      >
        <template #default><el-button link type="primary" @click="downloadPaperSource">重试下载</el-button></template>
      </el-alert>
      <pre class="source-content">{{ privateSource.originalContent }}</pre>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import {
  deletePrivateExamDraft,
  deletePrivateExamPaper,
  downloadPrivateExamDraftSourceFile,
  downloadPrivateExamSourceFile,
  getPrivateExamSource,
  getPrivateExamStorageFiles,
} from '@/api/exam'
import type { PrivateExamSource, PrivateExamSourceStorageItem } from '@/api/exam'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'
import { errorMessage } from '@/utils/errors'
import { formatStorage, formatTime } from '@/utils/format'
import LpEmptyState from '@/components/ui/LpEmptyState.vue'

const emit = defineEmits<{ contentDeleted: [] }>()
const sourceDialogVisible = ref(false)
const privateSource = ref<PrivateExamSource | null>(null)
const sourceLoading = ref(false)
const sourceError = ref('')
const sourcePaperId = ref<number | null>(null)
const sourceDownloading = ref(false)
const sourceDownloadError = ref('')
const storageDialogVisible = ref(false)
const storageFilesLoading = ref(false)
const storageLoaded = ref(false)
const storageError = ref('')
const storageFiles = ref<PrivateExamSourceStorageItem[]>([])
const storageFilesTotal = ref(0)
const storageFilesPage = ref(1)
const storageDownloadingId = ref<number | null>(null)
const storageDownloadErrorId = ref<number | null>(null)
const storageDownloadError = ref('')
const storageDeletingId = ref<number | null>(null)
const storageDeleteErrorId = ref<number | null>(null)
const storageDeleteError = ref('')
let alive = true
let sourceVersion = 0
let storageVersion = 0
let sourceActionVersion = 0
let storageDownloadVersion = 0
let storageDeleteVersion = 0

function current(version: number, activeVersion: number, session: number) {
  return alive && version === activeVersion && session === getAuthSessionVersion()
}
function invalidateSource() {
  sourceVersion++
  sourceActionVersion++
  sourceLoading.value = false
  sourceDownloading.value = false
}
function invalidateStorage() {
  storageVersion++
  storageDownloadVersion++
  storageDeleteVersion++
  storageFilesLoading.value = false
  storageDownloadingId.value = null
  storageDeletingId.value = null
}
async function openStorage() {
  storageFilesPage.value = 1
  storageDialogVisible.value = true
  await loadStorageFiles()
}
async function loadStorageFiles() {
  const request = ++storageVersion
  const session = getAuthSessionVersion()
  storageFilesLoading.value = true
  storageError.value = ''
  try {
    const res = await getPrivateExamStorageFiles(
      { pageNum: storageFilesPage.value, pageSize: 10 },
      { errorDisplay: 'inline' },
    )
    if (!alive || request !== storageVersion || session !== getAuthSessionVersion()) return
    if (res.code === 0 && res.data) {
      storageFiles.value = res.data.records
      storageFilesTotal.value = res.data.total
      storageLoaded.value = true
    } else {
      storageFiles.value = []
      storageFilesTotal.value = 0
      storageLoaded.value = false
      storageError.value = res.message || '原文件清单暂时无法读取，请重试。'
    }
  } catch (cause) {
    if (alive && request === storageVersion && session === getAuthSessionVersion())
      storageError.value = errorMessage(cause, '原文件清单暂时无法读取，请重试。')
  } finally {
    if (alive && request === storageVersion && session === getAuthSessionVersion()) storageFilesLoading.value = false
  }
}
async function openPaperSource(paperId: number) {
  const request = ++sourceVersion
  sourceActionVersion++
  const session = getAuthSessionVersion()
  sourcePaperId.value = paperId
  privateSource.value = null
  sourceError.value = ''
  sourceDownloadError.value = ''
  sourceLoading.value = true
  sourceDownloading.value = false
  sourceDialogVisible.value = true
  try {
    const res = await getPrivateExamSource(paperId, { errorDisplay: 'inline' })
    if (!alive || request !== sourceVersion || session !== getAuthSessionVersion()) return
    if (res.code === 0 && res.data) privateSource.value = res.data
    else sourceError.value = res.message || '原始资料暂时不可用，请重试。'
  } catch (cause) {
    if (alive && request === sourceVersion && session === getAuthSessionVersion())
      sourceError.value = errorMessage(cause, '原始资料暂时不可用，请重试。')
  } finally {
    if (alive && request === sourceVersion && session === getAuthSessionVersion()) sourceLoading.value = false
  }
}
function retryPaperSource() {
  if (sourcePaperId.value) void openPaperSource(sourcePaperId.value)
}
function saveSourceFile(data: BlobPart, mediaType: string, filename: string) {
  const url = window.URL.createObjectURL(new Blob([data], { type: mediaType }))
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  window.URL.revokeObjectURL(url)
}
function storageAssociationLabel(item: PrivateExamSourceStorageItem) {
  if (item.associationType === 'PAPER') return `关联试卷：${item.associationTitle || '已确认私有试卷'}`
  if (item.associationType === 'DRAFT') return `关联草稿：${item.associationTitle || '待复核草稿'}`
  return '未关联业务内容'
}
async function downloadStorageItem(item: PrivateExamSourceStorageItem) {
  if (!item.associationId || item.associationType === 'UNREFERENCED' || storageDownloadingId.value !== null) return
  const request = ++storageDownloadVersion
  const session = getAuthSessionVersion()
  storageDownloadingId.value = item.id
  storageDownloadErrorId.value = null
  try {
    const response =
      item.associationType === 'PAPER'
        ? await downloadPrivateExamSourceFile(item.associationId, { errorDisplay: 'inline' })
        : await downloadPrivateExamDraftSourceFile(item.associationId, { errorDisplay: 'inline' })
    if (!current(request, storageDownloadVersion, session)) return
    saveSourceFile(
      response.data,
      String(response.headers['content-type'] || 'application/octet-stream'),
      item.sourceName,
    )
  } catch (cause) {
    if (current(request, storageDownloadVersion, session)) {
      storageDownloadErrorId.value = item.id
      storageDownloadError.value = errorMessage(cause, '原文件下载失败，请重试。')
    }
  } finally {
    if (current(request, storageDownloadVersion, session)) storageDownloadingId.value = null
  }
}
async function deleteStorageItem(item: PrivateExamSourceStorageItem) {
  if (!item.associationId || item.associationType === 'UNREFERENCED' || storageDeletingId.value !== null) return
  const target = item.associationType === 'PAPER' ? '私有试卷及其原文件' : '草稿及其原文件'
  const beforeConfirm = ++storageDeleteVersion
  const session = getAuthSessionVersion()
  storageDeletingId.value = item.id
  const confirmed = await ElMessageBox.confirm(
    `确认删除“${item.associationTitle || item.sourceName}”对应的${target}？受学习或考试记录引用时将无法删除。`,
    '删除关联内容',
    { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
  )
    .then(() => true)
    .catch(() => false)
  if (!confirmed || !current(beforeConfirm, storageDeleteVersion, session)) {
    if (current(beforeConfirm, storageDeleteVersion, session)) storageDeletingId.value = null
    return
  }
  storageDeleteErrorId.value = null
  try {
    const res =
      item.associationType === 'PAPER'
        ? await deletePrivateExamPaper(item.associationId, { errorDisplay: 'inline' })
        : await deletePrivateExamDraft(item.associationId, { errorDisplay: 'inline' })
    if (!current(beforeConfirm, storageDeleteVersion, session)) return
    if (res.code === 0) {
      await loadStorageFiles()
      if (current(beforeConfirm, storageDeleteVersion, session)) emit('contentDeleted')
    } else {
      storageDeleteErrorId.value = item.id
      storageDeleteError.value = res.message || '删除关联内容失败，请重试。'
    }
  } catch (cause) {
    if (current(beforeConfirm, storageDeleteVersion, session)) {
      storageDeleteErrorId.value = item.id
      storageDeleteError.value = errorMessage(cause, '删除关联内容失败，请重试。')
    }
  } finally {
    if (current(beforeConfirm, storageDeleteVersion, session)) storageDeletingId.value = null
  }
}
async function downloadPaperSource() {
  if (!privateSource.value || sourceDownloading.value) return
  const source = privateSource.value
  const request = ++sourceActionVersion
  const session = getAuthSessionVersion()
  sourceDownloading.value = true
  sourceDownloadError.value = ''
  try {
    const response = await downloadPrivateExamSourceFile(source.paperId, { errorDisplay: 'inline' })
    if (!current(request, sourceActionVersion, session) || privateSource.value?.paperId !== source.paperId) return
    saveSourceFile(
      response.data,
      String(response.headers['content-type'] || 'application/octet-stream'),
      source.sourceName,
    )
  } catch (cause) {
    if (current(request, sourceActionVersion, session) && privateSource.value?.paperId === source.paperId)
      sourceDownloadError.value = errorMessage(cause, '原文件下载失败，请重试。')
  } finally {
    if (current(request, sourceActionVersion, session)) sourceDownloading.value = false
  }
}
const unsubscribe = onAuthSessionChange(() => {
  sourceActionVersion++
  storageDownloadVersion++
  storageDeleteVersion++
  sourceVersion++
  storageVersion++
  sourceDialogVisible.value = false
  storageDialogVisible.value = false
  privateSource.value = null
  sourceError.value = ''
  sourceDownloadError.value = ''
  sourceLoading.value = false
  sourceDownloading.value = false
  storageFiles.value = []
  storageFilesTotal.value = 0
  storageLoaded.value = false
  storageError.value = ''
  storageFilesLoading.value = false
  storageDownloadingId.value = null
  storageDeletingId.value = null
  storageDownloadErrorId.value = null
  storageDownloadError.value = ''
  storageDeleteErrorId.value = null
  storageDeleteError.value = ''
})
onBeforeUnmount(() => {
  alive = false
  unsubscribe()
  invalidateSource()
  invalidateStorage()
})
defineExpose({ openPaperSource, openStorage })
</script>

<style scoped>
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
.source-loading {
  min-height: 160px;
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
.action-error {
  color: var(--lp-danger) !important;
}
.source-action-error {
  margin-top: var(--lp-space-3);
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
@media (max-width: 640px) {
  .storage-item {
    align-items: stretch;
    flex-direction: column;
  }
  .storage-item-actions .el-button {
    min-height: 44px;
  }
}
</style>
