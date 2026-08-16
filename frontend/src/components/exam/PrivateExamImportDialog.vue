<template>
  <el-dialog
    v-model="dialogVisible"
    title="导入私有试卷"
    width="min(760px, 92vw)"
    class="private-import-dialog"
    @closed="resetImport"
  >
    <el-alert
      title="支持结构化 Markdown、文本、文本型 PDF 或有限 DOCX；无答案题目会先保存为草稿，AI 建议必须逐题人工复核后才能启用。"
      type="info"
      :closable="false"
      show-icon
      class="import-intro"
    />

    <div v-if="storageUsage" class="storage-summary">
      <span>
        原文件存储：{{ formatStorage(storageUsage.usedBytes) }} / {{ formatStorage(storageUsage.limitBytes) }} ·
        {{ storageUsage.fileCount }} 个文件
      </span>
      <el-button type="primary" link @click="emit('open-storage')">查看明细</el-button>
    </div>

    <el-form v-if="!importPreview && !activeDraft" label-position="top" class="import-form">
      <section v-if="privateDrafts.length" class="draft-list">
        <strong class="draft-list-title">待复核草稿</strong>
        <div v-for="draft in privateDrafts" :key="draft.id" class="draft-list-item">
          <el-button plain class="draft-open-button" @click="openDraft(draft)">
            {{ draft.title }} · {{ draft.reviewedQuestionCount }}/{{ draft.questionCount }} 已复核
          </el-button>
          <el-button type="danger" link :loading="deletingDraftId === draft.id" @click="deleteDraft(draft)">
            删除草稿
          </el-button>
        </div>
      </section>

      <div class="import-grid">
        <el-form-item label="试卷标题">
          <el-input v-model="importForm.title" maxlength="200" />
        </el-form-item>
        <el-form-item label="所属课程">
          <el-select v-model="importForm.courseId" filterable placeholder="选择课程">
            <el-option v-for="course in courses" :key="course.id" :label="course.name" :value="course.id" />
          </el-select>
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
          :placeholder="importPlaceholder"
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

    <section v-else-if="activeDraft" class="draft-review">
      <div class="preview-summary">
        <strong>{{ activeDraft.title }}</strong>
        <span>{{ activeDraft.reviewedQuestionCount }}/{{ activeDraft.questionCount }} 题已人工复核</span>
      </div>
      <div v-if="activeDraft.originalFileAvailable" class="draft-review-tools">
        <el-button plain :loading="sourceDownloading" @click="downloadDraftSource">下载草稿原文件</el-button>
      </div>
      <el-alert
        title="AI 只提供建议，不会直接成为判分答案；每题必须由你选择答案并确认解析。"
        type="warning"
        :closable="false"
      />
      <article v-for="question in activeDraft.questions" :key="question.id" class="draft-question">
        <div class="draft-question-title">
          <strong>{{ question.sortOrder }}. {{ question.content }}</strong>
          <el-tag :type="question.reviewStatus === 'REVIEWED' ? 'success' : 'warning'">
            {{ question.reviewStatus === 'REVIEWED' ? '已复核' : '待复核' }}
          </el-tag>
        </div>
        <ul>
          <li v-for="option in question.options" :key="option.label">{{ option.label }}. {{ option.content }}</li>
        </ul>
        <el-button
          v-if="question.generationStatus === 'PENDING'"
          type="primary"
          plain
          :loading="generatingQuestionId === question.id"
          @click="generateDraftAnswer(question.id)"
        >
          生成 AI 答案与解析
        </el-button>
        <template v-else>
          <p v-if="question.generationStatus === 'GENERATED'" class="ai-suggestion">
            AI 建议：{{ question.aiAnswerLabels.join('、') }} · {{ question.aiAnalysis }}
          </p>
          <p v-else class="ai-suggestion">原资料答案：{{ question.originalAnswerLabels.join('、') || '未提供' }}</p>
          <el-form-item label="人工确认答案">
            <el-checkbox-group v-model="draftAnswers[question.id]" :disabled="question.reviewStatus === 'REVIEWED'">
              <el-checkbox v-for="option in question.options" :key="option.label" :value="option.label">
                {{ option.label }}
              </el-checkbox>
            </el-checkbox-group>
          </el-form-item>
          <el-form-item label="人工确认解析">
            <el-input
              v-model="draftAnalyses[question.id]"
              type="textarea"
              :rows="3"
              maxlength="10000"
              :disabled="question.reviewStatus === 'REVIEWED'"
            />
          </el-form-item>
          <el-button
            v-if="question.reviewStatus !== 'REVIEWED'"
            type="success"
            :loading="reviewingQuestionId === question.id"
            @click="reviewDraftQuestion(question.id)"
          >
            确认本题
          </el-button>
        </template>
      </article>
    </section>

    <template #footer>
      <el-button v-if="importPreview" @click="importPreview = null">返回修改</el-button>
      <el-button v-if="activeDraft" @click="activeDraft = null">返回导入</el-button>
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
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadFile } from 'element-plus'
import {
  confirmPrivateExamDraft,
  confirmPrivateExamImport,
  confirmPrivateExamPdf,
  confirmPrivateExamDocx,
  createPrivateExamDraft,
  createPrivateExamPdfDraft,
  createPrivateExamDocxDraft,
  deletePrivateExamDraft,
  downloadPrivateExamDraftSourceFile,
  generatePrivateExamDraftAnswer,
  getPrivateExamDrafts,
  getPrivateExamStorageUsage,
  previewPrivateExamImport,
  previewPrivateExamPdf,
  previewPrivateExamDocx,
  reviewPrivateExamDraftQuestion,
} from '@/api/exam'
import type {
  PrivateExamDraft,
  PrivateExamImportPreview,
  PrivateExamImportRequest,
  PrivateExamStorageUsage,
} from '@/api/exam'
import { getAllCourses } from '@/api/course'
import type { CourseVO } from '@/api/course'
import { formatStorage } from '@/utils/format'

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

const courses = ref<CourseVO[]>([])
const previewLoading = ref(false)
const confirmLoading = ref(false)
const importPreview = ref<PrivateExamImportPreview | null>(null)
const privateDrafts = ref<PrivateExamDraft[]>([])
const activeDraft = ref<PrivateExamDraft | null>(null)
const generatingQuestionId = ref<number | null>(null)
const reviewingQuestionId = ref<number | null>(null)
const deletingDraftId = ref<number | null>(null)
const draftAnswers = ref<Record<number, string[]>>({})
const draftAnalyses = ref<Record<number, string>>({})
const storageUsage = ref<PrivateExamStorageUsage | null>(null)
const sourceDownloading = ref(false)
const sourceFile = ref<File | null>(null)

const initialCourseId = computed(() => {
  const id = props.defaultCourseId ?? 0
  return Number.isFinite(id) && id > 0 ? id : 0
})

const emptyImportForm = (): PrivateExamImportRequest => ({
  title: '',
  courseId: initialCourseId.value,
  duration: 60,
  sourceName: '',
  sourceFormat: 'MARKDOWN',
  content: '',
})

const importForm = ref<PrivateExamImportRequest>(emptyImportForm())
const importPlaceholder = `## 1. 单选题\n**题干**: 栈遵循哪种访问顺序？\n**选项**:\n- A. 先进先出\n- B. 先进后出\n**答案**: B\n**解析**: 栈遵循 LIFO。\n**分值**: 2`

const isFileImport = computed(() => ['PDF', 'DOCX'].includes(importForm.value.sourceFormat))
const fileAccept = computed(() =>
  importForm.value.sourceFormat === 'PDF'
    ? 'application/pdf,.pdf'
    : 'application/vnd.openxmlformats-officedocument.wordprocessingml.document,.docx',
)

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    void loadCourses()
    void loadPrivateDrafts()
    void loadStorageUsage()
  },
)

async function loadStorageUsage() {
  try {
    const res = await getPrivateExamStorageUsage()
    storageUsage.value = res.code === 0 && res.data ? res.data : null
  } catch {
    storageUsage.value = null
  }
}

async function loadCourses() {
  if (courses.value.length) return
  try {
    const res = await getAllCourses()
    if (res.code === 0 && res.data) courses.value = res.data
  } catch {
    ElMessage.error('获取课程列表失败')
  }
}

async function loadPrivateDrafts() {
  try {
    const res = await getPrivateExamDrafts()
    if (res.code === 0 && res.data) privateDrafts.value = res.data
  } catch {
    ElMessage.error('获取待复核草稿失败')
  }
}

const selectSourceFile = (uploadFile: UploadFile) => {
  sourceFile.value = uploadFile.raw || null
  importForm.value.sourceName = uploadFile.name
  importForm.value.content = ''
}

const removeSourceFile = () => {
  sourceFile.value = null
  importForm.value.sourceName = ''
}

const changeSourceFormat = () => {
  sourceFile.value = null
  importForm.value.sourceName = ''
  importForm.value.content = ''
}

const fileMetadata = () => ({
  title: importForm.value.title,
  courseId: importForm.value.courseId,
  duration: importForm.value.duration,
})

const validateImportForm = () => {
  if (isFileImport.value) {
    if (!importForm.value.title.trim() || !importForm.value.courseId || !sourceFile.value) {
      ElMessage.warning(`请完整填写标题、课程并选择 ${importForm.value.sourceFormat} 文件`)
      return false
    }
    return true
  }
  if (
    !importForm.value.title.trim() ||
    !importForm.value.sourceName.trim() ||
    !importForm.value.courseId ||
    !importForm.value.content.trim()
  ) {
    ElMessage.warning('请完整填写标题、课程、资料名称和原始内容')
    return false
  }
  return true
}

const previewImport = async () => {
  if (!validateImportForm()) return
  previewLoading.value = true
  try {
    let res
    if (importForm.value.sourceFormat === 'PDF' && sourceFile.value) {
      res = await previewPrivateExamPdf(fileMetadata(), sourceFile.value)
    } else if (importForm.value.sourceFormat === 'DOCX' && sourceFile.value) {
      res = await previewPrivateExamDocx(fileMetadata(), sourceFile.value)
    } else {
      res = await previewPrivateExamImport(importForm.value)
    }
    if (res.code === 0 && res.data) importPreview.value = res.data
    else ElMessage.error(res.message || '解析失败')
  } catch {
    ElMessage.error('解析失败，请检查结构化格式')
  } finally {
    previewLoading.value = false
  }
}

const confirmImport = async () => {
  if (!importPreview.value) return
  confirmLoading.value = true
  try {
    let res
    const metadata = {
      ...fileMetadata(),
      expectedContentHash: importPreview.value.contentHash,
      confirmed: true as const,
    }
    if (importForm.value.sourceFormat === 'PDF' && sourceFile.value) {
      res = await confirmPrivateExamPdf(metadata, sourceFile.value)
    } else if (importForm.value.sourceFormat === 'DOCX' && sourceFile.value) {
      res = await confirmPrivateExamDocx(metadata, sourceFile.value)
    } else {
      res = await confirmPrivateExamImport({
        ...importForm.value,
        expectedContentHash: importPreview.value.contentHash,
        confirmed: true,
      })
    }
    if (res.code === 0 && res.data) {
      ElMessage.success('私有试卷已导入')
      emit('update:modelValue', false)
      emit('imported')
    } else ElMessage.error(res.message || '导入失败')
  } catch {
    ElMessage.error('导入失败')
  } finally {
    confirmLoading.value = false
  }
}

const syncDraftForm = (draft: PrivateExamDraft) => {
  draft.questions.forEach((question) => {
    draftAnswers.value[question.id] = [
      ...(question.finalAnswerLabels.length
        ? question.finalAnswerLabels
        : question.aiAnswerLabels.length
          ? question.aiAnswerLabels
          : question.originalAnswerLabels),
    ]
    draftAnalyses.value[question.id] = question.finalAnalysis || question.aiAnalysis || question.originalAnalysis || ''
  })
}

const openDraft = (draft: PrivateExamDraft) => {
  activeDraft.value = draft
  importPreview.value = null
  syncDraftForm(draft)
}

const replaceDraft = (draft: PrivateExamDraft) => {
  activeDraft.value = draft
  const index = privateDrafts.value.findIndex((item) => item.id === draft.id)
  if (index >= 0) privateDrafts.value[index] = draft
  else privateDrafts.value.unshift(draft)
  syncDraftForm(draft)
}

const deleteDraft = async (draft: PrivateExamDraft) => {
  const confirmed = await ElMessageBox.confirm(
    `删除草稿“${draft.title}”及其未引用原始资料？此操作不可恢复。`,
    '删除私有试卷草稿',
    { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
  )
    .then(() => true)
    .catch(() => false)
  if (!confirmed) return
  deletingDraftId.value = draft.id
  try {
    const res = await deletePrivateExamDraft(draft.id)
    if (res.code === 0) {
      privateDrafts.value = privateDrafts.value.filter((item) => item.id !== draft.id)
      if (activeDraft.value?.id === draft.id) activeDraft.value = null
      await loadStorageUsage()
      ElMessage.success('私有试卷草稿已删除')
    }
  } finally {
    deletingDraftId.value = null
  }
}

const createAnswerDraft = async () => {
  if (!importPreview.value) return
  confirmLoading.value = true
  try {
    let res
    const metadata = { ...fileMetadata(), expectedContentHash: importPreview.value.contentHash }
    if (importForm.value.sourceFormat === 'PDF' && sourceFile.value) {
      res = await createPrivateExamPdfDraft(metadata, sourceFile.value)
    } else if (importForm.value.sourceFormat === 'DOCX' && sourceFile.value) {
      res = await createPrivateExamDocxDraft(metadata, sourceFile.value)
    } else {
      res = await createPrivateExamDraft({
        ...importForm.value,
        expectedContentHash: importPreview.value.contentHash,
      })
    }
    if (res.code === 0 && res.data) {
      replaceDraft(res.data)
      importPreview.value = null
      ElMessage.success('草稿已保存，请逐题生成并复核答案')
    } else ElMessage.error(res.message || '创建草稿失败')
  } catch {
    ElMessage.error('创建草稿失败')
  } finally {
    confirmLoading.value = false
  }
}

const generateDraftAnswer = async (questionId: number) => {
  if (!activeDraft.value) return
  generatingQuestionId.value = questionId
  try {
    const res = await generatePrivateExamDraftAnswer(activeDraft.value.id, questionId)
    if (res.code === 0 && res.data) {
      replaceDraft(res.data)
      ElMessage.success('AI 建议已生成，请人工核对')
    } else ElMessage.error(res.message || 'AI 生成失败')
  } catch {
    ElMessage.error('AI 生成失败，请稍后重试')
  } finally {
    generatingQuestionId.value = null
  }
}

const reviewDraftQuestion = async (questionId: number) => {
  if (!activeDraft.value) return
  const question = activeDraft.value.questions.find((item) => item.id === questionId)
  const answers = draftAnswers.value[questionId] || []
  const analysis = draftAnalyses.value[questionId]?.trim() || ''
  if (!question || !answers.length || !analysis) {
    ElMessage.warning('请选择答案并填写人工确认解析')
    return
  }
  if (question.questionType !== 'MULTIPLE_CHOICE' && answers.length !== 1) {
    ElMessage.warning('单选或判断题只能确认一个答案')
    return
  }
  if (question.questionType === 'MULTIPLE_CHOICE' && answers.length < 2) {
    ElMessage.warning('多选题至少确认两个答案')
    return
  }
  reviewingQuestionId.value = questionId
  try {
    const res = await reviewPrivateExamDraftQuestion(activeDraft.value.id, questionId, {
      answerLabels: answers,
      analysis,
    })
    if (res.code === 0 && res.data) {
      replaceDraft(res.data)
      ElMessage.success('本题已人工复核')
    } else ElMessage.error(res.message || '复核失败')
  } catch {
    ElMessage.error('复核失败')
  } finally {
    reviewingQuestionId.value = null
  }
}

const confirmDraft = async () => {
  if (!activeDraft.value) return
  confirmLoading.value = true
  try {
    const res = await confirmPrivateExamDraft(activeDraft.value.id)
    if (res.code === 0 && res.data) {
      ElMessage.success('私有试卷已人工确认并启用')
      emit('update:modelValue', false)
      emit('imported')
    } else ElMessage.error(res.message || '启用失败')
  } catch {
    ElMessage.error('启用失败')
  } finally {
    confirmLoading.value = false
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

const downloadDraftSource = async () => {
  if (!activeDraft.value?.sourceName) return
  sourceDownloading.value = true
  try {
    const response = await downloadPrivateExamDraftSourceFile(activeDraft.value.id)
    saveSourceFile(
      response.data,
      String(response.headers['content-type'] || 'application/octet-stream'),
      activeDraft.value.sourceName,
    )
  } catch {
    ElMessage.error('原文件下载失败')
  } finally {
    sourceDownloading.value = false
  }
}

const resetImport = () => {
  importPreview.value = null
  activeDraft.value = null
  draftAnswers.value = {}
  draftAnalyses.value = {}
  sourceFile.value = null
  importForm.value = emptyImportForm()
}

const reload = async () => {
  await Promise.all([loadPrivateDrafts(), loadStorageUsage()])
}

defineExpose({ reload })
</script>

<style scoped>
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

.preview-question,
.draft-question {
  padding: var(--lp-space-4);
  margin-bottom: var(--lp-space-3);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
}

.preview-question-title,
.draft-question-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--lp-space-3);
}

.preview-question ul,
.draft-question ul {
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

.draft-review {
  margin-top: var(--lp-space-4);
}

.draft-review-tools {
  display: flex;
  justify-content: flex-end;
  margin: 0 0 var(--lp-space-3);
}

.ai-suggestion {
  padding: var(--lp-space-3) var(--lp-space-4);
  color: var(--lp-text-secondary);
  background: var(--lp-surface-inset);
  border-radius: var(--lp-radius-sm);
  white-space: pre-wrap;
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
