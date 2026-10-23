<template>
  <section class="draft-review">
    <div class="preview-summary">
      <strong>{{ draft.title }}</strong
      ><span>{{ draft.reviewedQuestionCount }}/{{ draft.questionCount }} 题已人工复核</span>
    </div>
    <div v-if="draft.originalFileAvailable" class="draft-review-tools">
      <LtButton :disabled="sourceDownloading" @click="downloadDraftSource">下载草稿原文件</LtButton>
    </div>
    <p v-if="downloadError" role="alert" class="failure">
      {{ downloadError }} <LtButton @click="downloadDraftSource">重试下载</LtButton>
    </p>
    <p class="notice">AI 只提供建议，不会直接成为判分答案；每题必须由你选择答案并确认解析。</p>
    <p v-if="reviewNotice" role="status">{{ reviewNotice }}</p>
    <article v-for="question in draft.questions" :key="question.id" class="draft-question">
      <div class="draft-question-title">
        <strong>{{ question.sortOrder }}. {{ question.content }}</strong
        ><span class="tag" :data-tone="question.reviewStatus === 'REVIEWED' ? 'success' : 'warning'">{{
          question.reviewStatus === 'REVIEWED' ? '已复核' : '待复核'
        }}</span>
      </div>
      <ul>
        <li v-for="option in question.options" :key="option.label">{{ option.label }}. {{ option.content }}</li>
      </ul>
      <p v-if="question.generationStatus === 'GENERATED'" class="muted">
        AI 建议：{{ question.aiAnswerLabels.join('、') }} · {{ question.aiAnalysis }}
      </p>
      <p v-else-if="question.generationStatus === 'NOT_REQUIRED'" class="muted">
        原资料答案：{{ question.originalAnswerLabels.join('、') || '未提供' }}
      </p>
      <LtButton
        v-if="question.generationStatus === 'PENDING'"
        :disabled="!!questionStates[question.id]?.operation"
        @click="generateDraftAnswer(question.id)"
        >生成 AI 答案与解析</LtButton
      >
      <fieldset
        class="review-options"
        :aria-label="`第${question.sortOrder}题人工确认答案`"
        :disabled="question.reviewStatus === 'REVIEWED' || questionStates[question.id]?.operation === 'review'"
      >
        <legend>人工确认答案</legend>
        <label v-for="option in question.options" :key="option.label"
          ><input
            v-model="draftAnswers[question.id]"
            type="checkbox"
            :value="option.label"
            @change="questionStates[question.id]!.answerEdited = true"
          />{{ option.label }}</label
        >
      </fieldset>
      <label class="field"
        >人工确认解析<textarea
          v-model="draftAnalyses[question.id]"
          :aria-label="`第${question.sortOrder}题人工确认解析`"
          :rows="3"
          maxlength="10000"
          :disabled="question.reviewStatus === 'REVIEWED' || questionStates[question.id]?.operation === 'review'"
          @input="questionStates[question.id]!.analysisEdited = true"
        />
      </label>
      <p v-if="questionStates[question.id]?.phase" role="status" class="muted">
        {{
          questionStates[question.id]?.phase === 'waiting'
            ? '等待前一题处理完成'
            : questionStates[question.id]?.operation === 'generate'
              ? '正在生成 AI 建议…'
              : '正在保存复核结果…'
        }}
      </p>
      <p v-if="questionStates[question.id]?.error" role="alert" class="failure">
        {{ questionStates[question.id]!.error }}
      </p>
      <LtButton
        v-if="question.reviewStatus !== 'REVIEWED'"
        variant="primary"
        :disabled="question.generationStatus === 'PENDING' || !!questionStates[question.id]?.operation"
        @click="reviewDraftQuestion(question.id)"
        >确认本题</LtButton
      >
    </article>
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { LtButton } from '@/components/living'
import { downloadPrivateExamDraftSourceFile } from '@/api/exam'
import { usePrivateExamDraftReview } from './usePrivateExamDraftReview'
import type { PrivateExamDraft } from '@/api/exam'

const props = defineProps<{
  draft: PrivateExamDraft
}>()

const emit = defineEmits<{
  updated: [draft: PrivateExamDraft]
}>()

const reviewNotice = ref('')
const downloadError = ref('')
const { draftAnswers, draftAnalyses, questionStates, generateDraftAnswer, reviewDraftQuestion } =
  usePrivateExamDraftReview(
    () => props.draft,
    (draft) => emit('updated', draft),
    (message) => {
      reviewNotice.value = message
    },
  )
const sourceDownloading = ref(false)
let downloadVersion = 0
watch(
  () => props.draft.id,
  () => {
    downloadVersion++
    sourceDownloading.value = false
    downloadError.value = ''
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
  downloadError.value = ''
  try {
    const response = await downloadPrivateExamDraftSourceFile(draft.id, { errorDisplay: 'inline' })
    if (version !== downloadVersion) return
    saveSourceFile(
      response.data,
      String(response.headers['content-type'] || 'application/octet-stream'),
      draft.sourceName!,
    )
  } catch {
    if (version === downloadVersion) downloadError.value = '原文件下载失败'
  } finally {
    if (version === downloadVersion) sourceDownloading.value = false
  }
}
</script>
