<template>
  <div class="feedback-area">
    <div v-if="feedback.helpful === null" class="feedback-prompt">
      <span class="feedback-text">这个讲解对你有帮助吗？</span>
      <el-button size="small" :loading="submitting" @click="submitHelpful(true)">👍 有帮助</el-button>
      <el-button size="small" :loading="submitting" @click="submitHelpful(false)">👎 没帮助</el-button>
    </div>

    <div v-else class="feedback-done">
      <el-tag :type="feedback.helpful ? 'success' : 'warning'" size="small" effect="plain">
        {{ feedback.helpful ? '👍 已反馈：有帮助' : '👎 已反馈：没帮助' }}
      </el-tag>
      <el-button
        v-if="feedback.helpful === false && !showCommentInput && !feedback.comment"
        size="small"
        text
        type="primary"
        @click="showCommentInput = true"
      >
        补充说明
      </el-button>
      <el-button size="small" text type="info" @click="feedback.helpful = null">重新反馈</el-button>
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
      />
      <el-button size="small" type="primary" :loading="submitting" class="comment-submit" @click="submitComment">
        提交
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { getAssetFeedback, submitAssetFeedback } from '@/api/ai'
import type { AiAssetType } from '@/api/ai'

const props = defineProps<{
  questionId: number
  assetType: AiAssetType
  available: boolean
}>()

const feedback = reactive<{ helpful: boolean | null; comment: string }>({ helpful: null, comment: '' })
const submitting = ref(false)
const showCommentInput = ref(false)

function reset() {
  feedback.helpful = null
  feedback.comment = ''
  submitting.value = false
  showCommentInput.value = false
}

async function load() {
  if (!props.available) return
  try {
    const response = await getAssetFeedback(props.questionId, props.assetType)
    if (response?.code === 0 && response.data) {
      feedback.helpful = response.data.helpful
      feedback.comment = response.data.comment || ''
    }
  } catch {
    // Feedback is optional and must not block learning content.
  }
}

async function submitHelpful(helpful: boolean) {
  submitting.value = true
  try {
    const response = await submitAssetFeedback(props.questionId, props.assetType, helpful)
    if (response?.code === 0) {
      feedback.helpful = helpful
      if (helpful) showCommentInput.value = false
    }
  } catch {
    // Feedback is optional and can be retried in place.
  } finally {
    submitting.value = false
  }
}

async function submitComment() {
  if (feedback.helpful === null) return
  submitting.value = true
  try {
    const response = await submitAssetFeedback(
      props.questionId,
      props.assetType,
      feedback.helpful,
      feedback.comment || undefined,
    )
    if (response?.code === 0) showCommentInput.value = false
  } catch {
    // Feedback is optional and can be retried in place.
  } finally {
    submitting.value = false
  }
}

watch(
  () => [props.questionId, props.assetType, props.available] as const,
  () => {
    reset()
    void load()
  },
  { immediate: true },
)
</script>

<style scoped>
.feedback-area {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}

.feedback-prompt,
.feedback-done {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.feedback-text {
  font-size: 13px;
  color: #606266;
}

.feedback-comment {
  margin-top: 8px;
  max-width: 400px;
}

.comment-submit {
  margin-top: 8px;
}

@media (max-width: 720px) {
  .feedback-prompt,
  .feedback-done {
    gap: 6px;
  }

  .feedback-comment {
    max-width: 100%;
  }
}
</style>
