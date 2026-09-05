<template>
  <el-card v-if="reviewing && currentCard" shadow="never" class="review-session">
    <template #header>
      <div class="card-header">
        <span>复习进度: {{ currentIndex + 1 }} / {{ cards.length }}</span>
        <el-tag :type="reviewStatusTag(currentCard.statusLabel)" size="small">{{ currentCard.statusLabel }}</el-tag>
      </div>
    </template>

    <LpProgress :percent="Math.round((currentIndex / cards.length) * 100)" />

    <div class="question-info">
      <div class="question-tags">
        <el-tag size="small">{{ currentCard.questionType }}</el-tag>
        <el-tag size="small" type="info">{{ currentCard.courseName || '未知课程' }}</el-tag>
        <el-tag size="small" :type="currentCard.overdue ? 'danger' : 'success'">
          {{ currentCard.overdue ? `逾期 ${currentCard.overdueDays} 天` : '今日到期' }}
        </el-tag>
        <el-tag size="small">间隔 {{ currentCard.intervalDays }} 天</el-tag>
        <el-tag size="small">EF {{ currentCard.easeFactor?.toFixed(2) }}</el-tag>
      </div>
      <div class="question-content">{{ currentCard.questionContent }}</div>
    </div>

    <div class="answer-box">
      <el-input
        v-model="userAnswer"
        type="textarea"
        :rows="3"
        placeholder="输入你的答案..."
        :disabled="answerSubmitted"
      />
    </div>

    <div class="session-actions">
      <el-button
        type="primary"
        :disabled="!userAnswer.trim() || answerSubmitted"
        :loading="submitting"
        @click="submitCurrentAnswer"
      >
        提交答案
      </el-button>
      <el-button :disabled="answerSubmitted" @click="nextCard">跳过</el-button>
      <el-button type="danger" plain @click="stop">结束复习</el-button>
    </div>

    <el-alert
      v-if="answerSubmitted"
      :title="lastCorrect ? '回答正确！' : '回答错误'"
      :type="lastCorrect ? 'success' : 'error'"
      :description="
        lastCorrect
          ? `下次复习: ${currentCard.intervalDays} 天后 | 新间隔: ${lastResult?.intervalDays} 天`
          : '间隔已重置为 1 天，请继续加油！'
      "
      show-icon
      :closable="false"
      class="result-alert"
    />

    <div v-if="answerSubmitted" class="next-action">
      <el-button type="primary" @click="nextCard">
        {{ currentIndex < cards.length - 1 ? '下一题' : '完成复习' }}
      </el-button>
    </div>
  </el-card>

  <div v-if="reviewComplete" class="complete-card">
    <div class="complete-icon" aria-hidden="true">✓</div>
    <h3>今日复习完成！</h3>
    <p>共复习 {{ reviewedCount }} 题，正确 {{ correctCount }} 题</p>
    <el-button type="primary" @click="finish">返回</el-button>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { submitReview } from '@/api/review'
import type { ReviewScheduleVO } from '@/api/review'
import { errorMessage } from '@/utils/errors'
import { reviewStatusTag } from './reviewSessionPresentation'

const props = defineProps<{ cards: ReviewScheduleVO[] }>()
const emit = defineEmits<{ reviewed: [] }>()

const reviewing = ref(false)
const currentIndex = ref(0)
const userAnswer = ref('')
const answerSubmitted = ref(false)
const submitting = ref(false)
const lastResult = ref<ReviewScheduleVO | null>(null)
const lastCorrect = ref(false)
const reviewedCount = ref(0)
const correctCount = ref(0)
const reviewComplete = ref(false)

const currentCard = computed(() => props.cards[currentIndex.value] || null)

function resetAnswer() {
  userAnswer.value = ''
  answerSubmitted.value = false
  lastResult.value = null
  lastCorrect.value = false
}

function start() {
  reviewing.value = true
  reviewComplete.value = false
  currentIndex.value = 0
  reviewedCount.value = 0
  correctCount.value = 0
  resetAnswer()
}

async function submitCurrentAnswer() {
  if (!currentCard.value || !userAnswer.value.trim()) return
  submitting.value = true
  try {
    const previousRepetitions = currentCard.value.repetitions || 0
    const { data } = await submitReview({
      questionId: currentCard.value.questionId,
      userAnswer: userAnswer.value.trim(),
    })
    lastResult.value = data
    lastCorrect.value = (data?.repetitions ?? 0) > previousRepetitions || (data?.intervalDays ?? 0) > 1
    answerSubmitted.value = true
    reviewedCount.value++
    if (lastCorrect.value) correctCount.value++
    emit('reviewed')
  } catch (error) {
    ElMessage.error(errorMessage(error, '提交失败'))
  } finally {
    submitting.value = false
  }
}

function nextCard() {
  if (currentIndex.value < props.cards.length - 1) {
    currentIndex.value++
    resetAnswer()
    return
  }
  reviewing.value = false
  reviewComplete.value = true
}

function stop() {
  reviewing.value = false
  ElMessage.info(`已结束复习，本次复习 ${reviewedCount.value} 题`)
}

function finish() {
  reviewComplete.value = false
  reviewing.value = false
}

defineExpose({ start })
</script>

<style scoped>
.review-session {
  border: var(--lp-border-hairline);
  border-left: 3px solid var(--lp-primary);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--lp-space-3);
}

.card-header span {
  font-weight: var(--lp-weight-semibold);
  color: var(--lp-text);
}

.question-info {
  margin-top: var(--lp-space-4);
}

.question-tags {
  display: flex;
  gap: var(--lp-space-2);
  margin-bottom: var(--lp-space-3);
  flex-wrap: wrap;
}

.question-content {
  font-size: var(--lp-text-lg);
  line-height: var(--lp-leading-relaxed);
  padding: var(--lp-space-4);
  background: var(--lp-surface-soft);
  border-radius: var(--lp-radius-md);
  white-space: pre-wrap;
  color: var(--lp-text);
}

.answer-box,
.result-alert,
.next-action {
  margin-top: var(--lp-space-4);
}

.session-actions {
  display: flex;
  gap: var(--lp-space-3);
  flex-wrap: wrap;
  margin-top: var(--lp-space-3);
}

.complete-card {
  text-align: center;
  padding: var(--lp-space-8) var(--lp-space-6);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}

.complete-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 60px;
  height: 60px;
  margin: 0 auto var(--lp-space-4);
  border-radius: var(--lp-radius-full);
  background: var(--lp-success-soft);
  color: var(--lp-success);
  font-size: var(--lp-text-4xl);
  font-weight: var(--lp-weight-bold);
}

.complete-card h3 {
  margin: 0 0 var(--lp-space-2);
  color: var(--lp-text);
  font-size: var(--lp-text-3xl);
}

.complete-card p {
  margin: 0 0 var(--lp-space-4);
  color: var(--lp-text-secondary);
}

@media (max-width: 640px) {
  .card-header {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
