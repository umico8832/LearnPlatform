<template>
  <el-card v-if="reviewing && currentCard" shadow="never" class="review-session">
    <template #header>
      <div class="card-header">
        <span>复习进度: {{ currentIndex + 1 }} / {{ sessionCards.length }}</span>
        <el-tag :type="reviewStatusTag(currentCard.statusLabel)" size="small">{{ currentCard.statusLabel }}</el-tag>
      </div>
    </template>

    <LpProgress :percent="Math.round((currentIndex / sessionCards.length) * 100)" />

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
        :disabled="!userAnswer.trim() || answerSubmitted || submitting"
        :loading="submitting"
        @click="submitCurrentAnswer"
      >
        提交答案
      </el-button>
      <el-button :disabled="answerSubmitted || submitting" @click="nextCard">跳过</el-button>
      <el-button type="danger" plain @click="stop">结束复习</el-button>
    </div>

    <el-alert
      v-if="answerSubmitted"
      :title="lastCorrect === null ? '作答已记录' : lastCorrect ? '回答正确！' : '回答错误，请继续尝试'"
      :type="lastCorrect === null ? 'info' : lastCorrect ? 'success' : 'warning'"
      :description="lastCorrect === null ? '本题尚未判分' : `下次复习: ${lastResult?.nextReviewDate || '待安排'}`"
      show-icon
      :closable="false"
      class="result-alert"
    />

    <AnswerRewardFeedback v-if="answerSubmitted && lastResult?.reward" :reward="lastResult.reward" />
    <div v-if="answerSubmitted" class="next-action">
      <el-button type="primary" @click="nextCard">
        {{ currentIndex < sessionCards.length - 1 ? '下一题' : '完成复习' }}
      </el-button>
    </div>
  </el-card>

  <div v-if="reviewComplete" class="complete-card">
    <GamificationPracticeSummary :summary="sessionSummary" @continue="finish" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { submitReview } from '@/api/review'
import type { ReviewScheduleVO } from '@/api/review'
import { errorMessage } from '@/utils/errors'
import { useGamificationStore } from '@/stores/gamification'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'
import GamificationPracticeSummary from '@/components/gamification/GamificationPracticeSummary.vue'
import AnswerRewardFeedback from '@/components/gamification/AnswerRewardFeedback.vue'
import type { GamificationAchievement } from '@/components/gamification/types'
import { reviewStatusTag } from './reviewSessionPresentation'

const props = defineProps<{ cards: ReviewScheduleVO[] }>()
const emit = defineEmits<{ reviewed: [] }>()

const reviewing = ref(false)
const currentIndex = ref(0)
const userAnswer = ref('')
const answerSubmitted = ref(false)
const submitting = ref(false)
const lastResult = ref<ReviewScheduleVO | null>(null)
const lastCorrect = ref<boolean | null>(null)
const reviewedCount = ref(0)
const correctCount = ref(0)
const gradedCount = ref(0)
const reviewComplete = ref(false)

const sessionCards = ref<ReviewScheduleVO[]>([])
const currentCard = computed(() => sessionCards.value[currentIndex.value] || null)
const gamification = useGamificationStore()
const sessionXp = ref(0)
const sessionAchievements = ref<GamificationAchievement[]>([])
let generation = 0
let alive = true
const sessionSummary = computed(() => ({
  answeredCount: reviewedCount.value,
  correctRate: gradedCount.value ? (correctCount.value / gradedCount.value) * 100 : null,
  xpGained: sessionXp.value,
  achievements: sessionAchievements.value,
  kicker: '本组复习完成',
  rateLabel: '已判分正确率',
}))
const unsubscribeSession = onAuthSessionChange(() => {
  generation++
  reviewing.value = false
  reviewComplete.value = false
  sessionCards.value = []
  lastResult.value = null
})
onUnmounted(() => {
  alive = false
  generation++
  unsubscribeSession()
})

function resetAnswer() {
  userAnswer.value = ''
  answerSubmitted.value = false
  lastResult.value = null
  lastCorrect.value = null
}

function start() {
  generation++
  submitting.value = false
  sessionCards.value = [...props.cards]
  sessionXp.value = 0
  sessionAchievements.value = []
  reviewing.value = true
  reviewComplete.value = false
  currentIndex.value = 0
  reviewedCount.value = 0
  correctCount.value = 0
  gradedCount.value = 0
  resetAnswer()
}

async function submitCurrentAnswer() {
  if (!currentCard.value || !userAnswer.value.trim() || submitting.value || answerSubmitted.value || !reviewing.value)
    return
  const card = currentCard.value
  const requestGeneration = generation
  const session = getAuthSessionVersion()
  submitting.value = true
  try {
    const { data } = await submitReview({
      questionId: card.questionId,
      userAnswer: userAnswer.value.trim(),
    })
    if (!alive || requestGeneration !== generation || session !== getAuthSessionVersion()) return
    lastResult.value = data
    lastCorrect.value = data.correct ?? null
    if (gamification.acceptReward(data.reward, session) && data.reward) {
      sessionXp.value += data.reward.awardedXp
      for (const item of data.reward.newAchievements) {
        if (!sessionAchievements.value.some((achievement) => achievement.id === item.code))
          sessionAchievements.value.push({ id: item.code, title: item.name, description: item.description })
      }
    }
    answerSubmitted.value = true
    reviewedCount.value++
    if (lastCorrect.value !== null) {
      gradedCount.value++
      if (lastCorrect.value) correctCount.value++
    }
    emit('reviewed')
  } catch (error) {
    if (alive && requestGeneration === generation && session === getAuthSessionVersion())
      ElMessage.error(errorMessage(error, '提交失败'))
  } finally {
    if (alive && requestGeneration === generation && session === getAuthSessionVersion()) submitting.value = false
  }
}

function nextCard() {
  if (submitting.value) return
  generation++
  if (currentIndex.value < sessionCards.value.length - 1) {
    currentIndex.value++
    resetAnswer()
    return
  }
  reviewing.value = false
  reviewComplete.value = true
}

function stop() {
  generation++
  submitting.value = false
  reviewing.value = false
  ElMessage.info(`已结束复习，本次复习 ${reviewedCount.value} 题`)
}

function finish() {
  generation++
  reviewComplete.value = false
  reviewing.value = false
}

defineExpose({ start, reviewing })
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

.review-reward {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  margin-top: var(--lp-space-4);
  color: var(--lp-reward-combo);
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
