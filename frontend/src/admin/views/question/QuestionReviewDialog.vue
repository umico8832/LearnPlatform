<template>
  <el-dialog
    v-model="visible"
    title="题目复审"
    width="min(760px, calc(100vw - 32px))"
    destroy-on-close
    :close-on-click-modal="!submitting"
    :close-on-press-escape="!submitting"
    :show-close="!submitting"
  >
    <div v-if="question" class="review-question-summary">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="题目ID">{{ question.id }}</el-descriptions-item>
        <el-descriptions-item label="来源">
          <el-tag size="small" :type="sourceTypeTag(question.sourceType)">
            {{ sourceTypeLabel(question.sourceType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="题型">{{ questionTypeLabel(question.questionType) }}</el-descriptions-item>
        <el-descriptions-item label="难度">{{ question.difficulty }} / 5</el-descriptions-item>
        <el-descriptions-item label="累计复审">{{ question.reviewRounds ?? 0 }} 次</el-descriptions-item>
        <el-descriptions-item label="下次复审">{{ question.nextReviewTime ?? '未设置' }}</el-descriptions-item>
        <el-descriptions-item label="题干" :span="2">
          <div class="review-question-content">{{ question.content }}</div>
        </el-descriptions-item>
      </el-descriptions>
    </div>

    <el-form :model="form" label-width="90px" :disabled="submitting">
      <div class="review-suggestion-actions">
        <el-button :icon="DataAnalysis" :loading="suggestionLoading" :disabled="submitting" @click="loadSuggestion">
          AI 复审建议
        </el-button>
        <span v-if="suggestion" class="review-suggestion-meta">
          建议：{{ reviewActionLabel(suggestion.recommendation) }} · 置信分 {{ suggestion.confidenceScore }}
        </span>
      </div>

      <p v-if="suggestionError" class="review-inline-error" role="alert">
        {{ suggestionError }} 可再次点击“AI 复审建议”重试。
      </p>

      <el-alert
        v-if="suggestion"
        class="review-suggestion-panel"
        :type="
          suggestion.recommendation === 'REJECT'
            ? 'error'
            : suggestion.recommendation === 'REVISE'
              ? 'warning'
              : 'success'
        "
        :closable="false"
        show-icon
      >
        <template #title>{{ suggestion.summary }}</template>
        <div class="review-suggestion-content">
          <p v-if="suggestion.answerAnalysis">{{ suggestion.answerAnalysis }}</p>
          <p v-if="suggestion.knowledgeAnalysis">{{ suggestion.knowledgeAnalysis }}</p>
          <div v-if="suggestion.riskPoints?.length">
            <strong>风险点</strong>
            <ul>
              <li v-for="item in suggestion.riskPoints" :key="item">{{ item }}</li>
            </ul>
          </div>
          <div v-if="suggestion.suggestions?.length">
            <strong>修订建议</strong>
            <ul>
              <li v-for="item in suggestion.suggestions" :key="item">{{ item }}</li>
            </ul>
          </div>
          <el-button size="small" text type="primary" @click="applySuggestion">应用到表单</el-button>
        </div>
      </el-alert>

      <el-form-item label="复审动作">
        <el-radio-group v-model="form.action">
          <el-radio-button value="APPROVE">通过</el-radio-button>
          <el-radio-button value="REVISE">修订</el-radio-button>
          <el-radio-button value="REJECT">废弃</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="form.action === 'REVISE'" label="修订内容">
        <el-input v-model="form.newContent" type="textarea" :rows="3" placeholder="修订后的题干" />
      </el-form-item>
      <el-form-item v-if="form.action === 'REVISE'" label="修订难度">
        <el-rate v-model="form.newDifficulty" :max="5" />
      </el-form-item>
      <el-form-item label="复审意见">
        <el-input v-model="form.comment" type="textarea" :rows="2" placeholder="请输入复审意见" />
      </el-form-item>
    </el-form>

    <div class="review-records">
      <h3>历史复审记录</h3>
      <p v-if="recordsLoading" role="status">正在读取复审记录…</p>
      <div v-else-if="recordsError" class="review-inline-error" role="alert">
        {{ recordsError }} <el-button text :disabled="submitting" @click="loadRecords">重试读取</el-button>
      </div>
      <p v-else-if="!records.length" class="review-empty">暂无复审记录</p>
      <el-timeline v-else>
        <el-timeline-item v-for="record in records" :key="record.id" :timestamp="record.createTime" placement="top">
          <el-card shadow="never" body-style="padding: 8px 12px;">
            <div class="review-record-heading">
              <el-tag
                size="small"
                :type="record.action === 'APPROVE' ? 'success' : record.action === 'REJECT' ? 'danger' : 'warning'"
              >
                {{ record.action === 'APPROVE' ? '通过' : record.action === 'REVISE' ? '修订' : '废弃' }}
              </el-tag>
              <span>{{ record.reviewerName }} · {{ record.reviewType }}</span>
            </div>
            <div class="review-record-comment">{{ record.comment }}</div>
          </el-card>
        </el-timeline-item>
      </el-timeline>
    </div>

    <p v-if="submitError" class="review-inline-error" role="alert">{{ submitError }}</p>
    <template #footer>
      <el-button :disabled="submitting" @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitReview">提交复审</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { onBeforeUnmount, reactive, ref, watch } from 'vue'
import { DataAnalysis } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  getReviewRecords,
  getReviewSuggestion,
  performReReview,
  type QuestionReviewRecordVO,
  type QuestionReviewSuggestionVO,
  type QuestionVO,
} from '@/api/question'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'
import { errorMessage } from '@/utils/errors'
import { questionTypeLabel, reviewActionLabel, sourceTypeLabel, sourceTypeTag } from './questionManagePresentation'

const emit = defineEmits<{ reviewed: [] }>()
const visible = ref(false)
const question = ref<QuestionVO | null>(null)
const submitting = ref(false)
const submitError = ref('')
const records = ref<QuestionReviewRecordVO[]>([])
const recordsLoading = ref(false)
const recordsError = ref('')
const suggestion = ref<QuestionReviewSuggestionVO | null>(null)
const suggestionLoading = ref(false)
const suggestionError = ref('')
const form = reactive({ action: 'APPROVE', newContent: '', newDifficulty: 3, comment: '' })
let generation = 0
let alive = true
const current = (version: number, session: number) =>
  alive && visible.value && generation === version && getAuthSessionVersion() === session
function invalidate() {
  generation++
  submitting.value = false
  suggestionLoading.value = false
  recordsLoading.value = false
}
watch(
  visible,
  (value) => {
    if (!value) invalidate()
  },
  { flush: 'sync' },
)
const unsubscribe = onAuthSessionChange(() => {
  visible.value = false
  invalidate()
  question.value = null
})
onBeforeUnmount(() => {
  alive = false
  invalidate()
  unsubscribe()
})

async function open(target: QuestionVO) {
  invalidate()
  question.value = target
  Object.assign(form, { action: 'APPROVE', newContent: target.content, newDifficulty: target.difficulty, comment: '' })
  records.value = []
  suggestion.value = null
  submitError.value = suggestionError.value = recordsError.value = ''
  visible.value = true
  await loadRecords()
}
async function loadRecords() {
  if (!question.value || recordsLoading.value || submitting.value) return
  const version = generation,
    session = getAuthSessionVersion(),
    id = question.value.id
  recordsLoading.value = true
  recordsError.value = ''
  try {
    const res = await getReviewRecords(id, { errorDisplay: 'inline' })
    if (!current(version, session)) return
    if (!res.data) throw new Error('复审记录暂时无法读取。')
    records.value = res.data
  } catch (cause) {
    if (current(version, session)) recordsError.value = errorMessage(cause, '复审记录暂时无法读取。')
  } finally {
    if (current(version, session)) recordsLoading.value = false
  }
}
async function loadSuggestion() {
  if (!question.value || suggestionLoading.value || submitting.value) return
  const version = generation,
    session = getAuthSessionVersion(),
    id = question.value.id
  suggestionLoading.value = true
  suggestionError.value = ''
  try {
    const res = await getReviewSuggestion(id, { errorDisplay: 'inline' })
    if (!current(version, session)) return
    if (!res.data) throw new Error('AI 复审建议暂时无法读取。')
    suggestion.value = res.data
  } catch (cause) {
    if (current(version, session)) suggestionError.value = errorMessage(cause, 'AI 复审建议暂时无法读取。')
  } finally {
    if (current(version, session)) suggestionLoading.value = false
  }
}
function applySuggestion() {
  if (!suggestion.value || submitting.value) return
  form.action = suggestion.value.recommendation
  if (suggestion.value.recommendation === 'REVISE') {
    form.newContent = suggestion.value.suggestedContent || question.value?.content || ''
    form.newDifficulty = suggestion.value.suggestedDifficulty || question.value?.difficulty || 3
  }
  form.comment = suggestion.value.summary
  submitError.value = ''
}
async function submitReview() {
  if (!question.value || submitting.value) return
  submitError.value = ''
  if (!form.comment.trim()) {
    submitError.value = '请输入复审意见。'
    return
  }
  if (form.action === 'REVISE' && !form.newContent.trim()) {
    submitError.value = '请输入修订后的题干。'
    return
  }
  const version = generation,
    session = getAuthSessionVersion(),
    id = question.value.id
  submitting.value = true
  try {
    await performReReview(
      id,
      {
        action: form.action,
        newContent: form.action === 'REVISE' ? form.newContent : undefined,
        newDifficulty: form.action === 'REVISE' ? form.newDifficulty : undefined,
        comment: form.comment,
      },
      { errorDisplay: 'inline' },
    )
    if (!current(version, session)) return
    ElMessage.success('复审完成')
    visible.value = false
    emit('reviewed')
  } catch (cause) {
    if (current(version, session)) submitError.value = errorMessage(cause, '复审暂时无法提交，已保留填写内容。')
  } finally {
    if (current(version, session)) submitting.value = false
  }
}
defineExpose({ open })
</script>

<style scoped>
.review-question-summary {
  margin-bottom: 16px;
}

.review-question-content {
  max-height: 120px;
  overflow-y: auto;
  white-space: pre-wrap;
}

.review-suggestion-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.review-suggestion-meta {
  color: var(--lp-text-muted);
  font-size: 13px;
}

.review-suggestion-panel {
  margin-bottom: 16px;
}

.review-suggestion-content {
  color: var(--lp-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.review-suggestion-content p {
  margin: 6px 0;
}

.review-suggestion-content ul {
  margin: 6px 0 8px;
  padding-left: 18px;
}

.review-records {
  margin-top: 12px;
}

.review-records h3 {
  margin-bottom: 8px;
}

.review-record-heading {
  display: flex;
  align-items: center;
  gap: 8px;
}

.review-record-heading span {
  color: var(--lp-text-muted);
  font-size: 12px;
}

.review-record-comment {
  margin-top: 4px;
  font-size: 13px;
}

.review-inline-error {
  padding: var(--lp-space-3);
  border-left: 3px solid var(--lp-danger);
  background: var(--lp-danger-soft);
  color: var(--lp-danger);
  font-size: var(--lp-text-sm);
  overflow-wrap: anywhere;
}
.review-empty {
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}
.review-question-content,
.review-record-comment {
  overflow-wrap: anywhere;
}
@media (max-width: 720px) {
  .review-suggestion-actions {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
