<template>
  <div class="lt paper-learning" data-appearance="dark">
    <div v-if="loading" class="state" role="status" aria-busy="true">正在恢复试卷学习会话</div>
    <div v-else-if="loadFailed" class="state" role="alert">
      <h1>试卷学习会话暂时无法读取</h1>
      <p>{{ loadError || '请重新读取会话。' }}</p>
      <LtButton @click="loadSession">重试</LtButton>
    </div>
    <div v-else-if="!session" class="state">
      <h1>试卷学习会话不存在</h1>
      <p>该学习会话可能已失效或被删除。</p>
      <LtButton @click="router.push('/exams')">查看试卷列表</LtButton>
    </div>
    <template v-else-if="currentQuestion"
      ><header class="learning-header">
        <h1>{{ session.paperTitle }}</h1>
        <p v-if="session.paperType === 'OFFICIAL_EXAM' && session.sourceVerified" class="muted">
          {{ session.examYear }} · {{ session.examName }} · 来源：{{ session.sourceReference }}
        </p>
        <p class="muted">
          {{
            session.status === 1 ? '本轮已完成' : `已作答 ${session.answeredQuestionCount}/${session.questions.length}`
          }}
        </p>
        <progress :value="session.answeredQuestionCount" :max="session.questions.length" aria-label="本轮学习进度" />
      </header>
      <section class="learning-layout">
        <article class="question-card" aria-labelledby="learning-question-title">
          <p v-if="currentQuestion.sectionTitle" class="muted">{{ currentQuestion.sectionTitle }}</p>
          <div class="question-meta">
            <strong>{{ currentQuestion.displayNumber || `第 ${currentIndex + 1} 题` }}</strong
            ><span>{{ questionTypeLabel(currentQuestion.questionType) }}</span
            ><span>{{ currentQuestion.score }} 分</span>
          </div>
          <div id="learning-question-title" ref="questionTitle" class="question-content" tabindex="-1">
            <QuestionProse :content="currentQuestion.content" />
          </div>
          <fieldset class="answer-box" :disabled="answerLocked" aria-describedby="answer-help">
            <legend class="sr-only">作答</legend>
            <p id="answer-help" class="sr-only">选择答案后提交。本题保存后将锁定作答内容。</p>
            <ExamLearningChoices
              v-if="choiceOptions.length"
              :options="choiceOptions"
              :multiple="currentQuestion.questionType === 'MULTIPLE_CHOICE'"
              :binary="currentQuestion.questionType === 'TRUE_FALSE'"
              :name="`learning-answer-${currentQuestion.questionId}`"
              :label="
                currentQuestion.questionType === 'MULTIPLE_CHOICE'
                  ? '多选答案'
                  : currentQuestion.questionType === 'TRUE_FALSE'
                    ? '判断答案'
                    : '单选答案'
              "
              :is-selected="isSelected"
              :evaluated="evaluated"
              :correct-answers="correctAnswers"
              @pick="pick"
            /><textarea v-else v-model="userAnswer" :rows="4" aria-label="你的答案" placeholder="请输入你的答案" />
          </fieldset>
          <div v-if="saveError" class="inline-error" role="alert">
            <p>{{ saveError }}</p>
            <LtButton @click="submitCurrentAnswer">重新提交</LtButton>
          </div>
          <div class="question-actions">
            <LtButton :disabled="currentIndex === 0 || submitting" @click="goTo(currentIndex - 1)">上一题</LtButton
            ><LtButton v-if="currentQuestion.latestAnswer && session.status === 0 && !redoing" @click="startRedo"
              >重新作答</LtButton
            ><LtButton
              v-if="(!currentQuestion.latestAnswer || redoing) && session.status === 0"
              variant="primary"
              :disabled="!canSubmit"
              @click="submitCurrentAnswer"
              >{{ submitting ? '正在提交…' : '提交答案' }}</LtButton
            ><LtButton
              :disabled="currentIndex >= session.questions.length - 1 || submitting"
              @click="goTo(currentIndex + 1)"
              >下一题</LtButton
            >
          </div>
        </article>
        <ExamLearningFeedback
          v-if="currentQuestion.latestAnswer && !redoing"
          ref="answerFeedback"
          :result="currentQuestion.latestAnswer"
        />
        <AiQuestionAssistant
          :key="`${currentQuestion.questionId}:${currentQuestion.latestAnswer?.answerId || 0}:${redoing}`"
          :question-id="currentQuestion.questionId"
          :learning-session-id="session.id"
          :disabled="!currentQuestion.latestAnswer || redoing"
          disabled-reason="先提交本题答案，再让 AI 结合本轮真实作答提供辅导。"
        />
        <aside class="answer-sheet" aria-label="答题进度">
          <h2>本轮学习</h2>
          <p class="muted">已答 {{ session.answeredQuestionCount }} · 已判分答对 {{ session.correctQuestionCount }}</p>
          <div class="sheet-grid">
            <button
              v-for="(question, index) in session.questions"
              :key="question.questionId"
              type="button"
              :class="[
                'sheet-item',
                {
                  current: index === currentIndex,
                  answered: question.latestAnswer,
                  correct: question.latestAnswer?.correct === true,
                  wrong: question.latestAnswer?.correct === false,
                },
              ]"
              :aria-current="index === currentIndex ? 'step' : undefined"
              :aria-label="`${question.displayNumber || `第 ${index + 1} 题`}${index === currentIndex ? '，当前题' : ''}${question.latestAnswer ? '，已作答' : '，未作答'}`"
              @click="goTo(index)"
            >
              {{ question.displayNumber || index + 1 }}
            </button>
          </div>
          <LtButton
            v-if="session.status === 0"
            :disabled="session.answeredQuestionCount < session.questions.length || redoing || submitting || completing"
            class="complete-button"
            @click="completeLearning"
            >完成本轮学习</LtButton
          >
          <p v-if="session.status === 0 && session.answeredQuestionCount < session.questions.length" class="muted">
            全部题目至少作答一次后可完成。
          </p>
          <p v-if="completing" role="status">正在保存本轮学习…</p>
          <div v-if="completionError" class="inline-error" role="alert">
            <p>{{ completionError }}</p>
            <LtButton @click="completeLearning">重新完成</LtButton>
          </div>
        </aside>
      </section>
      <section v-if="session.status === 1" class="completion-summary">
        <h2>本轮学习已完成</h2>
        <p>已保存 {{ session.answeredQuestionCount }} 题作答；可继续查看每题反馈，或返回试卷列表开始下一项学习。</p>
        <LtButton variant="primary" @click="router.push('/exams')">查看试卷列表</LtButton>
      </section>
    </template>
    <div v-else class="state" role="status">
      <h1>这份试卷暂无可作答题目</h1>
      <p>请核对试卷内容后重新开始学习。</p>
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useGamificationStore } from '@/stores/gamification'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'
import {
  completeExamLearningSession,
  getExamLearningSession,
  submitExamLearningAnswer,
  type ExamLearningSessionVO,
} from '@/api/exam'
import AiQuestionAssistant from '@/views/practice/PracticeAiAssistant.vue'
import { LtButton } from '@/components/living'
import ExamLearningChoices from './ExamLearningChoices.vue'
import QuestionProse from '@/views/question/QuestionProse.vue'
import ExamLearningFeedback from './ExamLearningFeedback.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const loadFailed = ref(false)
const loadError = ref('')
const saveError = ref('')
const completionError = ref('')
const submitting = ref(false)
const completing = ref(false)
const session = ref<ExamLearningSessionVO | null>(null)
const currentIndex = ref(0)
const userAnswer = ref('')
const multiAnswers = ref<Set<string>>(new Set())
const redoing = ref(false)
const answerStartedAt = ref(Date.now())
const questionTitle = ref<HTMLElement>()
const answerFeedback = ref<{ $el?: HTMLElement }>()
let alive = true
let generation = 0

const currentQuestion = computed(() => session.value?.questions[currentIndex.value] || null)
const answerLocked = computed(
  () =>
    !session.value ||
    session.value.status === 1 ||
    submitting.value ||
    (!!currentQuestion.value?.latestAnswer && !redoing.value),
)
const canSubmit = computed(() => {
  if (!currentQuestion.value || answerLocked.value) return false
  return currentQuestion.value.questionType === 'MULTIPLE_CHOICE'
    ? multiAnswers.value.size > 0
    : userAnswer.value.trim().length > 0
})
function isCurrent(requestGeneration: number, authSession: number, sessionId?: number) {
  return (
    alive &&
    requestGeneration === generation &&
    authSession === getAuthSessionVersion() &&
    (sessionId === undefined || session.value?.id === sessionId)
  )
}
function resetAnswerInput() {
  const savedAnswer = currentQuestion.value?.latestAnswer?.userAnswer || ''
  userAnswer.value = currentQuestion.value?.questionType === 'MULTIPLE_CHOICE' ? '' : savedAnswer
  multiAnswers.value = new Set(
    currentQuestion.value?.questionType === 'MULTIPLE_CHOICE' ? savedAnswer.split(',').filter(Boolean) : [],
  )
  answerStartedAt.value = Date.now()
  saveError.value = ''
  redoing.value = false
}
function toggleMulti(label: string) {
  const next = new Set(multiAnswers.value)
  if (next.has(label)) next.delete(label)
  else next.add(label)
  multiAnswers.value = next
}
const choiceOptions = computed(() => {
  const question = currentQuestion.value
  if (!question) return []
  if (question.questionType === 'TRUE_FALSE')
    return [
      { key: '', value: 'TRUE', content: '正确' },
      { key: '', value: 'FALSE', content: '错误' },
    ]
  return question.options.map((option) => ({
    key: option.optionLabel,
    value: option.optionLabel,
    content: option.content,
  }))
})
const evaluated = computed(() => !redoing.value && currentQuestion.value?.latestAnswer?.correct != null)
const correctAnswers = computed(() =>
  (currentQuestion.value?.latestAnswer?.correctAnswer || '').split(',').map((value) => value.trim()),
)
function isSelected(value: string) {
  return currentQuestion.value?.questionType === 'MULTIPLE_CHOICE'
    ? multiAnswers.value.has(value)
    : userAnswer.value === value
}
function pick(value: string) {
  if (answerLocked.value) return
  if (currentQuestion.value?.questionType === 'MULTIPLE_CHOICE') toggleMulti(value)
  else userAnswer.value = value
}
function questionTypeLabel(type: string) {
  return (
    (
      {
        SINGLE_CHOICE: '单选题',
        MULTIPLE_CHOICE: '多选题',
        TRUE_FALSE: '判断题',
        FILL_BLANK: '填空题',
        SHORT_ANSWER: '简答题',
      } as Record<string, string>
    )[type] || type
  )
}

async function loadSession() {
  const sessionId = Number(route.params.sessionId)
  if (!Number.isFinite(sessionId) || sessionId <= 0) {
    loading.value = false
    return
  }
  const requestGeneration = ++generation
  const authSession = getAuthSessionVersion()
  loading.value = true
  loadFailed.value = false
  loadError.value = ''
  try {
    const response = await getExamLearningSession(sessionId, { errorDisplay: 'inline' })
    if (!isCurrent(requestGeneration, authSession)) return
    if (response.code === 0 && response.data) {
      session.value = response.data
      const savedIndex = response.data.questions.findIndex(
        (item) => item.questionId === response.data.currentQuestionId,
      )
      currentIndex.value = savedIndex >= 0 ? savedIndex : 0
      resetAnswerInput()
    } else {
      loadFailed.value = true
      loadError.value = response.message || '请重新读取会话。'
    }
  } catch {
    if (isCurrent(requestGeneration, authSession)) {
      loadFailed.value = true
      loadError.value = '请检查网络后重试。'
    }
  } finally {
    if (isCurrent(requestGeneration, authSession)) loading.value = false
  }
}
function goTo(index: number) {
  if (!session.value || index < 0 || index >= session.value.questions.length || submitting.value) return
  currentIndex.value = index
}
function startRedo() {
  if (!currentQuestion.value?.latestAnswer || session.value?.status === 1) return
  redoing.value = true
  saveError.value = ''
  answerStartedAt.value = Date.now()
}
watch(currentQuestion, async () => {
  resetAnswerInput()
  await nextTick()
  questionTitle.value?.scrollIntoView({ block: 'nearest', behavior: 'auto' })
  questionTitle.value?.focus({ preventScroll: true })
})

async function submitCurrentAnswer() {
  if (!session.value || !currentQuestion.value || !canSubmit.value || submitting.value) return
  const authSession = getAuthSessionVersion(),
    requestGeneration = generation,
    learningSessionId = session.value.id,
    submittedQuestion = currentQuestion.value
  const answer =
    submittedQuestion.questionType === 'MULTIPLE_CHOICE'
      ? Array.from(multiAnswers.value).sort().join(',')
      : userAnswer.value.trim()
  submitting.value = true
  saveError.value = ''
  try {
    const response = await submitExamLearningAnswer(
      learningSessionId,
      {
        questionId: submittedQuestion.questionId,
        userAnswer: answer,
        answerTime: Math.max(0, Math.round((Date.now() - answerStartedAt.value) / 1000)),
      },
      { errorDisplay: 'inline' },
    )
    if (!isCurrent(requestGeneration, authSession, learningSessionId)) return
    if (response.code === 0 && response.data) {
      submittedQuestion.latestAnswer = response.data
      redoing.value = false
      if (response.data.reward) useGamificationStore().acceptReward(response.data.reward, authSession)
      refreshSummary()
      await nextTick()
      answerFeedback.value?.$el?.scrollIntoView({ block: 'nearest', behavior: 'auto' })
      answerFeedback.value?.$el?.focus({ preventScroll: true })
    } else saveError.value = response.message || '提交答案失败，请重试。'
  } catch {
    if (isCurrent(requestGeneration, authSession, learningSessionId))
      saveError.value = '提交答案失败，请检查网络后重试。'
  } finally {
    if (isCurrent(requestGeneration, authSession, learningSessionId)) submitting.value = false
  }
}
function refreshSummary() {
  if (!session.value) return
  session.value.answeredQuestionCount = session.value.questions.filter((item) => item.latestAnswer).length
  session.value.correctQuestionCount = session.value.questions.filter(
    (item) => item.latestAnswer?.correct === true,
  ).length
}
async function completeLearning() {
  if (!session.value || completing.value) return
  const requestGeneration = generation,
    authSession = getAuthSessionVersion(),
    learningSessionId = session.value.id
  completing.value = true
  completionError.value = ''
  try {
    const response = await completeExamLearningSession(learningSessionId, { errorDisplay: 'inline' })
    if (!isCurrent(requestGeneration, authSession, learningSessionId)) return
    if (response.code === 0 && response.data) session.value = response.data
    else completionError.value = response.message || '完成学习失败，请重试。'
  } catch {
    if (isCurrent(requestGeneration, authSession, learningSessionId))
      completionError.value = '完成学习失败，请检查网络后重试。'
  } finally {
    if (isCurrent(requestGeneration, authSession, learningSessionId)) completing.value = false
  }
}
const unsubscribeAuth = onAuthSessionChange(() => {
  generation++
  loading.value = false
  submitting.value = false
  completing.value = false
  session.value = null
  router.replace('/exams')
})
onMounted(loadSession)
onUnmounted(() => {
  alive = false
  generation++
  unsubscribeAuth()
})
</script>
<style scoped>
.paper-learning {
  display: grid;
  gap: var(--lt-space-6);
  color: var(--lt-label);
}
h1 {
  margin: 0;
  font-size: var(--lt-text-title1);
  line-height: var(--lt-leading-tight);
  overflow-wrap: anywhere;
}
h2 {
  margin: 0;
  font-size: var(--lt-text-title3);
}
.learning-header {
  display: grid;
  gap: var(--lt-space-3);
  padding-block: var(--lt-space-5);
}
p {
  margin: 0;
  line-height: var(--lt-leading-reading);
}
.muted {
  color: var(--lt-label-2);
}
progress {
  display: block;
  width: 100%;
  height: var(--lt-space-1);
  border: 0;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill);
  color: var(--lt-accent);
  accent-color: var(--lt-accent);
}
progress::-webkit-progress-bar {
  background: var(--lt-fill);
  border-radius: var(--lt-radius-pill);
}
progress::-webkit-progress-value {
  background: var(--lt-accent);
  border-radius: var(--lt-radius-pill);
}
progress::-moz-progress-bar {
  background: var(--lt-accent);
  border-radius: var(--lt-radius-pill);
}
.learning-layout {
  display: grid;
  gap: var(--lt-space-6);
}
.question-card {
  padding: var(--lt-space-6);
  background: var(--lt-surface);
  border-radius: var(--lt-radius-xl);
  box-shadow: var(--lt-shadow-card);
  min-width: 0;
}
.question-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lt-space-3);
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.question-meta strong {
  color: var(--lt-label);
}
.question-content {
  scroll-margin-top: calc(var(--lt-space-10) + var(--lt-space-4));
  margin-block: var(--lt-space-5);
}
.answer-box {
  border: 0;
  padding: 0;
  margin: 0;
  min-width: 0;
}
textarea {
  width: 100%;
  padding: var(--lt-space-4);
  background: var(--lt-fill-2);
  color: var(--lt-label);
  border: 1px solid var(--lt-separator);
  border-radius: var(--lt-radius-md);
  font: var(--lt-text-reading)/var(--lt-leading-reading) var(--lt-font);
  resize: vertical;
}
.question-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--lt-space-2);
  flex-wrap: wrap;
  margin-top: var(--lt-space-5);
}
.answer-sheet {
  padding: var(--lt-space-5);
  background: var(--lt-surface);
  border-radius: var(--lt-radius-lg);
  display: grid;
  gap: var(--lt-space-3);
}
.sheet-grid {
  display: flex;
  gap: var(--lt-space-2);
  flex-wrap: wrap;
}
.sheet-item {
  padding: var(--lt-space-2);
  min-width: var(--lt-control-lg);
  min-height: var(--lt-control-lg);
  background: var(--lt-fill-2);
  border: 0;
  border-radius: var(--lt-radius-sm);
  color: var(--lt-label-2);
  font: inherit;
  cursor: pointer;
}
.sheet-item.answered {
  background: var(--lt-accent-soft);
  color: var(--lt-accent);
}
.sheet-item.correct {
  background: var(--lt-green-soft);
  color: var(--lt-green-text);
}
.sheet-item.wrong {
  background: var(--lt-orange-soft);
  color: var(--lt-orange-text);
}
.sheet-item.current {
  outline: 2px solid var(--lt-accent);
  outline-offset: 2px;
}
.complete-button {
  justify-self: start;
}
.inline-error {
  color: var(--lt-red-text);
  margin-top: var(--lt-space-4);
}
.state {
  padding: var(--lt-space-8);
  text-align: center;
  background: var(--lt-surface);
  border-radius: var(--lt-radius-xl);
}
.state p {
  margin-block: var(--lt-space-4);
  color: var(--lt-label-2);
}
.completion-summary {
  display: grid;
  gap: var(--lt-space-4);
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
textarea::placeholder {
  color: var(--lt-label-2);
  opacity: 1;
}
</style>
