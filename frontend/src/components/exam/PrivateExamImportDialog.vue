<template>
  <LtSheet v-model:open="dialogVisible" title="导入私有试卷" :width="760"
    ><div class="private-import-dialog">
      <p v-if="validationError" class="import-error" role="alert">{{ validationError }}</p>
      <p v-if="actionError" class="import-error" role="alert">
        {{ actionError }} <LtButton v-if="retryKind" @click="retryAction">重试</LtButton>
      </p>
      <p v-if="actionNotice" role="status">{{ actionNotice }}</p>
      <p class="muted">
        支持结构化 Markdown、文本、文本型 PDF 或有限 DOCX；无答案题目会先保存为草稿，AI 建议必须逐题人工复核后才能启用。
      </p>
      <p v-if="readLoading.usage" role="status">正在读取原文件用量</p>
      <p v-else-if="loadErrors.usage" class="import-error" role="alert">
        {{ loadErrors.usage }} <LtButton @click="loadStorageUsage">重试用量</LtButton>
      </p>
      <div v-else-if="storageUsage" class="storage-summary">
        <span
          >原文件存储：{{ formatStorage(storageUsage.usedBytes) }} / {{ formatStorage(storageUsage.limitBytes) }} ·
          {{ storageUsage.fileCount }} 个文件</span
        ><LtButton @click="emit('open-storage')">查看明细</LtButton>
      </div>
      <fieldset v-if="!importPreview && !activeDraft" class="import-form" :disabled="previewLoading || confirmLoading">
        <legend class="sr-only">导入试卷资料</legend>
        <p v-if="readLoading.drafts" role="status">正在读取待复核草稿</p>
        <p v-else-if="loadErrors.drafts" role="alert" class="import-error">
          {{ loadErrors.drafts }} <LtButton @click="loadPrivateDrafts">重试草稿</LtButton>
        </p>
        <section v-else-if="privateDrafts.length" class="draft-list">
          <h3>待复核草稿</h3>
          <div v-for="draft in privateDrafts" :key="draft.id" class="draft-list-item">
            <LtButton class="draft-open-button" @click="openDraft(draft)"
              >{{ draft.title }} · {{ draft.reviewedQuestionCount }}/{{ draft.questionCount }} 已复核</LtButton
            ><LtButton class="danger" :disabled="deletingDraftId !== null" @click="deleteDraft(draft)"
              >删除草稿</LtButton
            >
          </div>
        </section>
        <p v-else-if="readLoaded.drafts" class="muted">暂无待复核草稿</p>
        <div class="import-grid">
          <label class="field">试卷标题<input v-model="importForm.title" maxlength="200" /></label
          ><label class="field"
            >所属课程<select v-model="importForm.courseId" :disabled="readLoading.courses || !!loadErrors.courses">
              <option :value="0">选择课程</option>
              <option v-for="course in courses" :key="course.id" :value="course.id">{{ course.name }}</option></select
            ><span v-if="readLoading.courses" role="status">正在读取课程</span
            ><span v-else-if="loadErrors.courses" class="import-error" role="alert"
              >{{ loadErrors.courses }} <LtButton @click="loadCourses">重试课程</LtButton></span
            ><span v-else-if="readLoaded.courses && !courses.length" class="muted">暂无可选课程</span></label
          >
          <label class="field"
            >原始资料名称<input
              v-model="importForm.sourceName"
              maxlength="255"
              :disabled="isFileImport"
              placeholder="例如：数据结构练习.md" /></label
          ><label class="field"
            >格式<select v-model="importForm.sourceFormat" @change="changeSourceFormat">
              <option value="MARKDOWN">Markdown</option>
              <option value="TEXT">结构化文本</option>
              <option value="PDF">文本型 PDF</option>
              <option value="DOCX">有限 DOCX</option>
            </select></label
          ><label class="field"
            >考试时长（分钟）<input v-model.number="importForm.duration" type="number" min="1" max="600"
          /></label>
        </div>
        <label v-if="isFileImport" class="field"
          >{{ importForm.sourceFormat }} 文件<input
            :key="importForm.sourceFormat"
            type="file"
            :accept="fileAccept"
            @change="selectNativeFile"
          /><span v-if="sourceFile">{{ sourceFile.name }} <LtButton @click="removeSourceFile">移除文件</LtButton></span
          ><span class="muted">{{
            importForm.sourceFormat === 'PDF'
              ? '最大 10MB、200 页；只提取已有文本，扫描件不做 OCR。'
              : '最大 10MB；只提取普通段落和表格，图片、公式和复杂排版不支持。'
          }}</span></label
        ><label v-else class="field"
          >原始内容<textarea v-model="importForm.content" :rows="14" maxlength="100000" :placeholder="PLACEHOLDER" />
        </label>
      </fieldset>
      <section v-else-if="importPreview" class="import-preview">
        <div class="preview-summary">
          <strong>{{ importPreview.title }}</strong
          ><span
            >{{ importPreview.questionCount }} 题 · {{ importPreview.totalScore }} 分 ·
            {{ importPreview.duration }} 分钟</span
          >
        </div>
        <article v-for="(question, index) in importPreview.questions" :key="index" class="preview-question">
          <div class="draft-question-title">
            <strong>{{ index + 1 }}. {{ question.content }}</strong
            ><span class="tag">{{ question.score }} 分</span>
          </div>
          <ul>
            <li v-for="option in question.options" :key="option.label" :class="{ correct: option.correct }">
              {{ option.label }}. {{ option.content }}
            </li>
          </ul>
          <p v-if="question.answerComplete">确认答案：{{ question.answer }}</p>
          <p v-else class="notice">未提供可靠答案，将进入 AI 建议与人工逐题复核草稿</p>
        </article>
      </section>
      <PrivateExamDraftReview
        v-else-if="activeDraft && dialogVisible"
        :key="activeDraft.id"
        :draft="activeDraft"
        @updated="replaceDraft"
      />
      <div class="sheet-actions">
        <LtButton v-if="importPreview" :disabled="confirmLoading" @click="openDraft(null)">返回修改</LtButton
        ><LtButton v-if="activeDraft" :disabled="confirmLoading" @click="openDraft(null)">返回导入</LtButton
        ><LtButton @click="dialogVisible = false">取消</LtButton
        ><LtButton
          v-if="!importPreview && !activeDraft"
          variant="primary"
          :disabled="previewLoading"
          @click="previewImport"
          >{{ previewLoading ? '正在解析…' : '解析并预览' }}</LtButton
        ><LtButton
          v-else-if="importPreview?.requiresAnswerReview"
          variant="primary"
          :disabled="confirmLoading"
          @click="createAnswerDraft"
          >创建 AI 补全草稿</LtButton
        ><LtButton v-else-if="importPreview" variant="primary" :disabled="confirmLoading" @click="confirmImport"
          >确认导入</LtButton
        ><LtButton
          v-else-if="activeDraft?.status === 'READY'"
          variant="primary"
          :disabled="confirmLoading"
          @click="confirmDraft"
          >确认启用试卷</LtButton
        >
      </div>
      <LtSheet v-model:open="deleteOpen" title="删除私有试卷草稿"
        ><p>{{ deletePrompt }}</p>
        <div class="sheet-actions">
          <LtButton @click="deleteSheet.answer(false)">取消</LtButton
          ><LtButton class="danger" @click="deleteSheet.answer(true)">确认删除</LtButton>
        </div></LtSheet
      >
    </div></LtSheet
  >
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { LtButton, LtSheet } from '@/components/living'
import { useConfirmSheet } from '@/composables/useConfirmSheet'
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
  (e: 'notice', value: string): void
}>()

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const deleteSheet = useConfirmSheet()
const deleteOpen = deleteSheet.open
const deletePrompt = ref('')
const actionNotice = ref('')
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

const {
  sourceFile,
  importForm,
  isFileImport,
  fileAccept,
  selectNativeFile,
  removeSourceFile,
  changeSourceFormat,
  valid: validImportForm,
  validationError,
  reset: resetForm,
} = usePrivateExamImportForm(computed(() => initialPrivateExamCourseId(props.defaultCourseId)))

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
  deleteSheet.answer(false)
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
      emit('notice', '私有试卷已导入')
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
  deletePrompt.value = `删除草稿“${draft.title}”及其未引用原始资料？此操作不可恢复。`
  const confirmed = await deleteSheet.ask()
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
      if (current(version, session)) actionNotice.value = '私有试卷草稿已删除'
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
      actionNotice.value = '草稿已保存，请逐题生成并复核答案'
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
      emit('notice', '私有试卷已人工确认并启用')
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
  actionNotice.value = ''
  importPreview.value = null
  activeDraft.value = null
  resetForm()
}

defineExpose({ reload: () => Promise.all([loadPrivateDrafts(), loadStorageUsage()]) })
</script>
