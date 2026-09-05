<template>
  <el-dialog v-model="visible" :title="action === 1 ? '通过投稿' : '拒绝投稿'" width="500px">
    <el-form label-width="80px">
      <el-form-item label="审核意见">
        <el-input
          v-model="comment"
          type="textarea"
          :rows="5"
          :placeholder="action === 1 ? '审核通过意见（可选）' : '请输入拒绝原因'"
        />
        <el-button
          type="primary"
          link
          size="small"
          class="generate-button"
          :loading="generatingComment"
          @click="generateComment"
        >
          🤖 AI 一键填充审核意见
        </el-button>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button :type="action === 1 ? 'success' : 'danger'" :loading="reviewing" @click="submit">
        {{ action === 1 ? '确认通过' : '确认拒绝' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { generateReviewComment, reviewSubmission } from '@/api/submission'
import type { QuestionSubmissionVO } from '@/api/submission'

const emit = defineEmits<{ reviewed: [] }>()

const visible = ref(false)
const target = ref<QuestionSubmissionVO | null>(null)
const action = ref(1)
const comment = ref('')
const reviewing = ref(false)
const generatingComment = ref(false)

function open(submission: QuestionSubmissionVO, reviewAction: number) {
  target.value = submission
  action.value = reviewAction
  comment.value = ''
  visible.value = true
}

async function submit() {
  if (!target.value) return
  if (action.value === 2 && !comment.value.trim()) {
    ElMessage.warning('拒绝时请填写审核意见')
    return
  }
  reviewing.value = true
  try {
    const response = await reviewSubmission(target.value.id, {
      status: action.value,
      reviewComment: comment.value || undefined,
    })
    if (response.code !== 0) {
      ElMessage.error(response.message || '操作失败')
      return
    }
    ElMessage.success(action.value === 1 ? '已通过' : '已拒绝')
    visible.value = false
    emit('reviewed')
  } finally {
    reviewing.value = false
  }
}

async function generateComment() {
  if (!target.value) return
  generatingComment.value = true
  try {
    const response = await generateReviewComment(target.value.id)
    if (response.code === 0 && response.data) {
      comment.value = response.data
      ElMessage.success('AI 审核意见已填充')
    } else {
      ElMessage.error(response.message || '生成审核意见失败')
    }
  } catch {
    ElMessage.error('生成审核意见请求失败')
  } finally {
    generatingComment.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.generate-button {
  margin-top: 4px;
}
</style>
