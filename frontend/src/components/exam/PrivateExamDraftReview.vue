<template>
  <section class="draft-review">
    <div class="preview-summary">
      <strong>{{ draft.title }}</strong>
      <span>{{ draft.reviewedQuestionCount }}/{{ draft.questionCount }} 题已人工复核</span>
    </div>
    <div v-if="draft.originalFileAvailable" class="draft-review-tools">
      <el-button plain :loading="sourceDownloading" @click="downloadDraftSource">下载草稿原文件</el-button>
    </div>
    <el-alert
      title="AI 只提供建议，不会直接成为判分答案；每题必须由你选择答案并确认解析。"
      type="warning"
      :closable="false"
    />
    <article v-for="question in draft.questions" :key="question.id" class="draft-question">
      <div class="draft-question-title">
        <strong>{{ question.sortOrder }}. {{ question.content }}</strong>
        <el-tag :type="question.reviewStatus === 'REVIEWED' ? 'success' : 'warning'">
          {{ question.reviewStatus === 'REVIEWED' ? '已复核' : '待复核' }}
        </el-tag>
      </div>
      <ul>
        <li v-for="option in question.options" :key="option.label">{{ option.label }}. {{ option.content }}</li>
      </ul>
      <p v-if="question.generationStatus === 'GENERATED'" class="ai-suggestion">
        AI 建议：{{ question.aiAnswerLabels.join('、') }} · {{ question.aiAnalysis }}
      </p>
      <p v-else-if="question.generationStatus === 'NOT_REQUIRED'" class="ai-suggestion">
        原资料答案：{{ question.originalAnswerLabels.join('、') || '未提供' }}
      </p>
      <el-button
        v-if="question.generationStatus === 'PENDING'"
        type="primary"
        plain
        :loading="
          questionStates[question.id]?.operation === 'generate' && questionStates[question.id]?.phase === 'running'
        "
        :disabled="!!questionStates[question.id]?.operation"
        @click="generateDraftAnswer(question.id)"
      >
        生成 AI 答案与解析
      </el-button>
      <el-form-item label="人工确认答案">
        <el-checkbox-group
          v-model="draftAnswers[question.id]"
          :aria-label="`第${question.sortOrder}题人工确认答案`"
          :disabled="question.reviewStatus === 'REVIEWED' || questionStates[question.id]?.operation === 'review'"
          @update:model-value="questionStates[question.id]!.answerEdited = true"
        >
          <el-checkbox v-for="option in question.options" :key="option.label" :value="option.label">
            {{ option.label }}
          </el-checkbox>
        </el-checkbox-group>
      </el-form-item>
      <el-form-item label="人工确认解析">
        <el-input
          v-model="draftAnalyses[question.id]"
          :aria-label="`第${question.sortOrder}题人工确认解析`"
          type="textarea"
          :rows="3"
          maxlength="10000"
          :disabled="question.reviewStatus === 'REVIEWED' || questionStates[question.id]?.operation === 'review'"
          @update:model-value="questionStates[question.id]!.analysisEdited = true"
        />
      </el-form-item>
      <p v-if="questionStates[question.id]?.phase" role="status" class="question-progress">
        {{
          questionStates[question.id]?.phase === 'waiting'
            ? '等待前一题处理完成'
            : questionStates[question.id]?.operation === 'generate'
              ? '正在生成 AI 建议…'
              : '正在保存复核结果…'
        }}
      </p>
      <el-alert
        v-if="questionStates[question.id]?.error"
        :title="questionStates[question.id]!.error"
        type="error"
        :closable="false"
        show-icon
        class="question-error"
      />
      <el-button
        v-if="question.reviewStatus !== 'REVIEWED'"
        type="success"
        :loading="
          questionStates[question.id]?.operation === 'review' && questionStates[question.id]?.phase === 'running'
        "
        :disabled="question.generationStatus === 'PENDING' || !!questionStates[question.id]?.operation"
        @click="reviewDraftQuestion(question.id)"
      >
        确认本题
      </el-button>
    </article>
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { downloadPrivateExamDraftSourceFile } from '@/api/exam'
import { usePrivateExamDraftReview } from './usePrivateExamDraftReview'
import type { PrivateExamDraft } from '@/api/exam'

const props = defineProps<{
  draft: PrivateExamDraft
}>()

const emit = defineEmits<{
  updated: [draft: PrivateExamDraft]
}>()

const { draftAnswers, draftAnalyses, questionStates, generateDraftAnswer, reviewDraftQuestion } =
  usePrivateExamDraftReview(
    () => props.draft,
    (draft) => emit('updated', draft),
  )
const sourceDownloading = ref(false)
let downloadVersion = 0
watch(
  () => props.draft.id,
  () => {
    downloadVersion++
    sourceDownloading.value = false
  },
  { flush: 'sync' },
)
onBeforeUnmount(() => {
  downloadVersion++
})

function saveSourceFile(data: BlobPart, mediaType: string, filename: string) {
  const url = window.URL.createObjectURL(new Blob([data], { type: mediaType }))
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  window.URL.revokeObjectURL(url)
}

async function downloadDraftSource() {
  if (!props.draft.sourceName || sourceDownloading.value) return
  const draft = props.draft
  const version = ++downloadVersion
  sourceDownloading.value = true
  try {
    const response = await downloadPrivateExamDraftSourceFile(draft.id)
    if (version !== downloadVersion) return
    saveSourceFile(
      response.data,
      String(response.headers['content-type'] || 'application/octet-stream'),
      draft.sourceName!,
    )
  } catch {
    if (version === downloadVersion) ElMessage.error('原文件下载失败')
  } finally {
    if (version === downloadVersion) sourceDownloading.value = false
  }
}
</script>

<style scoped>
.draft-review {
  margin-top: var(--lp-space-4);
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

.draft-review-tools {
  display: flex;
  justify-content: flex-end;
  margin: 0 0 var(--lp-space-3);
}

.draft-question {
  padding: var(--lp-space-4);
  margin-bottom: var(--lp-space-3);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
}

.draft-question-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--lp-space-3);
}

.draft-question ul {
  margin: var(--lp-space-3) 0;
  padding-left: var(--lp-space-6);
  color: var(--lp-text-secondary);
}

.question-progress {
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}

.question-error {
  margin-bottom: var(--lp-space-3);
}

.ai-suggestion {
  padding: var(--lp-space-3) var(--lp-space-4);
  color: var(--lp-text-secondary);
  background: var(--lp-surface-inset);
  border-radius: var(--lp-radius-sm);
  white-space: pre-wrap;
}

@media (max-width: 640px) {
  .preview-summary {
    flex-direction: column;
  }
}
</style>
