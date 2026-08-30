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
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  downloadPrivateExamDraftSourceFile,
  generatePrivateExamDraftAnswer,
  reviewPrivateExamDraftQuestion,
} from '@/api/exam'
import type { PrivateExamDraft } from '@/api/exam'

const props = defineProps<{
  draft: PrivateExamDraft
}>()

const emit = defineEmits<{
  updated: [draft: PrivateExamDraft]
}>()

const generatingQuestionId = ref<number | null>(null)
const reviewingQuestionId = ref<number | null>(null)
const draftAnswers = ref<Record<number, string[]>>({})
const draftAnalyses = ref<Record<number, string>>({})
const sourceDownloading = ref(false)

watch(
  () => props.draft,
  (draft) => {
    draftAnswers.value = {}
    draftAnalyses.value = {}
    draft.questions.forEach((question) => {
      draftAnswers.value[question.id] = [
        ...(question.finalAnswerLabels.length
          ? question.finalAnswerLabels
          : question.aiAnswerLabels.length
            ? question.aiAnswerLabels
            : question.originalAnswerLabels),
      ]
      draftAnalyses.value[question.id] =
        question.finalAnalysis || question.aiAnalysis || question.originalAnalysis || ''
    })
  },
  { immediate: true },
)

async function generateDraftAnswer(questionId: number) {
  generatingQuestionId.value = questionId
  try {
    const res = await generatePrivateExamDraftAnswer(props.draft.id, questionId)
    if (res.code === 0 && res.data) {
      emit('updated', res.data)
      ElMessage.success('AI 建议已生成，请人工核对')
    } else ElMessage.error(res.message || 'AI 生成失败')
  } catch {
    ElMessage.error('AI 生成失败，请稍后重试')
  } finally {
    generatingQuestionId.value = null
  }
}

async function reviewDraftQuestion(questionId: number) {
  const question = props.draft.questions.find((item) => item.id === questionId)
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
    const res = await reviewPrivateExamDraftQuestion(props.draft.id, questionId, {
      answerLabels: answers,
      analysis,
    })
    if (res.code === 0 && res.data) {
      emit('updated', res.data)
      ElMessage.success('本题已人工复核')
    } else ElMessage.error(res.message || '复核失败')
  } catch {
    ElMessage.error('复核失败')
  } finally {
    reviewingQuestionId.value = null
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

async function downloadDraftSource() {
  if (!props.draft.sourceName) return
  sourceDownloading.value = true
  try {
    const response = await downloadPrivateExamDraftSourceFile(props.draft.id)
    saveSourceFile(
      response.data,
      String(response.headers['content-type'] || 'application/octet-stream'),
      props.draft.sourceName,
    )
  } catch {
    ElMessage.error('原文件下载失败')
  } finally {
    sourceDownloading.value = false
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
