<template>
  <LtSheet v-model:open="storageDialogVisible" title="我的原文件存储" :width="760"
    ><div class="storage-list">
      <p v-if="storageFilesLoading" role="status" aria-busy="true">正在读取原文件清单</p>
      <div v-else-if="storageError" class="failure" role="alert">
        {{ storageError }} <LtButton @click="loadStorageFiles">重试读取</LtButton>
      </div>
      <p v-else-if="storageLoaded && !storageFiles.length" role="status">暂无已保存的 PDF 或 DOCX 原文件</p>
      <template v-else
        ><article v-for="item in storageFiles" :key="item.id" class="storage-item">
          <div class="storage-item-main">
            <div class="storage-item-title">
              <strong>{{ item.sourceName }}</strong
              ><span class="tag">{{ item.sourceFormat }}</span>
            </div>
            <p class="muted">{{ formatStorage(item.sourceSize) }} · {{ formatTime(item.createTime) }}</p>
            <p>{{ storageAssociationLabel(item) }}</p>
            <p v-if="storageDownloadErrorId === item.id" class="action-error" role="alert">
              {{ storageDownloadError }} <LtButton @click="downloadStorageItem(item)">重试下载</LtButton>
            </p>
            <p v-if="storageDeleteErrorId === item.id" class="action-error" role="alert">
              {{ storageDeleteError }} <LtButton @click="deleteStorageItem(item)">重试删除</LtButton>
            </p>
          </div>
          <div class="storage-item-actions">
            <LtButton
              v-if="item.associationType !== 'UNREFERENCED'"
              :disabled="storageDownloadingId !== null"
              @click="downloadStorageItem(item)"
              >下载</LtButton
            ><LtButton
              v-if="item.associationType !== 'UNREFERENCED'"
              class="danger"
              :disabled="storageDeletingId !== null"
              @click="deleteStorageItem(item)"
              >删除关联内容</LtButton
            >
          </div>
        </article></template
      >
    </div>
    <nav
      v-if="!storageFilesLoading && !storageError && storageFilesTotal > 10"
      class="pagination-wrapper"
      aria-label="原文件分页"
    >
      <span>共 {{ storageFilesTotal }} 个 · 第 {{ storageFilesPage }} 页</span
      ><LtButton :disabled="storageFilesPage <= 1" @click="changePage(-1)">上一页</LtButton
      ><LtButton :disabled="storageFilesPage * 10 >= storageFilesTotal" @click="changePage(1)">下一页</LtButton>
    </nav></LtSheet
  >
  <LtSheet v-model:open="sourceDialogVisible" title="私有试卷原始资料" :width="760"
    ><p v-if="sourceLoading" role="status" aria-busy="true">正在读取原始资料</p>
    <p v-else-if="sourceError" class="failure" role="alert">
      {{ sourceError }} <LtButton @click="retryPaperSource">重试读取</LtButton>
    </p>
    <template v-else-if="privateSource"
      ><p class="source-meta">{{ privateSource.sourceName }} · {{ privateSource.sourceFormat }}</p>
      <LtButton v-if="privateSource.originalFileAvailable" :disabled="sourceDownloading" @click="downloadPaperSource"
        >下载原文件</LtButton
      >
      <p v-if="sourceDownloadError" class="failure" role="alert">
        {{ sourceDownloadError }} <LtButton @click="downloadPaperSource">重试下载</LtButton>
      </p>
      <pre class="source-content">{{ privateSource.originalContent }}</pre>
    </template></LtSheet
  >
  <LtSheet v-model:open="deleteOpen" title="删除关联内容"
    ><p>{{ deletePrompt }}</p>
    <div class="sheet-actions">
      <LtButton @click="deleteSheet.answer(false)">取消</LtButton
      ><LtButton class="danger" @click="deleteSheet.answer(true)">确认删除</LtButton>
    </div></LtSheet
  >
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { LtButton, LtSheet } from '@/components/living'
import { useConfirmSheet } from '@/composables/useConfirmSheet'
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

const deleteSheet = useConfirmSheet()
const deleteOpen = deleteSheet.open
const deletePrompt = ref('')
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
watch(sourceDialogVisible, (open) => {
  if (!open) invalidateSource()
})
watch(storageDialogVisible, (open) => {
  if (!open) {
    deleteSheet.answer(false)
    invalidateStorage()
  }
})
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
  deletePrompt.value = `确认删除“${item.associationTitle || item.sourceName}”对应的${target}？受学习或考试记录引用时将无法删除。`
  const confirmed = await deleteSheet.ask()
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
  deleteSheet.answer(false)
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
  deleteSheet.answer(false)
  alive = false
  unsubscribe()
  invalidateSource()
  invalidateStorage()
})
defineExpose({ openPaperSource, openStorage })
function changePage(delta: number) {
  storageFilesPage.value += delta
  void loadStorageFiles()
}
</script>
