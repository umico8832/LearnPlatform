<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref } from 'vue'
import { Check, ChevronRight, RotateCcw, Square } from '@lucide/vue'
import type { ReviewScheduleVO } from '@/api/review'
import { submitReview } from '@/api/review'
import { getQuestionById, type QuestionVO } from '@/api/question'
import { LtButton, LtCard } from '@/components/living'
import QuestionProse from '@/views/question/QuestionProse.vue'
import { errorMessage } from '@/utils/errors'
import { useGamificationStore } from '@/stores/gamification'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'
import PracticeRewardFeedback from './PracticeRewardFeedback.vue'
import PracticeSummary from './PracticeSummary.vue'
import { practiceQuestionTypeLabel } from './practiceSessionPresentation'
import type { GamificationAchievement } from '@/components/gamification/types'

const props = defineProps<{ cards: ReviewScheduleVO[] }>()
const emit = defineEmits<{ reviewed: [] }>()
const reviewing = ref(false)
const index = ref(0)
const question = ref<QuestionVO | null>(null)
const loading = ref(false)
const failed = ref(false)
const answer = ref('')
const submitted = ref(false)
const submitting = ref(false)
const error = ref('')
type ReviewResult = ReviewScheduleVO & { correct?: boolean | null }
const result = ref<ReviewResult | null>(null)
const quality = ref<2 | 5 | undefined>(undefined)
const sessionCards = ref<ReviewScheduleVO[]>([])
const reviewedCount = ref(0)
const correctCount = ref(0)
const gradedCount = ref(0)
const pendingCount = ref(0)
const complete = ref(false)
const endedNotice = ref('')
const questionRoot = ref<HTMLElement | null>(null)
const nextButton = ref<InstanceType<typeof LtButton> | null>(null)
const sessionXp = ref(0)
const achievements = ref<GamificationAchievement[]>([])
const gamification = useGamificationStore()
let generation = 0
let alive = true
const current = computed(() => sessionCards.value[index.value] ?? null)
const multiple = computed<string[]>({
  get: () => (answer.value ? answer.value.split(',') : []),
  set: (value) => {
    answer.value = [...new Set(value)].sort().join(',')
  },
})
const canSubmit = computed(() => !!question.value && !!answer.value.trim() && !submitted.value && !submitting.value)
const summary = computed(() => ({
  answeredCount: reviewedCount.value,
  pendingCount: pendingCount.value,
  correctRate: gradedCount.value ? (correctCount.value / gradedCount.value) * 100 : null,
  xpGained: sessionXp.value,
  achievements: achievements.value,
  kicker: '本组复习完成',
  rateLabel: '已判分正确率',
}))

async function loadQuestion() {
  if (!current.value) return
  const requestGeneration = generation
  const session = getAuthSessionVersion()
  loading.value = true
  failed.value = false
  error.value = ''
  try {
    const response = await getQuestionById(current.value.questionId, { errorDisplay: 'inline' })
    if (response.code !== 0 || !response.data || response.data.id !== current.value?.questionId)
      throw new Error('missing')
    if (alive && requestGeneration === generation && session === getAuthSessionVersion()) {
      question.value = response.data
      await nextTick()
      if (alive && requestGeneration === generation && session === getAuthSessionVersion()) {
        questionRoot.value?.scrollIntoView?.({ block: 'nearest' })
        questionRoot.value?.focus({ preventScroll: true })
      }
    }
  } catch {
    if (alive && requestGeneration === generation && session === getAuthSessionVersion()) failed.value = true
  } finally {
    if (alive && requestGeneration === generation && session === getAuthSessionVersion()) loading.value = false
  }
}
function reset() {
  answer.value = ''
  submitted.value = false
  submitting.value = false
  error.value = ''
  result.value = null
  quality.value = undefined
  question.value = null
}
function start() {
  generation++
  index.value = 0
  sessionCards.value = [...props.cards]
  reviewing.value = true
  endedNotice.value = ''
  complete.value = false
  reviewedCount.value = 0
  correctCount.value = 0
  gradedCount.value = 0
  pendingCount.value = 0
  sessionXp.value = 0
  achievements.value = []
  reset()
  void loadQuestion()
}
async function submit() {
  if (!current.value || !canSubmit.value) return
  const requestGeneration = generation
  const session = getAuthSessionVersion()
  submitting.value = true
  error.value = ''
  try {
    const response = await submitReview(
      {
        questionId: current.value.questionId,
        userAnswer: answer.value.trim(),
        ...(quality.value === undefined ? {} : { selfAssessedQuality: quality.value }),
      },
      { errorDisplay: 'inline' },
    )
    if (!alive || requestGeneration !== generation || session !== getAuthSessionVersion()) return
    result.value = response.data
    submitted.value = true
    reviewedCount.value++
    if (response.data.correct === null || response.data.correct === undefined) pendingCount.value++
    else {
      gradedCount.value++
      if (response.data.correct) correctCount.value++
    }
    if (response.data.reward && gamification.acceptReward(response.data.reward, session)) {
      sessionXp.value += response.data.reward.awardedXp
      for (const item of response.data.reward.newAchievements) {
        if (!achievements.value.some((achievement) => achievement.id === item.code))
          achievements.value.push({ id: item.code, title: item.name, description: item.description })
      }
    }
    emit('reviewed')
    await nextTick()
    if (alive && requestGeneration === generation && session === getAuthSessionVersion()) nextButton.value?.$el?.focus()
  } catch (cause) {
    if (alive && requestGeneration === generation && session === getAuthSessionVersion())
      error.value = errorMessage(cause, '提交失败，请重试')
  } finally {
    if (alive && requestGeneration === generation && session === getAuthSessionVersion()) submitting.value = false
  }
}
function next() {
  if (submitting.value) return
  generation++
  if (index.value < sessionCards.value.length - 1) {
    index.value += 1
    reset()
    void loadQuestion()
  } else {
    reviewing.value = false
    complete.value = true
  }
}
function stop() {
  generation++
  reviewing.value = false
  submitting.value = false
  endedNotice.value = `已结束复习，本次复习 ${reviewedCount.value} 题`
}
function finish() {
  generation++
  complete.value = false
  reviewing.value = false
}
const off = onAuthSessionChange(() => {
  generation++
  reviewing.value = false
  complete.value = false
  question.value = null
  submitting.value = false
  result.value = null
})
onUnmounted(() => {
  alive = false
  generation++
  off()
})
defineExpose({ start, reviewing })
</script>
<template>
  <p v-if="endedNotice" role="status">{{ endedNotice }}</p>
  <LtCard v-if="reviewing && current" as="section" role="region" radius="xl" class="session" aria-label="复习作答">
    <header class="session-head">
      <div>
        <p class="eyebrow">复习进度 {{ index + 1 }} / {{ sessionCards.length }}</p>
        <h2>{{ current.statusLabel }}</h2>
      </div>
      <span class="due">{{ current.overdue ? `逾期 ${current.overdueDays} 天` : '今日到期' }}</span>
    </header>
    <p class="metadata">
      {{ current.courseName || '未知课程' }} ·
      {{ practiceQuestionTypeLabel(question?.questionType ?? current.questionType) }} · 间隔
      {{ current.intervalDays }} 天
    </p>
    <progress :value="index" :max="sessionCards.length" aria-label="复习进度" />
    <div v-if="loading" class="state" aria-busy="true">
      <span class="skeleton" /><span class="skeleton short" /><span class="sr-only">正在加载题目</span>
    </div>
    <div v-else-if="failed" class="state">
      <p>题目暂时无法加载，暂不能提交作答。</p>
      <LtButton @click="loadQuestion"
        ><template #icon><RotateCcw aria-hidden="true" /></template>重新加载</LtButton
      >
    </div>
    <template v-else-if="question"
      ><div ref="questionRoot" tabindex="-1"><QuestionProse class="question" :content="question.content" /></div>
      <fieldset :disabled="submitted || submitting">
        <legend>作答</legend>
        <div v-if="question.questionType === 'SINGLE_CHOICE'" class="options">
          <label v-for="option in question.options" :key="option.id"
            ><input v-model="answer" type="radio" name="review-answer" :value="option.optionLabel" /><span
              >{{ option.optionLabel }}. {{ option.content }}</span
            ></label
          >
        </div>
        <div v-else-if="question.questionType === 'MULTIPLE_CHOICE'" class="options">
          <label v-for="option in question.options" :key="option.id"
            ><input v-model="multiple" type="checkbox" :value="option.optionLabel" /><span
              >{{ option.optionLabel }}. {{ option.content }}</span
            ></label
          >
        </div>
        <div v-else-if="question.questionType === 'TRUE_FALSE'" class="options">
          <label><input v-model="answer" type="radio" name="review-answer" value="TRUE" /><span>正确</span></label
          ><label><input v-model="answer" type="radio" name="review-answer" value="FALSE" /><span>错误</span></label>
        </div>
        <textarea v-else v-model="answer" aria-label="复习答案" placeholder="输入你的答案" />
      </fieldset>
      <div v-if="!submitted" class="rating" aria-label="自评难度">
        <span>自评（可选）</span
        ><label
          ><input
            v-model="quality"
            type="radio"
            name="review-quality"
            :disabled="submitting"
            :value="undefined"
          />按作答安排</label
        ><label
          ><input v-model="quality" type="radio" name="review-quality" :disabled="submitting" :value="2" />困难</label
        ><label
          ><input v-model="quality" type="radio" name="review-quality" :disabled="submitting" :value="5" />容易</label
        >
      </div>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <div v-if="submitted" class="result" aria-live="polite">
        <p>
          {{
            result?.correct === null || result?.correct === undefined
              ? '作答已记录，等待服务端判分。'
              : result.correct
                ? '服务端判分：回答正确'
                : '服务端判分：回答错误'
          }}
        </p>
        <p>
          {{
            result?.correct === null || result?.correct === undefined
              ? '本题尚未判分，不计入正确率。'
              : `下次复习：${result.nextReviewDate || '待安排'}`
          }}
        </p>
      </div>
      <PracticeRewardFeedback v-if="submitted && result?.reward" :reward="result.reward" />
      <div class="actions">
        <LtButton v-if="!submitted" variant="primary" :disabled="!canSubmit" @click="submit"
          ><template #icon><Check aria-hidden="true" /></template>{{ submitting ? '正在提交' : '提交答案' }}</LtButton
        ><LtButton ref="nextButton" :disabled="submitting" @click="next"
          >{{ submitted ? (index < sessionCards.length - 1 ? '下一题' : '完成复习') : '跳过'
          }}<template #trailing><ChevronRight aria-hidden="true" /></template></LtButton
        ><LtButton variant="quiet" @click="stop"
          ><template #icon><Square aria-hidden="true" /></template>结束复习</LtButton
        >
      </div>
    </template>
    <div v-if="loading || failed" class="actions">
      <LtButton @click="next">跳过</LtButton><LtButton variant="quiet" @click="stop">结束复习</LtButton>
    </div>
  </LtCard>
  <PracticeSummary v-if="complete" role="region" aria-label="本组复习完成" :summary="summary" @continue="finish" />
</template>
<style scoped>
.session {
  margin-top: var(--lt-space-6);
}
.session-head,
.actions,
.rating {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lt-space-3);
}
.eyebrow,
.due,
.rating {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
h2,
p {
  margin: 0;
}
.session-head h2 {
  margin-top: var(--lt-space-1);
  font-size: var(--lt-text-title3);
}
.due {
  padding: var(--lt-space-1) var(--lt-space-2);
  border-radius: var(--lt-radius-pill);
  background: var(--lt-orange-soft);
  color: var(--lt-orange-text);
}
.question {
  margin-top: var(--lt-space-5);
}
fieldset {
  display: grid;
  gap: var(--lt-space-3);
  margin: var(--lt-space-5) 0 0;
  padding: 0;
  border: 0;
}
legend {
  padding: 0;
  font-size: var(--lt-text-footnote);
  font-weight: 600;
}
.options {
  display: grid;
  gap: var(--lt-space-2);
}
.options label {
  display: flex;
  align-items: flex-start;
  gap: var(--lt-space-3);
  padding: var(--lt-space-3) var(--lt-space-4);
  border: thin solid var(--lt-separator);
  border-radius: var(--lt-radius-md);
  background: var(--lt-fill-3);
  line-height: var(--lt-leading-reading);
}
input {
  accent-color: var(--lt-accent);
}
textarea {
  min-height: calc(var(--lt-space-10) + var(--lt-space-7));
  padding: var(--lt-space-3);
  border: thin solid var(--lt-separator);
  border-radius: var(--lt-radius-md);
  background: var(--lt-fill-3);
  color: var(--lt-label);
  font: inherit;
  resize: vertical;
}
.rating {
  justify-content: flex-start;
  margin-top: var(--lt-space-4);
}
.rating label {
  display: inline-flex;
  align-items: center;
  gap: var(--lt-space-1);
}
.error {
  margin-top: var(--lt-space-3);
  color: var(--lt-red-text);
  font-size: var(--lt-text-footnote);
}
.result {
  display: grid;
  gap: var(--lt-space-1);
  margin-top: var(--lt-space-4);
  padding: var(--lt-space-3);
  border-radius: var(--lt-radius-md);
  background: var(--lt-green-soft);
  color: var(--lt-green-text);
}
.actions {
  justify-content: flex-start;
  margin-top: var(--lt-space-5);
}
.state {
  display: grid;
  gap: var(--lt-space-3);
  margin-top: var(--lt-space-5);
  color: var(--lt-label-2);
}
.skeleton {
  display: block;
  height: var(--lt-space-4);
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
}
.short {
  width: 50%;
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
.metadata {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
progress {
  width: 100%;
  height: var(--lt-space-1);
  accent-color: var(--lt-accent-fill);
}
</style>
