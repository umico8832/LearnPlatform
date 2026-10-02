<template>
  <div class="feedback-area">
    <p v-if="loading" class="feedback-text" role="status">正在读取反馈…</p>
    <div v-else-if="loadError" class="feedback-error" role="alert">
      <span>{{ loadError }}</span
      ><el-button size="small" @click="load">重试</el-button>
    </div>
    <div v-else-if="feedback.helpful === null" class="feedback-prompt">
      <span class="feedback-text">这个讲解对你有帮助吗？</span>
      <el-button size="small" :loading="submitting" @click="submitHelpful(true)">有帮助</el-button>
      <el-button size="small" :loading="submitting" @click="submitHelpful(false)">没帮助</el-button>
    </div>

    <div v-else class="feedback-done">
      <el-tag :type="feedback.helpful ? 'success' : 'warning'" size="small" effect="plain">
        {{ feedback.helpful ? '已反馈：有帮助' : '已反馈：没帮助' }}
      </el-tag>
      <el-button
        v-if="feedback.helpful === false && !showCommentInput && !feedback.comment"
        size="small"
        text
        type="primary"
        :disabled="submitting"
        @click="showCommentInput = true"
      >
        补充说明
      </el-button>
      <el-button size="small" text type="info" :disabled="submitting" @click="feedback.helpful = null"
        >重新反馈</el-button
      >
    </div>

    <div v-if="showCommentInput" class="feedback-comment">
      <el-input
        v-model="feedback.comment"
        type="textarea"
        :rows="2"
        placeholder="请告诉我们哪里可以改进（可选）..."
        maxlength="500"
        show-word-limit
        size="small"
        :disabled="submitting"
      />
      <el-button size="small" type="primary" :loading="submitting" class="comment-submit" @click="submitComment">
        提交
      </el-button>
    </div>
    <div v-if="submitError" class="feedback-error" role="alert">
      <span>{{ submitError }}</span
      ><el-button size="small" :loading="submitting" @click="retrySubmission">重试</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onUnmounted, reactive, ref, watch } from 'vue'
import { getAssetFeedback, submitAssetFeedback } from '@/api/ai'
import type { AiAssetType } from '@/api/ai'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'

const props = defineProps<{
  questionId: number
  assetType: AiAssetType
  available: boolean
}>()

const feedback = reactive<{ helpful: boolean | null; comment: string }>({ helpful: null, comment: '' })
const submitting = ref(false)
const loading = ref(false)
const showCommentInput = ref(false)
const loadError = ref('')
const submitError = ref('')
let alive = true
let generation = 0
let retryHelpful: boolean | null = null

function reset() {
  feedback.helpful = null
  feedback.comment = ''
  submitting.value = false
  loading.value = false
  showCommentInput.value = false
  loadError.value = ''
  submitError.value = ''
  retryHelpful = null
}

async function load() {
  if (!props.available || loading.value) return
  const requestGeneration = generation
  const authSession = getAuthSessionVersion()
  loadError.value = ''
  loading.value = true
  try {
    const response = await getAssetFeedback(props.questionId, props.assetType, { errorDisplay: 'inline' })
    if (!alive || requestGeneration !== generation || authSession !== getAuthSessionVersion()) return
    if (response?.code === 0 && response.data) {
      feedback.helpful = response.data.helpful
      feedback.comment = response.data.comment || ''
    }
  } catch {
    if (alive && requestGeneration === generation && authSession === getAuthSessionVersion())
      loadError.value = '反馈暂时无法读取。'
  } finally {
    if (alive && requestGeneration === generation && authSession === getAuthSessionVersion()) loading.value = false
  }
}

async function submitHelpful(helpful: boolean) {
  if (submitting.value || loading.value || loadError.value) return
  const requestGeneration = generation
  const authSession = getAuthSessionVersion()
  const questionId = props.questionId
  const assetType = props.assetType
  submitting.value = true
  retryHelpful = helpful
  submitError.value = ''
  try {
    const response = await submitAssetFeedback(props.questionId, props.assetType, helpful, undefined, {
      errorDisplay: 'inline',
    })
    if (!isCurrent(requestGeneration, authSession, questionId, assetType)) return
    if (response?.code === 0) {
      feedback.helpful = helpful
      if (helpful) showCommentInput.value = false
    }
  } catch {
    if (isCurrent(requestGeneration, authSession, questionId, assetType)) submitError.value = '提交反馈失败，请重试。'
  } finally {
    if (isCurrent(requestGeneration, authSession, questionId, assetType)) submitting.value = false
  }
}

async function submitComment() {
  if (feedback.helpful === null || submitting.value || loading.value) return
  const requestGeneration = generation
  const authSession = getAuthSessionVersion()
  const questionId = props.questionId
  const assetType = props.assetType
  submitting.value = true
  retryHelpful = null
  submitError.value = ''
  try {
    const response = await submitAssetFeedback(
      props.questionId,
      props.assetType,
      feedback.helpful,
      feedback.comment || undefined,
      { errorDisplay: 'inline' },
    )
    if (!isCurrent(requestGeneration, authSession, questionId, assetType)) return
    if (response?.code === 0) showCommentInput.value = false
  } catch {
    if (isCurrent(requestGeneration, authSession, questionId, assetType)) submitError.value = '提交反馈失败，请重试。'
  } finally {
    if (isCurrent(requestGeneration, authSession, questionId, assetType)) submitting.value = false
  }
}

function retrySubmission() {
  if (retryHelpful === null) void submitComment()
  else void submitHelpful(retryHelpful)
}

function isCurrent(requestGeneration: number, authSession: number, questionId: number, assetType: AiAssetType) {
  return (
    alive &&
    requestGeneration === generation &&
    authSession === getAuthSessionVersion() &&
    questionId === props.questionId &&
    assetType === props.assetType
  )
}

watch(
  () => [props.questionId, props.assetType, props.available] as const,
  () => {
    generation++
    reset()
    void load()
  },
  { immediate: true },
)
const unsubscribeAuth = onAuthSessionChange(() => {
  generation++
  reset()
})
onUnmounted(() => {
  alive = false
  generation++
  unsubscribeAuth()
})
</script>

<style scoped>
.feedback-area {
  margin-top: var(--lp-space-4);
  padding-top: var(--lp-space-3);
  border-top: var(--lp-border-hairline);
}

.feedback-prompt,
.feedback-done {
  display: flex;
  align-items: center;
  gap: var(--lp-space-2);
  flex-wrap: wrap;
}

.feedback-text {
  font-size: var(--lp-text-sm);
  color: var(--lp-text-secondary);
}

.feedback-comment {
  margin-top: var(--lp-space-2);
  max-width: 25rem;
}

.comment-submit {
  margin-top: var(--lp-space-2);
}
.feedback-error {
  display: flex;
  gap: var(--lp-space-2);
  align-items: center;
  color: var(--lp-text);
  font-size: var(--lp-text-sm);
}

@media (max-width: 720px) {
  .feedback-prompt,
  .feedback-done {
    gap: var(--lp-space-2);
  }

  .feedback-comment {
    max-width: 100%;
  }
}
</style>
