<template>
  <div class="paper-learning">
    <LpStatePanel v-if="loading" state="loading" loading-label="正在恢复试卷学习会话" />
    <LpStatePanel
      v-else-if="loadFailed"
      state="error"
      title="试卷学习会话暂时无法读取"
      :description="loadError || '请重新读取会话。'"
      @retry="loadSession"
    />
    <LpEmptyState v-else-if="!session" title="试卷学习会话不存在" description="该学习会话可能已失效或被删除。">
      <template #actions><el-button type="primary" @click="router.push('/exams')">返回试卷列表</el-button></template>
    </LpEmptyState>

    <template v-else-if="currentQuestion">
      <section class="learning-header">
        <div class="learning-header-copy">
          <span class="section-kicker">试卷学习</span>
          <h1 class="learning-title">{{ session.paperTitle }}</h1>
          <p v-if="session.paperType === 'OFFICIAL_EXAM' && session.sourceVerified" class="paper-source">
            {{ session.examYear }} · {{ session.examName }} · 来源：{{ session.sourceReference }}
          </p>
        </div>
        <div class="header-actions">
          <span class="learning-progress" aria-live="polite">
            {{
              session.status === 1
                ? '本轮已完成'
                : `已作答 ${session.answeredQuestionCount}/${session.questions.length}`
            }}
          </span>
          <el-button @click="router.push('/exams')">返回试卷列表</el-button>
        </div>
      </section>

      <section class="learning-layout">
        <article class="question-card" aria-labelledby="learning-question-title">
          <div v-if="currentQuestion.sectionTitle" class="question-section">{{ currentQuestion.sectionTitle }}</div>
          <div class="question-meta">
            <strong>{{ currentQuestion.displayNumber || `第 ${currentIndex + 1} 题` }}</strong>
            <el-tag size="small" effect="plain">{{ questionTypeLabel(currentQuestion.questionType) }}</el-tag>
            <span>{{ currentQuestion.score }} 分</span>
          </div>
          <div id="learning-question-title" ref="questionTitle" class="question-content" tabindex="-1">
            {{ currentQuestion.content }}
          </div>

          <fieldset class="answer-box" :disabled="answerLocked" aria-describedby="answer-help">
            <legend>作答</legend>
            <p id="answer-help" class="sr-only">选择答案后提交。本题保存后将锁定作答内容。</p>
            <div
              v-if="currentQuestion.questionType === 'SINGLE_CHOICE'"
              class="option-list"
              role="radiogroup"
              aria-label="单选答案"
            >
              <label
                v-for="option in currentQuestion.options"
                :key="option.id"
                class="option-item"
                :class="{ selected: userAnswer === option.optionLabel }"
              >
                <input v-model="userAnswer" type="radio" name="learning-answer" :value="option.optionLabel" />
                <span class="option-badge">{{ option.optionLabel }}</span
                ><span>{{ option.content }}</span>
              </label>
            </div>
            <div
              v-else-if="currentQuestion.questionType === 'MULTIPLE_CHOICE'"
              class="option-list"
              role="group"
              aria-label="多选答案"
            >
              <label
                v-for="option in currentQuestion.options"
                :key="option.id"
                class="option-item"
                :class="{ selected: multiAnswers.has(option.optionLabel) }"
              >
                <input
                  type="checkbox"
                  :checked="multiAnswers.has(option.optionLabel)"
                  @change="toggleMulti(option.optionLabel)"
                />
                <span class="option-badge">{{ option.optionLabel }}</span
                ><span>{{ option.content }}</span>
              </label>
            </div>
            <div
              v-else-if="currentQuestion.questionType === 'TRUE_FALSE'"
              class="option-list true-false-list"
              role="radiogroup"
              aria-label="判断答案"
            >
              <label class="option-item" :class="{ selected: userAnswer === 'TRUE' }"
                ><input v-model="userAnswer" type="radio" name="learning-answer" value="TRUE" />正确</label
              >
              <label class="option-item" :class="{ selected: userAnswer === 'FALSE' }"
                ><input v-model="userAnswer" type="radio" name="learning-answer" value="FALSE" />错误</label
              >
            </div>
            <el-input v-else v-model="userAnswer" type="textarea" :rows="4" placeholder="请输入你的答案" />
          </fieldset>

          <div v-if="saveError" class="inline-error" role="alert">
            <p>{{ saveError }}</p>
            <el-button size="small" @click="submitCurrentAnswer">重新提交</el-button>
          </div>
          <ExamLearningFeedback
            v-if="currentQuestion.latestAnswer && !redoing"
            ref="answerFeedback"
            :result="currentQuestion.latestAnswer"
          />

          <div class="question-actions">
            <el-button :disabled="currentIndex === 0 || submitting" @click="goTo(currentIndex - 1)">上一题</el-button>
            <el-button v-if="currentQuestion.latestAnswer && session.status === 0 && !redoing" @click="startRedo">
              重新作答
            </el-button>
            <el-button
              v-if="(!currentQuestion.latestAnswer || redoing) && session.status === 0"
              type="primary"
              :disabled="!canSubmit"
              :loading="submitting"
              @click="submitCurrentAnswer"
              >提交答案</el-button
            >
            <el-button
              :disabled="currentIndex >= session.questions.length - 1 || submitting"
              @click="goTo(currentIndex + 1)"
              >下一题</el-button
            >
          </div>

          <AiQuestionAssistant
            :key="`${currentQuestion.questionId}:${currentQuestion.latestAnswer?.answerId || 0}:${redoing}`"
            :question-id="currentQuestion.questionId"
            :learning-session-id="session.id"
            :disabled="!currentQuestion.latestAnswer || redoing"
            disabled-reason="先提交本题答案，再让 AI 结合本轮真实作答提供辅导。"
          />
        </article>

        <aside class="answer-sheet" aria-label="答题进度">
          <h2 class="sheet-title">本轮学习</h2>
          <p class="sheet-summary">
            已答 {{ session.answeredQuestionCount }} · 已判分答对 {{ session.correctQuestionCount }}
          </p>
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
                },
              ]"
              :aria-current="index === currentIndex ? 'step' : undefined"
              :aria-label="`${question.displayNumber || `第 ${index + 1} 题`}${index === currentIndex ? '，当前题' : ''}${question.latestAnswer ? '，已作答' : '，未作答'}`"
              @click="goTo(index)"
            >
              {{ question.displayNumber || index + 1 }}
            </button>
          </div>
          <el-button
            v-if="session.status === 0"
            type="primary"
            :disabled="session.answeredQuestionCount < session.questions.length || redoing || submitting"
            :loading="completing"
            class="complete-button"
            @click="completeLearning"
            >完成本轮学习</el-button
          >
          <p
            v-if="session.status === 0 && session.answeredQuestionCount < session.questions.length"
            class="complete-hint"
          >
            全部题目至少作答一次后可完成。
          </p>
          <div v-if="completionError" class="inline-error" role="alert">
            <p>{{ completionError }}</p>
            <el-button size="small" @click="completeLearning">重新完成</el-button>
          </div>
        </aside>
      </section>

      <section v-if="session.status === 1" class="completion-summary" aria-live="polite">
        <h2>本轮学习已完成</h2>
        <p>已保存 {{ session.answeredQuestionCount }} 题作答；可继续查看每题反馈，或返回试卷列表开始下一项学习。</p>
        <el-button type="primary" @click="router.push('/exams')">返回试卷列表</el-button>
      </section>
    </template>
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
import AiQuestionAssistant from '@/components/AiQuestionAssistant.vue'
import LpEmptyState from '@/components/ui/LpEmptyState.vue'
import LpStatePanel from '@/components/ui/LpStatePanel.vue'
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
  questionTitle.value?.scrollIntoView({ block: 'start', behavior: 'auto' })
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
  gap: var(--lp-space-5);
  width: min(100%, var(--lp-container-narrow));
  margin: 0 auto;
  padding: var(--lp-space-4) 0;
}
.learning-header,
.question-card,
.answer-sheet,
.completion-summary {
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}
.learning-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--lp-space-5);
  padding: var(--lp-space-6);
}
.learning-header-copy {
  min-width: 0;
}
.section-kicker {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
}
.learning-title {
  margin: var(--lp-space-1) 0 0;
  color: var(--lp-text);
  font-size: var(--lp-text-3xl);
  line-height: var(--lp-leading-tight);
}
.paper-source {
  margin: var(--lp-space-2) 0 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  overflow-wrap: anywhere;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  flex-shrink: 0;
}
.learning-progress {
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  white-space: nowrap;
}
.learning-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 240px;
  gap: var(--lp-space-4);
  align-items: start;
}
.question-card {
  min-width: 0;
  padding: var(--lp-space-6);
}
.question-section,
.question-meta {
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}
.question-section {
  margin-bottom: var(--lp-space-2);
  font-weight: var(--lp-weight-bold);
}
.question-meta {
  display: flex;
  align-items: center;
  gap: var(--lp-space-2);
}
.question-meta strong {
  color: var(--lp-text);
  font-size: var(--lp-text-lg);
}
.question-content {
  margin: var(--lp-space-5) 0;
  color: var(--lp-text);
  font-size: var(--lp-text-lg);
  line-height: var(--lp-leading-relaxed);
  white-space: pre-wrap;
  outline: none;
}
.answer-box {
  min-width: 0;
  padding: 0;
  border: 0;
}
.answer-box legend {
  margin-bottom: var(--lp-space-3);
  color: var(--lp-text);
  font-weight: var(--lp-weight-semibold);
}
.option-list {
  display: grid;
  gap: var(--lp-space-3);
}
.option-item {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  padding: var(--lp-space-3) var(--lp-space-4);
  color: var(--lp-text);
  background: var(--lp-surface);
  border: 1px solid var(--lp-border-strong);
  border-radius: var(--lp-radius-md);
  cursor: pointer;
}
.option-item:focus-within,
.option-item:hover {
  border-color: var(--lp-primary);
  background: var(--lp-surface-subtle);
}
.option-item.selected {
  border-color: var(--lp-primary);
  background: var(--lp-primary-soft);
}
.option-item input {
  accent-color: var(--lp-primary);
}
.option-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  flex: 0 0 auto;
  color: var(--lp-primary);
  background: var(--lp-primary-soft);
  border-radius: var(--lp-radius-full);
  font-weight: var(--lp-weight-bold);
}
.true-false-list {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.true-false-list .option-item {
  justify-content: center;
}
.question-actions {
  display: flex;
  justify-content: center;
  gap: var(--lp-space-3);
  margin-top: var(--lp-space-5);
}
.inline-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lp-space-3);
  margin-top: var(--lp-space-4);
  padding: var(--lp-space-3);
  color: var(--lp-text);
  background: var(--lp-danger-soft);
  border-inline-start: 3px solid var(--lp-danger);
}
.inline-error p {
  margin: 0;
}
.answer-sheet {
  position: sticky;
  top: var(--lp-space-4);
  padding: var(--lp-space-4);
}
.sheet-title {
  margin: 0 0 var(--lp-space-2);
  color: var(--lp-text);
  font-size: var(--lp-text-lg);
}
.sheet-summary {
  margin: 0 0 var(--lp-space-4);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-xs);
}
.sheet-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--lp-space-2);
}
.sheet-item {
  min-width: 0;
  height: var(--lp-control-height-small);
  color: var(--lp-text-secondary);
  background: var(--lp-surface-soft);
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius-sm);
  cursor: pointer;
}
.sheet-item.answered {
  color: var(--lp-on-primary);
  background: var(--lp-warning);
  border-color: var(--lp-warning);
}
.sheet-item.correct {
  background: var(--lp-success);
  border-color: var(--lp-success);
}
.sheet-item.current {
  box-shadow: 0 0 0 var(--lp-focus-width) var(--lp-primary);
}
.complete-button {
  width: 100%;
  margin-top: var(--lp-space-4);
}
.complete-hint {
  margin: var(--lp-space-2) 0 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-xs);
  line-height: var(--lp-leading-snug);
}
.completion-summary {
  display: grid;
  gap: var(--lp-space-3);
  padding: var(--lp-space-5);
}
.completion-summary h2,
.completion-summary p {
  margin: 0;
}
.completion-summary h2 {
  color: var(--lp-text);
  font-size: var(--lp-text-xl);
}
.completion-summary p {
  color: var(--lp-text-secondary);
  line-height: var(--lp-leading-body);
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}
@media (max-width: 860px) {
  .learning-layout {
    grid-template-columns: 1fr;
  }
  .answer-sheet {
    position: static;
  }
}
@media (max-width: 640px) {
  .paper-learning {
    padding: 0;
  }
  .learning-header {
    align-items: stretch;
    flex-direction: column;
    padding: var(--lp-space-4);
  }
  .header-actions {
    justify-content: space-between;
  }
  .question-card {
    padding: var(--lp-space-4);
  }
  .true-false-list {
    grid-template-columns: 1fr;
  }
  .question-actions {
    flex-wrap: wrap;
  }
  .inline-error {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
