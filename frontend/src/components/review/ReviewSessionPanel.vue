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
        <el-tag size="small">{{ questionTypeLabel(question?.questionType ?? currentCard.questionType) }}</el-tag>
        <el-tag size="small" type="info">{{ currentCard.courseName || '未知课程' }}</el-tag>
        <el-tag size="small" :type="currentCard.overdue ? 'danger' : 'success'">
          {{ currentCard.overdue ? `逾期 ${currentCard.overdueDays} 天` : '今日到期' }}
        </el-tag>
        <el-tag size="small">间隔 {{ currentCard.intervalDays }} 天</el-tag>
      </div>
      <div v-if="questionLoading" class="question-loading" role="status">正在加载题目…</div>
      <div v-else-if="questionFailed" class="question-load-error" role="alert">
        <p>题目暂时无法加载，暂不能提交作答。</p>
        <el-button @click="loadQuestion">重新加载题目</el-button>
      </div>
      <div v-else-if="question" ref="questionContent" class="question-content" tabindex="-1">
        {{ question.content }}
      </div>
    </div>

    <fieldset
      v-if="question"
      class="answer-box"
      :disabled="answerSubmitted || submitting || questionLoading || questionFailed"
    >
      <legend>作答</legend>
      <div v-if="question.questionType === 'SINGLE_CHOICE'" class="answer-options">
        <label v-for="option in question.options" :key="option.id" class="answer-option">
          <input v-model="userAnswer" type="radio" name="review-answer" :value="option.optionLabel" />
          <span>{{ option.optionLabel }}. {{ option.content }}</span>
        </label>
      </div>
      <div v-else-if="question.questionType === 'MULTIPLE_CHOICE'" class="answer-options">
        <label v-for="option in question.options" :key="option.id" class="answer-option">
          <input v-model="multipleAnswers" type="checkbox" :value="option.optionLabel" />
          <span>{{ option.optionLabel }}. {{ option.content }}</span>
        </label>
      </div>
      <div v-else-if="question.questionType === 'TRUE_FALSE'" class="answer-options">
        <label class="answer-option"
          ><input v-model="userAnswer" type="radio" name="review-answer" value="TRUE" />正确</label
        >
        <label class="answer-option"
          ><input v-model="userAnswer" type="radio" name="review-answer" value="FALSE" />错误</label
        >
      </div>
      <el-input
        v-else
        v-model="userAnswer"
        type="textarea"
        :rows="3"
        aria-label="复习答案"
        placeholder="输入你的答案..."
      />
    </fieldset>

    <div class="session-actions">
      <el-button
        type="primary"
        :disabled="!canSubmit || !!submissionError"
        :loading="submitting"
        @click="submitCurrentAnswer"
      >
        提交答案
      </el-button>
      <el-button :disabled="answerSubmitted || submitting" @click="nextCard">跳过</el-button>
      <el-button type="danger" plain @click="stop">结束复习</el-button>
    </div>
    <p v-if="submissionError" class="submission-error" role="alert">
      {{ submissionError }}
      <button type="button" :disabled="submitting" @click="submitCurrentAnswer">重试</button>
    </p>

    <section v-if="answerSubmitted" class="review-result" aria-live="polite">
      <p class="review-result__title">
        {{
          lastCorrect === null
            ? '作答已记录，等待服务端判分'
            : lastCorrect
              ? '服务端判分：回答正确'
              : '服务端判分：回答错误'
        }}
      </p>
      <p class="review-result__detail">
        {{
          lastCorrect === null ? '本题尚未判分，不计入正确率。' : `下次复习：${lastResult?.nextReviewDate || '待安排'}`
        }}
      </p>
    </section>

    <AnswerRewardFeedback v-if="answerSubmitted && lastResult?.reward" :reward="lastResult.reward" />
    <div v-if="answerSubmitted" class="next-action">
      <el-button ref="nextActionButton" type="primary" @click="nextCard">
        {{ currentIndex < sessionCards.length - 1 ? '下一题' : '完成复习' }}
      </el-button>
    </div>
  </el-card>

  <div v-if="reviewComplete" class="complete-card">
    <GamificationPracticeSummary :summary="sessionSummary" @continue="finish" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
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
import { useReviewQuestion } from './useReviewQuestion'

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
const pendingCount = ref(0)
const reviewComplete = ref(false)
const submissionError = ref('')
const nextActionButton = ref<{ $el?: HTMLButtonElement }>()
const questionContent = ref<HTMLElement>()

const sessionCards = ref<ReviewScheduleVO[]>([])
const currentCard = computed(() => sessionCards.value[currentIndex.value] || null)
const currentQuestionId = computed(() => (reviewing.value ? (currentCard.value?.questionId ?? null) : null))
const {
  question,
  loading: questionLoading,
  failed: questionFailed,
  load: loadQuestion,
} = useReviewQuestion(currentQuestionId)
const gamification = useGamificationStore()
const sessionXp = ref(0)
const sessionAchievements = ref<GamificationAchievement[]>([])
let generation = 0
let alive = true
const sessionSummary = computed(() => ({
  answeredCount: reviewedCount.value,
  pendingCount: pendingCount.value,
  correctRate: gradedCount.value ? (correctCount.value / gradedCount.value) * 100 : null,
  xpGained: sessionXp.value,
  achievements: sessionAchievements.value,
  kicker: '本组复习完成',
  rateLabel: '已判分正确率',
}))
const multipleAnswers = computed<string[]>({
  get: () => (userAnswer.value ? userAnswer.value.split(',') : []),
  set: (value) => {
    userAnswer.value = [...new Set(value)].sort().join(',')
  },
})
const canSubmit = computed(
  () =>
    !!question.value &&
    !questionLoading.value &&
    !questionFailed.value &&
    !!userAnswer.value.trim() &&
    !answerSubmitted.value &&
    !submitting.value,
)
let focusQuestionWhenReady = false
const unsubscribeSession = onAuthSessionChange(() => {
  generation++
  reviewing.value = false
  reviewComplete.value = false
  sessionCards.value = []
  lastResult.value = null
  submissionError.value = ''
  submitting.value = false
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
  submissionError.value = ''
}

function questionTypeLabel(type: string) {
  return (
    {
      SINGLE_CHOICE: '单选题',
      MULTIPLE_CHOICE: '多选题',
      TRUE_FALSE: '判断题',
      FILL_BLANK: '填空题',
      SHORT_ANSWER: '简答题',
    }[type] ?? type
  )
}

watch(question, async (value) => {
  if (!value || !focusQuestionWhenReady) return
  await nextTick()
  questionContent.value?.scrollIntoView({ block: 'start', behavior: 'auto' })
  questionContent.value?.focus({ preventScroll: true })
  focusQuestionWhenReady = false
})

function start() {
  generation++
  submitting.value = false
  focusQuestionWhenReady = true
  sessionCards.value = [...props.cards]
  sessionXp.value = 0
  sessionAchievements.value = []
  reviewing.value = true
  reviewComplete.value = false
  currentIndex.value = 0
  reviewedCount.value = 0
  correctCount.value = 0
  gradedCount.value = 0
  pendingCount.value = 0
  resetAnswer()
}

async function submitCurrentAnswer() {
  if (!currentCard.value || !userAnswer.value.trim() || submitting.value || answerSubmitted.value || !reviewing.value)
    return
  const card = currentCard.value
  const requestGeneration = generation
  const session = getAuthSessionVersion()
  submissionError.value = ''
  submitting.value = true
  try {
    const { data } = await submitReview(
      {
        questionId: card.questionId,
        userAnswer: userAnswer.value.trim(),
      },
      { errorDisplay: 'inline' },
    )
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
    } else pendingCount.value++
    emit('reviewed')
    await nextTick()
    nextActionButton.value?.$el?.focus()
  } catch (error) {
    if (alive && requestGeneration === generation && session === getAuthSessionVersion())
      submissionError.value = errorMessage(error, '提交失败，请重试')
  } finally {
    if (alive && requestGeneration === generation && session === getAuthSessionVersion()) submitting.value = false
  }
}

async function nextCard() {
  if (submitting.value) return
  generation++
  if (currentIndex.value < sessionCards.value.length - 1) {
    currentIndex.value++
    resetAnswer()
    focusQuestionWhenReady = true
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

.question-loading,
.question-load-error {
  margin: 0;
  padding: var(--lp-space-4);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
  background: var(--lp-surface-soft);
  color: var(--lp-text-secondary);
}
.question-load-error {
  display: grid;
  gap: var(--lp-space-3);
  border-left: 2px solid var(--lp-danger);
}
.question-load-error p {
  margin: 0;
}
.answer-box,
.review-result,
.next-action {
  margin-top: var(--lp-space-4);
}

.answer-box {
  display: grid;
  gap: var(--lp-space-3);
  margin-inline: 0;
  padding: 0;
  border: 0;
}
.answer-box legend {
  padding: 0;
  color: var(--lp-text);
  font-size: var(--lp-text-sm);
  font-weight: var(--lp-weight-semibold);
}
.answer-options {
  display: grid;
  gap: var(--lp-space-2);
}
.answer-option {
  display: flex;
  align-items: flex-start;
  gap: var(--lp-space-3);
  padding: var(--lp-space-3) var(--lp-space-4);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
  background: var(--lp-surface);
  color: var(--lp-text);
  cursor: pointer;
  line-height: var(--lp-leading-body);
}
.answer-option input {
  margin-top: 0.2em;
  accent-color: var(--lp-primary);
}
.answer-box:disabled .answer-option {
  cursor: not-allowed;
  opacity: 0.7;
}

.review-result {
  display: grid;
  gap: var(--lp-space-1);
  padding: var(--lp-space-3) var(--lp-space-4);
  background: var(--lp-surface-soft);
  border-left: 2px solid var(--lp-primary);
  border-radius: var(--lp-radius-sm);
}

.review-result__title,
.review-result__detail {
  margin: 0;
}

.review-result__title {
  font-weight: var(--lp-weight-semibold);
  color: var(--lp-text);
}

.review-result__detail {
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
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
.submission-error {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: var(--lp-space-2);
  margin: var(--lp-space-3) 0 0;
  color: var(--lp-danger);
  font-size: var(--lp-text-sm);
  line-height: var(--lp-leading-body);
}
.submission-error button {
  padding: 0;
  border: 0;
  border-radius: var(--lp-radius-sm);
  color: var(--lp-primary);
  background: transparent;
  font: inherit;
  text-decoration: underline;
  cursor: pointer;
}
.submission-error button:focus-visible {
  outline: var(--lp-focus-width) solid var(--lp-focus-ring);
  outline-offset: var(--lp-focus-offset);
}

.complete-card {
  display: flex;
  justify-content: flex-start;
}

@media (max-width: 640px) {
  .card-header {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
