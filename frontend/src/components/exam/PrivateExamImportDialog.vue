<template>
  <el-dialog v-model="dialogVisible" title="导入私有试卷" width="min(760px, 92vw)" class="private-import-dialog">
    <p v-if="validationError" class="import-error" role="alert">{{ validationError }}</p>
    <el-alert v-if="actionError" :title="actionError" type="error" :closable="false" show-icon class="import-error">
      <template v-if="retryKind" #default
        ><el-button link type="primary" @click="retryAction">重试</el-button></template
      >
    </el-alert>
    <el-alert
      title="支持结构化 Markdown、文本、文本型 PDF 或有限 DOCX；无答案题目会先保存为草稿，AI 建议必须逐题人工复核后才能启用。"
      type="info"
      :closable="false"
      show-icon
      class="import-intro"
    />

    <p v-if="readLoading.usage" role="status">正在读取原文件用量</p>
    <el-alert
      v-else-if="loadErrors.usage"
      :title="loadErrors.usage"
      type="error"
      :closable="false"
      class="import-error"
    >
      <template #default><el-button @click="loadStorageUsage">重试用量</el-button></template>
    </el-alert>
    <div v-else-if="storageUsage" class="storage-summary">
      <span>
        原文件存储：{{ formatStorage(storageUsage.usedBytes) }} / {{ formatStorage(storageUsage.limitBytes) }} ·
        {{ storageUsage.fileCount }} 个文件
      </span>
      <el-button type="primary" link @click="emit('open-storage')">查看明细</el-button>
    </div>

    <el-form
      v-if="!importPreview && !activeDraft"
      label-position="top"
      class="import-form"
      :disabled="previewLoading || confirmLoading"
    >
      <p v-if="readLoading.drafts" role="status">正在读取待复核草稿</p>
      <el-alert
        v-else-if="loadErrors.drafts"
        :title="loadErrors.drafts"
        type="error"
        :closable="false"
        class="import-error"
      >
        <template #default><el-button @click="loadPrivateDrafts">重试草稿</el-button></template>
      </el-alert>
      <section v-else-if="privateDrafts.length" class="draft-list">
        <strong class="draft-list-title">待复核草稿</strong>
        <div v-for="draft in privateDrafts" :key="draft.id" class="draft-list-item">
          <el-button plain class="draft-open-button" @click="openDraft(draft)">
            {{ draft.title }} · {{ draft.reviewedQuestionCount }}/{{ draft.questionCount }} 已复核
          </el-button>
          <el-button
            type="danger"
            link
            :loading="deletingDraftId === draft.id"
            :disabled="deletingDraftId !== null"
            @click="deleteDraft(draft)"
          >
            删除草稿
          </el-button>
        </div>
      </section>
      <p v-else-if="readLoaded.drafts" class="import-hint">暂无待复核草稿</p>

      <div class="import-grid">
        <el-form-item label="试卷标题">
          <el-input v-model="importForm.title" maxlength="200" />
        </el-form-item>
        <el-form-item label="所属课程">
          <el-select
            :model-value="importForm.courseId || undefined"
            @update:model-value="importForm.courseId = Number($event) || 0"
            :loading="readLoading.courses"
            :disabled="readLoading.courses || !!loadErrors.courses"
            filterable
            placeholder="选择课程"
          >
            <el-option v-for="course in courses" :key="course.id" :label="course.name" :value="course.id" />
          </el-select>
          <div v-if="loadErrors.courses" class="import-error" role="alert">
            {{ loadErrors.courses }} <el-button link @click="loadCourses">重试课程</el-button>
          </div>
          <p v-else-if="readLoaded.courses && !courses.length" class="import-hint">暂无可选课程</p>
        </el-form-item>
        <el-form-item label="原始资料名称">
          <el-input
            v-model="importForm.sourceName"
            maxlength="255"
            :disabled="isFileImport"
            placeholder="例如：数据结构练习.md"
          />
        </el-form-item>
        <el-form-item label="格式">
          <el-select v-model="importForm.sourceFormat" @change="changeSourceFormat">
            <el-option label="Markdown" value="MARKDOWN" />
            <el-option label="结构化文本" value="TEXT" />
            <el-option label="文本型 PDF" value="PDF" />
            <el-option label="有限 DOCX" value="DOCX" />
          </el-select>
        </el-form-item>
        <el-form-item label="考试时长（分钟）">
          <el-input-number v-model="importForm.duration" :min="1" :max="600" />
        </el-form-item>
      </div>

      <el-form-item v-if="isFileImport" :label="`${importForm.sourceFormat} 文件`">
        <el-upload
          :accept="fileAccept"
          :auto-upload="false"
          :limit="1"
          :on-change="selectSourceFile"
          :on-remove="removeSourceFile"
        >
          <el-button plain>{{ importForm.sourceFormat === 'PDF' ? '选择文本型 PDF' : '选择 DOCX' }}</el-button>
          <template #tip>
            <span v-if="importForm.sourceFormat === 'PDF'" class="upload-tip">
              最大 10MB、200 页；只提取已有文本，扫描件不做 OCR。
            </span>
            <span v-else class="upload-tip">最大 10MB；只提取普通段落和表格，图片、公式和复杂排版不支持。</span>
          </template>
        </el-upload>
      </el-form-item>
      <el-form-item v-else label="原始内容">
        <el-input
          v-model="importForm.content"
          type="textarea"
          :rows="14"
          maxlength="100000"
          show-word-limit
          :placeholder="PLACEHOLDER"
        />
      </el-form-item>
    </el-form>

    <section v-else-if="importPreview" class="import-preview">
      <div class="preview-summary">
        <strong>{{ importPreview.title }}</strong>
        <span
          >{{ importPreview.questionCount }} 题 · {{ importPreview.totalScore }} 分 ·
          {{ importPreview.duration }} 分钟</span
        >
      </div>
      <article v-for="(question, index) in importPreview.questions" :key="index" class="preview-question">
        <div class="preview-question-title">
          <strong>{{ index + 1 }}. {{ question.content }}</strong>
          <el-tag size="small">{{ question.score }} 分</el-tag>
        </div>
        <ul>
          <li v-for="option in question.options" :key="option.label" :class="{ correct: option.correct }">
            {{ option.label }}. {{ option.content }}
          </li>
        </ul>
        <p v-if="question.answerComplete">确认答案：{{ question.answer }}</p>
        <el-alert v-else title="未提供可靠答案，将进入 AI 建议与人工逐题复核草稿" type="warning" :closable="false" />
      </article>
    </section>

    <PrivateExamDraftReview
      v-else-if="activeDraft && dialogVisible"
      :key="activeDraft.id"
      :draft="activeDraft"
      @updated="replaceDraft"
    />

    <template #footer>
      <el-button v-if="importPreview" :disabled="confirmLoading" @click="openDraft(null)">返回修改</el-button>
      <el-button v-if="activeDraft" :disabled="confirmLoading" @click="openDraft(null)">返回导入</el-button>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button v-if="!importPreview && !activeDraft" type="primary" :loading="previewLoading" @click="previewImport">
        解析并预览
      </el-button>
      <el-button
        v-else-if="importPreview?.requiresAnswerReview"
        type="warning"
        :loading="confirmLoading"
        @click="createAnswerDraft"
      >
        创建 AI 补全草稿
      </el-button>
      <el-button v-else-if="importPreview" type="primary" :loading="confirmLoading" @click="confirmImport">
        确认导入
      </el-button>
      <el-button
        v-else-if="activeDraft?.status === 'READY'"
        type="primary"
        :loading="confirmLoading"
        @click="confirmDraft"
      >
        确认启用试卷
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { confirmPrivateExamDraft, deletePrivateExamDraft } from '@/api/exam'
import type { PrivateExamDraft, PrivateExamImportPreview, PrivateExamStorageUsage } from '@/api/exam'
import { formatStorage } from '@/utils/format'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'
import { usePrivateExamImportLoaders } from './usePrivateExamImportLoaders'
import { errorMessage } from '@/utils/errors'
import PrivateExamDraftReview from '@/components/exam/PrivateExamDraftReview.vue'
import {
  confirmPrivateExamSource,
  createPrivateExamAnswerDraft,
  previewPrivateExamSource,
} from './privateExamImportRequests'
import { initialPrivateExamCourseId, PLACEHOLDER, usePrivateExamImportForm } from './usePrivateExamImportForm'

const props = defineProps<{
  modelValue: boolean
  defaultCourseId?: number
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'imported'): void
  (e: 'open-storage'): void
}>()

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const courses = ref([] as import('@/api/course').CourseVO[])
const previewLoading = ref(false)
const confirmLoading = ref(false)
const importPreview = ref<PrivateExamImportPreview | null>(null)
const privateDrafts = ref<PrivateExamDraft[]>([])
const activeDraft = ref<PrivateExamDraft | null>(null)
const deletingDraftId = ref<number | null>(null)
const storageUsage = ref<PrivateExamStorageUsage | null>(null)
const actionError = ref('')
const retryKind = ref<'preview' | 'confirm' | 'draft' | 'enable' | null>(null)

const initialCourseId = computed(() => initialPrivateExamCourseId(props.defaultCourseId))

const {
  sourceFile,
  importForm,
  isFileImport,
  fileAccept,
  selectSourceFile,
  removeSourceFile,
  changeSourceFormat,
  valid: validImportForm,
  validationError,
  reset: resetForm,
} = usePrivateExamImportForm(initialCourseId)

let dialogVersion = 0
let alive = true

function current(version: number, session: number) {
  return alive && version === dialogVersion && session === getAuthSessionVersion()
}
const {
  loadCourses,
  loadDrafts: loadPrivateDrafts,
  loadUsage: loadStorageUsage,
  invalidate: invalidateLoads,
  invalidateDrafts,
  errors: loadErrors,
  loading: readLoading,
  loaded: readLoaded,
} = usePrivateExamImportLoaders(courses, privateDrafts, storageUsage, () => alive && dialogVisible.value)

function invalidateDialogRequests() {
  dialogVersion++
  previewLoading.value = false
  confirmLoading.value = false
  deletingDraftId.value = null
  actionError.value = ''
  retryKind.value = null
}

const unsubscribe = onAuthSessionChange(() => {
  invalidateDialogRequests()
  invalidateLoads()
  courses.value = []
  privateDrafts.value = []
  storageUsage.value = null
  actionError.value = ''
  resetImport()
  if (dialogVisible.value) {
    void loadCourses()
    void loadPrivateDrafts()
    void loadStorageUsage()
  }
})
onBeforeUnmount(() => {
  alive = false
  invalidateDialogRequests()
  invalidateLoads()
  unsubscribe()
})

watch(
  () => props.modelValue,
  (open) => {
    invalidateDialogRequests()
    invalidateLoads()
    if (!open) {
      resetImport()
      return
    }
    void loadCourses()
    void loadPrivateDrafts()
    void loadStorageUsage()
  },
  { flush: 'sync' },
)

const previewImport = async () => {
  if (previewLoading.value || !validImportForm()) return
  const version = dialogVersion
  const session = getAuthSessionVersion()
  actionError.value = ''
  retryKind.value = null
  previewLoading.value = true
  try {
    const res = await previewPrivateExamSource(importForm.value, sourceFile.value)
    if (!current(version, session)) return
    if (res.code === 0 && res.data) importPreview.value = res.data
    else {
      actionError.value = res.message || '解析失败'
      retryKind.value = 'preview'
    }
  } catch (cause) {
    if (current(version, session)) {
      actionError.value = errorMessage(cause, '暂时无法解析，请重试。已选内容会保留。')
      retryKind.value = 'preview'
    }
  } finally {
    if (current(version, session)) previewLoading.value = false
  }
}

const confirmImport = async () => {
  if (confirmLoading.value || !importPreview.value) return
  const version = dialogVersion
  const session = getAuthSessionVersion()
  actionError.value = ''
  retryKind.value = null
  confirmLoading.value = true
  try {
    const res = await confirmPrivateExamSource(importForm.value, importPreview.value, sourceFile.value)
    if (!current(version, session)) return
    if (res.code === 0 && res.data) {
      ElMessage.success('私有试卷已导入')
      emit('update:modelValue', false)
      emit('imported')
    } else {
      actionError.value = res.message || '导入失败'
      retryKind.value = 'confirm'
    }
  } catch (cause) {
    if (current(version, session)) {
      actionError.value = errorMessage(cause, '导入未完成，请重试。')
      retryKind.value = 'confirm'
    }
  } finally {
    if (current(version, session)) confirmLoading.value = false
  }
}

const openDraft = (draft: PrivateExamDraft | null) => {
  invalidateDialogRequests()
  activeDraft.value = draft
  importPreview.value = null
}

const updateDraftList = (draft: PrivateExamDraft) => {
  invalidateDrafts()
  const index = privateDrafts.value.findIndex((item) => item.id === draft.id)
  if (index >= 0) privateDrafts.value[index] = draft
  else privateDrafts.value.unshift(draft)
}

const replaceDraft = (draft: PrivateExamDraft) => {
  if (!dialogVisible.value || activeDraft.value?.id !== draft.id) return
  activeDraft.value = draft
  updateDraftList(draft)
}

const deleteDraft = async (draft: PrivateExamDraft) => {
  if (deletingDraftId.value !== null) return
  const version = dialogVersion
  const session = getAuthSessionVersion()
  deletingDraftId.value = draft.id
  const confirmed = await ElMessageBox.confirm(
    `删除草稿“${draft.title}”及其未引用原始资料？此操作不可恢复。`,
    '删除私有试卷草稿',
    { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
  )
    .then(() => true)
    .catch(() => false)
  if (!current(version, session)) return
  if (!confirmed) {
    deletingDraftId.value = null
    return
  }
  actionError.value = ''
  try {
    const res = await deletePrivateExamDraft(draft.id, { errorDisplay: 'inline' })
    if (!current(version, session)) return
    if (res.code === 0) {
      invalidateDrafts()
      privateDrafts.value = privateDrafts.value.filter((item) => item.id !== draft.id)
      if (activeDraft.value?.id === draft.id) activeDraft.value = null
      await loadStorageUsage()
      if (current(version, session)) ElMessage.success('私有试卷草稿已删除')
    } else actionError.value = res.message || '删除草稿失败，请重试。'
  } catch (cause) {
    if (current(version, session)) actionError.value = errorMessage(cause, '删除草稿失败，请重试。')
  } finally {
    if (current(version, session)) deletingDraftId.value = null
  }
}

const createAnswerDraft = async () => {
  if (confirmLoading.value || !importPreview.value) return
  const version = dialogVersion
  const session = getAuthSessionVersion()
  actionError.value = ''
  retryKind.value = null
  confirmLoading.value = true
  try {
    const res = await createPrivateExamAnswerDraft(importForm.value, importPreview.value, sourceFile.value)
    if (!current(version, session)) return
    if (res.code === 0 && res.data) {
      updateDraftList(res.data)
      openDraft(res.data)
      ElMessage.success('草稿已保存，请逐题生成并复核答案')
    } else {
      actionError.value = res.message || '创建草稿失败'
      retryKind.value = 'draft'
    }
  } catch (cause) {
    if (current(version, session)) {
      actionError.value = errorMessage(cause, '创建草稿失败')
      retryKind.value = 'draft'
    }
  } finally {
    if (current(version, session)) confirmLoading.value = false
  }
}

const confirmDraft = async () => {
  if (confirmLoading.value || !activeDraft.value) return
  const version = dialogVersion
  const session = getAuthSessionVersion()
  actionError.value = ''
  retryKind.value = null
  confirmLoading.value = true
  try {
    const res = await confirmPrivateExamDraft(activeDraft.value.id, { errorDisplay: 'inline' })
    if (!current(version, session)) return
    if (res.code === 0 && res.data) {
      ElMessage.success('私有试卷已人工确认并启用')
      emit('update:modelValue', false)
      emit('imported')
    } else {
      actionError.value = res.message || '启用失败'
      retryKind.value = 'enable'
    }
  } catch (cause) {
    if (current(version, session)) {
      actionError.value = errorMessage(cause, '启用失败')
      retryKind.value = 'enable'
    }
  } finally {
    if (current(version, session)) confirmLoading.value = false
  }
}

const retryAction = () => {
  const retry = retryKind.value
  const actions = { preview: previewImport, confirm: confirmImport, draft: createAnswerDraft, enable: confirmDraft }
  if (retry) void actions[retry]()
}

const resetImport = () => {
  importPreview.value = null
  activeDraft.value = null
  resetForm()
}

defineExpose({ reload: () => Promise.all([loadPrivateDrafts(), loadStorageUsage()]) })
</script>

<style scoped>
.import-error {
  margin: var(--lp-space-3) 0;
  overflow-wrap: anywhere;
}
p.import-error,
div.import-error[role='alert'] {
  color: var(--lp-danger);
}
.import-hint {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-sm);
}
.private-import-dialog :deep(.el-dialog__body) {
  padding-top: var(--lp-space-3);
}

.import-intro {
  border-radius: var(--lp-radius-md);
}

.storage-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lp-space-3);
  margin: var(--lp-space-3) 0;
  padding: var(--lp-space-3) var(--lp-space-4);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  background: var(--lp-surface-soft);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
}

.import-form {
  margin-top: var(--lp-space-4);
}

.draft-list {
  display: flex;
  flex-direction: column;
  gap: var(--lp-space-2);
  margin-bottom: var(--lp-space-4);
  padding: var(--lp-space-3);
  background: var(--lp-warning-soft);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
}

.draft-list-title {
  color: var(--lp-warning);
  font-size: var(--lp-text-xs);
  font-weight: var(--lp-weight-bold);
  letter-spacing: var(--lp-tracking-wide);
  text-transform: uppercase;
}

.draft-list-item {
  display: flex;
  align-items: center;
  gap: var(--lp-space-2);
}

.draft-open-button {
  flex: 1;
  min-width: 0;
}

.import-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 var(--lp-space-4);
}

.import-grid .el-select {
  width: 100%;
}

.upload-tip {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
}

.preview-summary {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--lp-space-3);
  margin: var(--lp-space-4) 0 var(--lp-space-3);
}

.preview-summary strong {
  min-width: 0;
  color: var(--lp-text);
  overflow-wrap: anywhere;
}

.preview-summary span {
  flex-shrink: 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}

.preview-question {
  padding: var(--lp-space-4);
  margin-bottom: var(--lp-space-3);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
}

.preview-question-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--lp-space-3);
}

.preview-question ul {
  margin: var(--lp-space-3) 0;
  padding-left: var(--lp-space-6);
  color: var(--lp-text-secondary);
}

.preview-question li.correct {
  color: var(--lp-success);
  font-weight: var(--lp-weight-bold);
}

.preview-question p {
  margin: 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}

@media (max-width: 640px) {
  .import-grid {
    grid-template-columns: 1fr;
  }

  .storage-summary {
    align-items: stretch;
    flex-direction: column;
  }

  .preview-summary {
    flex-direction: column;
  }
}
</style>
