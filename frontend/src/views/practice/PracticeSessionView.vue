<template>
  <section class="practice-session" aria-label="练习会话">
    <header class="session-header">
      <el-button text @click="leavePractice"
        ><el-icon><ArrowLeft /></el-icon>退出练习</el-button
      ><LpProgress
        class="session-progress"
        :percent="progressPercent"
        show-label
        :label="`${currentIndex + 1} / ${questions.length}`"
      />
      <p>已判分：{{ correctCount }} 对，{{ wrongCount }} 错</p>
    </header>
    <section v-if="finished" class="finish-container">
      <GamificationPracticeSummary :summary="sessionSummary" @continue="leavePractice" />
    </section>
    <LpCard v-else-if="currentQuestion" as="article" class="question-card">
      <template #header
        ><div class="question-meta">
          <el-tag :type="getQuestionTypeTag(currentQuestion.questionType)" size="small">{{
            getQuestionTypeLabel(currentQuestion.questionType)
          }}</el-tag
          ><span>{{ currentQuestion.courseName }}</span
          ><span>{{ currentQuestion.score }} 分</span>
        </div></template
      >
      <div ref="questionHeadingRef" class="question-content" tabindex="-1">
        <MarkdownRenderer :content="currentQuestion.content" />
      </div>
      <div v-if="currentQuestion.knowledgePointNames?.length" class="knowledge-points">
        <el-tag v-for="name in currentQuestion.knowledgePointNames" :key="name" size="small" effect="plain">{{
          name
        }}</el-tag>
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
              >{{ option.optionLabel }}. {{ option.content }}</span
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
            /><span>{{ option.optionLabel }}. {{ option.content }}</span></label
          >
        </div>
        <div v-else-if="currentQuestion.questionType === 'TRUE_FALSE'" class="option-list">
          <label class="option-item" :class="{ selected: userAnswer === 'TRUE' }"
            ><input v-model="userAnswer" type="radio" name="practice-answer" value="TRUE" />正确</label
          ><label class="option-item" :class="{ selected: userAnswer === 'FALSE' }"
            ><input v-model="userAnswer" type="radio" name="practice-answer" value="FALSE" />错误</label
          >
        </div>
        <el-input
          v-else
          v-model="userAnswer"
          type="textarea"
          :rows="currentQuestion.questionType === 'SHORT_ANSWER' ? 4 : 2"
          placeholder="请输入你的答案"
        />
      </fieldset>
      <PracticeAnswerFeedback v-if="currentResult" :result="currentResult" :wrong-practice="isWrongPractice" />
      <template #footer
        ><div class="session-action">
          <el-button
            v-if="!currentResult"
            type="primary"
            :loading="submitting"
            :disabled="!canSubmit || !!submissionError"
            @click="handleSubmit"
            >提交答案</el-button
          ><span v-else ref="nextActionRef"
            ><el-button type="primary" @click="nextQuestion">{{
              currentIndex < questions.length - 1 ? '下一题' : '查看结果'
            }}</el-button></span
          >
          <p v-if="submissionError" class="submission-error" role="alert">
            {{ submissionError }}
            <button type="button" :disabled="submitting" @click="handleSubmit">重试</button>
          </p>
        </div></template
      >
    </LpCard>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { submitAnswer, type PracticeQuestionVO, type PracticeResultVO } from '@/api/practice'
import GamificationPracticeSummary from '@/components/gamification/GamificationPracticeSummary.vue'
import type { GamificationAchievement } from '@/components/gamification/types'
import { useGamificationStore } from '@/stores/gamification'
import { useUserStore } from '@/stores/user'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'
import { errorMessage } from '@/utils/errors'
import { clearPracticeSession, loadPracticeSession } from '@/utils/practiceSession'
import PracticeAnswerFeedback from './PracticeAnswerFeedback.vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import {
  practiceQuestionTypeLabel as getQuestionTypeLabel,
  practiceQuestionTypeTag as getQuestionTypeTag,
  practiceReturnRoute,
} from './practiceSessionPresentation'
import { usePracticeAnswer } from './usePracticeAnswer'

const router = useRouter()
const questions = ref<PracticeQuestionVO[]>([]),
  currentIndex = ref(0),
  submitting = ref(false),
  currentResult = ref<PracticeResultVO | null>(null),
  finished = ref(false),
  correctCount = ref(0),
  wrongCount = ref(0),
  pendingCount = ref(0),
  startTime = ref(Date.now()),
  practiceMode = ref(''),
  sessionXp = ref(0),
  sessionAchievements = ref<GamificationAchievement[]>([])
const submissionError = ref('')
const nextActionRef = ref<HTMLElement | null>(null)
const questionHeadingRef = ref<HTMLElement | null>(null)
const gamification = useGamificationStore()
let alive = true
let submissionVersion = 0
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
onMounted(async () => {
  const user = useUserStore()
  const session = getAuthSessionVersion()
  if (!user.userInfo) await user.fetchUserInfo()
  if (!alive || session !== getAuthSessionVersion()) return
  const stored = loadPracticeSession(user.userInfo?.id)
  if (!stored) {
    ElMessage.warning('没有练习题目，请先选择练习条件')
    void router.replace({ name: 'Practice' })
    return
  }
  questions.value = stored.questions
  practiceMode.value = stored.mode
  startTime.value = Date.now()
})
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

<style scoped>
.practice-session {
  width: min(840px, 100%);
  margin: 0 auto;
  padding: var(--lp-space-6);
}
.session-header {
  display: grid;
  grid-template-columns: auto minmax(180px, 1fr) auto;
  gap: var(--lp-space-4);
  align-items: center;
  margin-bottom: var(--lp-space-6);
  padding: var(--lp-space-3) var(--lp-space-4);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  background: var(--lp-surface);
}
.session-header p {
  margin: 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}
.question-meta {
  display: flex;
  gap: var(--lp-space-3);
  width: 100%;
  color: var(--lp-text-muted);
  font-size: var(--lp-text-sm);
}
.question-meta span:last-child {
  margin-left: auto;
}
.question-content {
  margin: 0 0 var(--lp-space-4);
  color: var(--lp-text);
  line-height: var(--lp-leading-relaxed);
}
.knowledge-points,
.option-list {
  display: grid;
  gap: var(--lp-space-3);
}
.knowledge-points {
  grid-template-columns: repeat(auto-fit, minmax(0, max-content));
  margin-bottom: var(--lp-space-5);
}
.answer-area {
  display: grid;
  gap: var(--lp-space-3);
  margin: 0;
  padding: 0;
  border: 0;
}
.answer-area legend {
  padding: 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}
.option-item {
  display: flex;
  gap: var(--lp-space-3);
  align-items: center;
  padding: var(--lp-space-4);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-control);
  cursor: pointer;
}
.option-item:hover {
  background: var(--lp-surface-hover);
}
.option-item.selected {
  border-color: var(--lp-primary);
  background: var(--lp-surface-selected);
}
.option-item:has(input:focus-visible) {
  outline: var(--lp-focus-width) solid var(--lp-focus-ring);
  outline-offset: var(--lp-focus-offset);
  box-shadow: var(--lp-shadow-focus);
}
.option-item input {
  margin: 0;
  accent-color: var(--lp-primary);
}
.session-action,
.finish-container {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--lp-space-3);
  width: 100%;
}
.submission-error {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: var(--lp-space-2);
  margin: 0;
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
.finish-container {
  justify-content: center;
  padding-top: var(--lp-space-6);
}
@media (max-width: 767px) {
  .practice-session {
    padding: var(--lp-space-3);
  }
  .session-header {
    grid-template-columns: 1fr;
  }
  .question-meta {
    flex-wrap: wrap;
  }
}
</style>
