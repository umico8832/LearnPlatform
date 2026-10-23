<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { ArrowRight, ChevronLeft } from '@lucide/vue'
import { submitAnswer, type PracticeQuestionVO, type PracticeResultVO } from '@/api/practice'
import { LtButton, LtCard, LtIconButton } from '@/components/living'
import { useLivingTheme } from '@/composables/useLivingTheme'
import type { GamificationAchievement } from '@/components/gamification/types'
import MarkdownRenderer from '@/views/question/QuestionProse.vue'
import { useGamificationStore } from '@/stores/gamification'
import { useUserStore } from '@/stores/user'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'
import { errorMessage } from '@/utils/errors'
import { clearPracticeSession, loadPracticeSession } from '@/utils/practiceSession'
import PracticeAnswerFeedback from './PracticeAnswerFeedback.vue'
import PracticeSummary from './PracticeSummary.vue'
import { practiceQuestionTypeLabel as getQuestionTypeLabel, practiceReturnRoute } from './practiceSessionPresentation'
import { usePracticeAnswer } from './usePracticeAnswer'

defineOptions({ name: 'PracticeSessionView' })
useLivingTheme({ dark: true })
const router = useRouter(),
  gamification = useGamificationStore()
let alive = true,
  submissionVersion = 0
const sessionLoading = ref(true),
  sessionLoadError = ref('')
const questions = ref<PracticeQuestionVO[]>([]),
  currentIndex = ref(0),
  submitting = ref(false),
  currentResult = ref<PracticeResultVO | null>(null),
  submissionError = ref(''),
  practiceMode = ref(''),
  startTime = ref(0),
  finished = ref(false),
  correctCount = ref(0),
  wrongCount = ref(0),
  pendingCount = ref(0),
  sessionXp = ref(0),
  sessionAchievements = ref<GamificationAchievement[]>([])
const questionHeadingRef = ref<HTMLElement | null>(null),
  nextActionRef = ref<HTMLElement | null>(null)
const currentQuestion = computed(() => questions.value[currentIndex.value] || null)
const answerLocked = computed(() => submitting.value || currentResult.value !== null)
const { userAnswer, multiAnswers, canSubmit, toggleMulti, answer, reset } = usePracticeAnswer(
  currentQuestion,
  answerLocked,
)
const isWrongPractice = computed(() => practiceMode.value === 'wrong_question')
const progressPercent = computed(() =>
  questions.value.length ? ((currentIndex.value + 1) / questions.value.length) * 100 : 0,
)
const sessionSummary = computed(() => {
  const graded = correctCount.value + wrongCount.value
  return {
    answeredCount: graded + pendingCount.value,
    pendingCount: pendingCount.value,
    correctRate: graded ? (correctCount.value / graded) * 100 : null,
    xpGained: sessionXp.value,
    achievements: sessionAchievements.value,
    rateLabel: graded ? '正确率' : '判分状态',
  }
})
function resetSession() {
  submissionVersion++
  questions.value = []
  currentResult.value = null
  submissionError.value = ''
  sessionXp.value = 0
  correctCount.value = 0
  wrongCount.value = 0
  pendingCount.value = 0
  finished.value = false
  submitting.value = false
  sessionAchievements.value = []
}
const unsubscribeSession = onAuthSessionChange(() => {
  resetSession()
  if (alive) void router.replace({ name: 'Practice' })
})
onBeforeRouteLeave(() => {
  clearPracticeSession()
  return true
})
onUnmounted(() => {
  alive = false
  unsubscribeSession()
})
onMounted(loadSession)
async function loadSession() {
  const user = useUserStore(),
    session = getAuthSessionVersion()
  sessionLoading.value = true
  sessionLoadError.value = ''
  try {
    if (!user.userInfo) await user.fetchUserInfo()
    if (!alive || session !== getAuthSessionVersion()) return
    const stored = loadPracticeSession(user.userInfo?.id)
    if (!stored) {
      void router.replace({ name: 'Practice' })
      return
    }
    questions.value = stored.questions
    practiceMode.value = stored.mode
    startTime.value = Date.now()
  } catch {
    if (alive && session === getAuthSessionVersion()) sessionLoadError.value = '练习暂时无法加载，请重试。'
  } finally {
    if (alive && session === getAuthSessionVersion()) sessionLoading.value = false
  }
}
async function handleSubmit() {
  if (!currentQuestion.value || !canSubmit.value || answerLocked.value || finished.value) return
  const questionId = currentQuestion.value.id,
    session = getAuthSessionVersion(),
    requestVersion = ++submissionVersion
  submissionError.value = ''
  submitting.value = true
  try {
    const response = await submitAnswer(
      { questionId, userAnswer: answer(), answerTime: Math.round((Date.now() - startTime.value) / 1000) },
      { errorDisplay: 'inline' },
    )
    if (
      !alive ||
      requestVersion !== submissionVersion ||
      session !== getAuthSessionVersion() ||
      currentQuestion.value?.id !== questionId
    )
      return
    if (response.code !== 0 || !response.data) {
      submissionError.value = response.message || '提交失败，请重试'
      return
    }
    currentResult.value = response.data
    if (response.data.correct === true) correctCount.value++
    else if (response.data.correct === false) wrongCount.value++
    else pendingCount.value++
    if (gamification.acceptReward(response.data.reward, session) && response.data.reward) {
      sessionXp.value += response.data.reward.awardedXp
      for (const achievement of response.data.reward.newAchievements)
        if (!sessionAchievements.value.some((item) => item.id === achievement.code))
          sessionAchievements.value.push({
            id: achievement.code,
            title: achievement.name,
            description: achievement.description,
          })
    }
    await nextTick()
    if (
      !alive ||
      requestVersion !== submissionVersion ||
      session !== getAuthSessionVersion() ||
      currentResult.value?.questionId !== questionId
    )
      return
    const nextElement = nextActionRef.value?.querySelector<HTMLElement>('button')
    if (nextElement) {
      nextElement.scrollIntoView?.({ block: 'nearest' })
      nextElement.focus()
    }
  } catch (error) {
    if (
      alive &&
      requestVersion === submissionVersion &&
      session === getAuthSessionVersion() &&
      currentQuestion.value?.id === questionId
    )
      submissionError.value = errorMessage(error, '提交答案失败，请重试')
  } finally {
    if (alive && requestVersion === submissionVersion && session === getAuthSessionVersion()) submitting.value = false
  }
}
async function nextQuestion() {
  if (!currentResult.value) return
  submissionError.value = ''
  currentResult.value = null
  if (currentIndex.value >= questions.value.length - 1) {
    finished.value = true
    return
  }
  currentIndex.value++
  reset()
  startTime.value = Date.now()
  await nextTick()
  questionHeadingRef.value?.scrollIntoView?.({ block: 'nearest' })
  questionHeadingRef.value?.focus()
}
function leavePractice() {
  clearPracticeSession()
  void router.push(practiceReturnRoute(practiceMode.value))
}
</script>

<template>
  <section class="lt page" data-appearance="dark" aria-label="练习会话">
    <header v-if="!sessionLoading && !sessionLoadError && (currentQuestion || finished)" class="session-header">
      <LtIconButton label="退出练习" @click="leavePractice"><ChevronLeft aria-hidden="true" /></LtIconButton>
      <div class="progress">
        <span>练习进度</span><strong>{{ currentIndex + 1 }} / {{ questions.length }}</strong
        ><span class="track"><i :style="{ width: `${progressPercent}%` }" /></span>
      </div>
      <p>已判分：{{ correctCount }} 对，{{ wrongCount }} 错</p>
    </header>
    <p v-if="sessionLoading" role="status" aria-busy="true">正在加载练习…</p>
    <div v-else-if="sessionLoadError" role="alert">
      <p>{{ sessionLoadError }}</p>
      <LtButton @click="loadSession">重试</LtButton><LtButton @click="leavePractice">返回练习</LtButton>
    </div>
    <section v-else-if="finished" class="finish-container">
      <PracticeSummary :summary="sessionSummary" @continue="leavePractice" />
    </section>
    <LtCard
      v-else-if="currentQuestion"
      as="article"
      radius="xl"
      padding="none"
      class="question-card"
      :aria-labelledby="`question-${currentQuestion.id}`"
      ><header class="question-head">
        <span class="tag">{{ getQuestionTypeLabel(currentQuestion.questionType) }}</span
        ><span>{{ currentQuestion.courseName }}</span
        ><span>{{ currentQuestion.score }} 分</span>
      </header>
      <h1 :id="`question-${currentQuestion.id}`" class="lt-sr-only">练习题</h1>
      <div ref="questionHeadingRef" class="question-content" tabindex="-1">
        <MarkdownRenderer :content="currentQuestion.content" />
      </div>
      <div v-if="currentQuestion.knowledgePointNames?.length" class="knowledge-points">
        <span v-for="name in currentQuestion.knowledgePointNames" :key="name">{{ name }}</span>
      </div>
      <fieldset class="answer-area" :disabled="answerLocked">
        <legend>作答</legend>
        <div v-if="currentQuestion.questionType === 'SINGLE_CHOICE'" class="option-list">
          <label
            v-for="option in currentQuestion.options"
            :key="option.id"
            class="option-item"
            :class="{ selected: userAnswer === option.optionLabel }"
            ><input v-model="userAnswer" type="radio" name="practice-answer" :value="option.optionLabel" /><span
              ><b>{{ option.optionLabel }}.</b>{{ option.content }}</span
            ></label
          >
        </div>
        <div v-else-if="currentQuestion.questionType === 'MULTIPLE_CHOICE'" class="option-list">
          <label
            v-for="option in currentQuestion.options"
            :key="option.id"
            class="option-item"
            :class="{ selected: multiAnswers.has(option.optionLabel) }"
            ><input
              type="checkbox"
              :checked="multiAnswers.has(option.optionLabel)"
              @change="toggleMulti(option.optionLabel)"
            /><span
              ><b>{{ option.optionLabel }}.</b>{{ option.content }}</span
            ></label
          >
        </div>
        <div v-else-if="currentQuestion.questionType === 'TRUE_FALSE'" class="option-list">
          <label class="option-item" :class="{ selected: userAnswer === 'TRUE' }"
            ><input v-model="userAnswer" type="radio" name="practice-answer" value="TRUE" /><span>正确</span></label
          ><label class="option-item" :class="{ selected: userAnswer === 'FALSE' }"
            ><input v-model="userAnswer" type="radio" name="practice-answer" value="FALSE" /><span>错误</span></label
          >
        </div>
        <textarea
          v-else
          v-model="userAnswer"
          :rows="currentQuestion.questionType === 'SHORT_ANSWER' ? 4 : 2"
          placeholder="请输入你的答案"
          aria-label="你的答案"
        />
      </fieldset>
      <PracticeAnswerFeedback v-if="currentResult" :result="currentResult" :wrong-practice="isWrongPractice" />
      <footer class="session-action">
        <LtButton
          v-if="!currentResult"
          variant="primary"
          :disabled="!canSubmit || !!submissionError || submitting"
          @click="handleSubmit"
          >{{ submitting ? '正在提交…' : '提交答案' }}</LtButton
        ><span v-else ref="nextActionRef"
          ><LtButton variant="primary" @click="nextQuestion"
            >{{ currentIndex < questions.length - 1 ? '下一题' : '查看结果'
            }}<template #trailing><ArrowRight aria-hidden="true" /></template></LtButton
        ></span>
        <p v-if="submissionError" class="submission-error" role="alert">
          {{ submissionError }} <button type="button" :disabled="submitting" @click="handleSubmit">重试</button>
        </p>
      </footer></LtCard
    >
  </section>
</template>

<style scoped>
.page {
  width: min(calc(var(--lt-space-10) * 13 + var(--lt-space-6)), 100%);
  margin: 0 auto;
  padding: var(--lt-space-7) var(--lt-space-7) calc(var(--lt-space-10) + var(--lt-space-8));
}
.session-header {
  display: grid;
  grid-template-columns: auto minmax(calc(var(--lt-space-10) * 2 + var(--lt-space-6)), 1fr) auto;
  gap: var(--lt-space-4);
  align-items: center;
  margin-bottom: var(--lt-space-6);
}
.session-header > p {
  margin: 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.progress {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: var(--lt-space-1) var(--lt-space-3);
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.progress strong {
  color: var(--lt-label);
  font-variant-numeric: tabular-nums;
}
.track {
  grid-column: 1 / -1;
  height: var(--lt-space-1);
  overflow: hidden;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
}
.track i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--lt-accent-fill);
  transition: width var(--lt-dur-layout) var(--lt-ease-layout);
}
.finish-container {
  display: flex;
  justify-content: center;
}
.question-head {
  display: flex;
  gap: var(--lt-space-3);
  align-items: center;
  padding: var(--lt-space-4) var(--lt-space-6);
  border-bottom: solid var(--lt-hairline);
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.question-head span:last-child {
  margin-left: auto;
}
.tag,
.knowledge-points span {
  padding: var(--lt-space-1) var(--lt-space-2);
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
}
.question-content {
  padding: var(--lt-space-7) var(--lt-space-6) var(--lt-space-4);
  font-size: var(--lt-text-reading);
  line-height: var(--lt-leading-reading);
}
.knowledge-points {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lt-space-2);
  padding: 0 var(--lt-space-6) var(--lt-space-5);
  color: var(--lt-label-2);
  font-size: var(--lt-text-caption);
}
.answer-area {
  display: grid;
  gap: var(--lt-space-3);
  margin: 0;
  padding: var(--lt-space-6);
  border: 0;
  border-top: solid var(--lt-hairline);
}
.answer-area legend {
  padding: 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.option-list {
  display: grid;
  gap: var(--lt-space-3);
}
.option-item {
  display: flex;
  gap: var(--lt-space-3);
  align-items: flex-start;
  padding: var(--lt-space-4);
  border: solid var(--lt-hairline);
  border-radius: var(--lt-radius-md);
  cursor: pointer;
}
.option-item.selected {
  border-color: var(--lt-accent);
  background: var(--lt-accent-soft);
}
.option-item input {
  flex: none;
  margin-top: var(--lt-space-1);
  accent-color: var(--lt-accent-fill);
}
.option-item b {
  display: inline-grid;
  place-items: center;
  width: var(--lt-space-6);
  height: var(--lt-space-6);
  margin-right: var(--lt-space-2);
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
  font-size: var(--lt-text-footnote);
}
.option-item:has(input:focus-visible) {
  outline: var(--lt-space-1) solid var(--lt-accent);
  outline-offset: var(--lt-space-1);
}
.answer-area textarea {
  width: 100%;
  resize: vertical;
  box-sizing: border-box;
  padding: var(--lt-space-3);
  border: solid var(--lt-hairline);
  border-radius: var(--lt-radius-md);
  background: var(--lt-fill-2);
  color: var(--lt-label);
  font: inherit;
}
.session-action {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--lt-space-3);
  padding: var(--lt-space-5) var(--lt-space-6) var(--lt-space-6);
  border-top: solid var(--lt-hairline);
}
.submission-error {
  margin: 0;
  color: var(--lt-red-text);
  font-size: var(--lt-text-footnote);
}
.submission-error button {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--lt-accent);
  font: inherit;
  text-decoration: underline;
  cursor: pointer;
}
@media (max-width: 640px) {
  .page {
    padding: var(--lt-space-4) var(--lt-space-3) var(--lt-space-8);
  }
  .session-header {
    grid-template-columns: auto 1fr;
  }
  .session-header > p {
    grid-column: 1 / -1;
  }
  .question-head {
    flex-wrap: wrap;
  }
  .question-head span:last-child {
    margin-left: 0;
  }
}
</style>
