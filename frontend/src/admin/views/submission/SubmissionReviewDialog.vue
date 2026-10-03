<template>
  <el-dialog
    v-model="visible"
    :title="action === 1 ? '通过投稿' : '拒绝投稿'"
    width="500px"
    :close-on-click-modal="!busy()"
    :close-on-press-escape="!busy()"
    :show-close="!busy()"
    ><el-form label-width="80px" :disabled="busy()"
      ><el-form-item label="审核意见"
        ><el-input
          v-model="comment"
          type="textarea"
          :rows="5"
          :disabled="busy()"
          :placeholder="action === 1 ? '审核通过意见（可选）' : '请输入拒绝原因'"
        /><el-button
          type="primary"
          link
          size="small"
          :loading="generatingComment"
          :disabled="busy()"
          @click="generateComment"
          >生成审核意见</el-button
        ></el-form-item
      ></el-form
    >
    <p v-if="generationError" class="review-error" role="alert">
      {{ generationError }} <button type="button" :disabled="busy()" @click="generateComment">重新生成</button>
    </p>
    <p v-if="reviewError" class="review-error" role="alert">
      {{ reviewError }} <button type="button" :disabled="busy()" @click="submit">重试</button>
    </p>
    <template #footer
      ><el-button :disabled="busy()" @click="visible = false">取消</el-button
      ><el-button
        :type="action === 1 ? 'success' : 'danger'"
        :loading="reviewing"
        :disabled="generatingComment"
        @click="submit"
        >{{ action === 1 ? '确认通过' : '确认拒绝' }}</el-button
      ></template
    ></el-dialog
  >
</template>
<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { generateReviewComment, reviewSubmission } from '@/api/submission'
import type { QuestionSubmissionVO } from '@/api/submission'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'
const emit = defineEmits<{ reviewed: [] }>(),
  visible = ref(false),
  target = ref<QuestionSubmissionVO | null>(null),
  action = ref(1),
  comment = ref(''),
  reviewing = ref(false),
  generatingComment = ref(false),
  reviewError = ref(''),
  generationError = ref('')
let alive = true,
  version = 0
const busy = () => reviewing.value || generatingComment.value
function current(v: number, s: number, id: number) {
  return alive && v === version && s === getAuthSessionVersion() && target.value?.id === id && visible.value
}
function invalidate() {
  version++
  reviewing.value = false
  generatingComment.value = false
  reviewError.value = ''
  generationError.value = ''
}
function open(submission: QuestionSubmissionVO, reviewAction: number) {
  invalidate()
  target.value = submission
  action.value = reviewAction
  comment.value = ''
  visible.value = true
}
async function submit() {
  if (!target.value || busy()) return
  if (action.value === 2 && !comment.value.trim()) {
    ElMessage.warning('拒绝时请填写审核意见')
    return
  }
  const v = ++version,
    s = getAuthSessionVersion(),
    id = target.value.id
  reviewing.value = true
  reviewError.value = ''
  generationError.value = ''
  try {
    const r = await reviewSubmission(
      id,
      { status: action.value, reviewComment: comment.value || undefined },
      { errorDisplay: 'inline' },
    )
    if (!current(v, s, id)) return
    if (r.code !== 0) {
      reviewError.value = r.message || '审核失败，请重试'
      return
    }
    ElMessage.success(action.value === 1 ? '已通过' : '已拒绝')
    visible.value = false
    emit('reviewed')
  } catch {
    if (current(v, s, id)) reviewError.value = '审核失败，请重试'
  } finally {
    if (current(v, s, id)) reviewing.value = false
  }
}
async function generateComment() {
  if (!target.value || busy()) return
  const v = ++version,
    s = getAuthSessionVersion(),
    id = target.value.id
  generatingComment.value = true
  reviewError.value = ''
  generationError.value = ''
  try {
    const r = await generateReviewComment(id, { errorDisplay: 'inline' })
    if (current(v, s, id) && r.code === 0 && r.data) comment.value = r.data
    else if (current(v, s, id)) generationError.value = r.message || '生成审核意见失败'
  } catch {
    if (current(v, s, id)) generationError.value = '生成审核意见失败，请重试'
  } finally {
    if (current(v, s, id)) generatingComment.value = false
  }
}
watch(
  visible,
  (value) => {
    if (!value) invalidate()
  },
  { flush: 'sync' },
)
const unsubscribe = onAuthSessionChange(() => {
  invalidate()
  visible.value = false
  target.value = null
  comment.value = ''
})
onUnmounted(() => {
  alive = false
  invalidate()
  unsubscribe()
})
defineExpose({ open })
</script>
<style scoped>
.review-error {
  color: var(--lp-danger);
  margin: var(--lp-space-2) 0 0;
}
.review-error button {
  border: 0;
  border-radius: var(--lp-radius-sm);
  padding: var(--lp-space-1) var(--lp-space-2);
  background: transparent;
  color: var(--lp-primary);
  font: inherit;
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}
.review-error button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
</style>
