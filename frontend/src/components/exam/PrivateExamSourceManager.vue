<template>
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
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deletePrivateExamDraft,
  deletePrivateExamPaper,
  downloadPrivateExamDraftSourceFile,
  downloadPrivateExamSourceFile,
  getPrivateExamSource,
  getPrivateExamStorageFiles,
} from '@/api/exam'
import type { PrivateExamSource, PrivateExamSourceStorageItem } from '@/api/exam'
import { formatStorage, formatTime } from '@/utils/format'
import LpEmptyState from '@/components/ui/LpEmptyState.vue'

const emit = defineEmits<{
  contentDeleted: []
}>()

const sourceDialogVisible = ref(false)
const privateSource = ref<PrivateExamSource | null>(null)
const sourceDownloading = ref(false)
const storageDialogVisible = ref(false)
const storageFilesLoading = ref(false)
const storageFiles = ref<PrivateExamSourceStorageItem[]>([])
const storageFilesTotal = ref(0)
const storageFilesPage = ref(1)
const storageDownloadingId = ref<number | null>(null)
const storageDeletingId = ref<number | null>(null)

async function openStorage() {
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

async function openPaperSource(paperId: number) {
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

async function deleteStorageItem(item: PrivateExamSourceStorageItem) {
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
      await loadStorageFiles()
      emit('contentDeleted')
    }
  } finally {
    storageDeletingId.value = null
  }
}

async function downloadPaperSource() {
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

defineExpose({
  openPaperSource,
  openStorage,
})
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
