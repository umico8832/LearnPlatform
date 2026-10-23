<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ChevronLeft, RotateCcw } from '@lucide/vue'
import { LtButton, LtSheet } from '@/components/living'
import { springAnimate, springs } from '@/motion'
import QuestionProse from '@/views/question/QuestionProse.vue'
import ExamChoices from './ExamChoices.vue'
import ExamPanel from './ExamPanel.vue'
import { leaveMessage } from './useExamLeaveGuard'
import { useExamTakingSession } from './useExamTakingSession'

/**
 * 限时考试答题页（Phase 28 新基线，沉浸外框内）。左边是当前这道题，右边一列固定显示剩余时间、
 * 交卷和答题卡。交卷前不出现任何对错提示；交卷与离开都先在弹层里确认。
 */
defineOptions({ name: 'ExamTakeView' })

const route = useRoute()
const recordId = computed(() => Number(route.params.recordId))
const questionTitle = ref<HTMLElement>()
const questionBody = ref<HTMLElement>()
const {
  loading,
  loadError,
  submitError,
  draftStorageError,
  paperTitle,
  questions,
  currentIndex,
  currentQuestion,
  answers,
  answeredCount,
  progressPercent,
  isMultiSelected,
  toggleMulti,
  submitted,
  locked,
  submitSheet,
  leaveSheet,
  remainSeconds,
  countdownText,
  load,
  handleSubmit,
  doSubmit,
  leaveForRecords,
} = useExamTakingSession(recordId)

const submitOpen = submitSheet.open
const leaveOpen = leaveSheet.open

const TYPE_LABELS: Record<string, string> = {
  SINGLE_CHOICE: '单选题',
  MULTIPLE_CHOICE: '多选题',
  TRUE_FALSE: '判断题',
  FILL_BLANK: '填空题',
  SHORT_ANSWER: '简答题',
}
const TRUE_FALSE = [
  { key: '', value: 'TRUE', content: '正确' },
  { key: '', value: 'FALSE', content: '错误' },
]

const totalScore = computed(() => questions.value.reduce((sum, question) => sum + (question.score || 0), 0))
const unanswered = computed(() => questions.value.length - answeredCount.value)
const isLast = computed(() => currentIndex.value >= questions.value.length - 1)

const choiceOptions = computed(() => {
  const question = currentQuestion.value
  if (!question) return []
  if (question.questionType === 'TRUE_FALSE') return TRUE_FALSE
  return question.options.map((option) => ({
    key: option.optionLabel,
    value: option.optionLabel,
    content: option.content,
  }))
})

function numberOf(index: number) {
  return questions.value[index]?.displayNumber || `第 ${index + 1} 题`
}

function isSelected(value: string) {
  const question = currentQuestion.value
  if (!question) return false
  if (question.questionType === 'MULTIPLE_CHOICE') return isMultiSelected(question.questionId, value)
  return answers.value[question.questionId] === value
}

function pick(value: string) {
  const question = currentQuestion.value
  if (!question || locked.value) return
  if (question.questionType === 'MULTIPLE_CHOICE') toggleMulti(question.questionId, value)
  else answers.value[question.questionId] = value
}

function goTo(index: number) {
  if (!locked.value && index >= 0 && index < questions.value.length) currentIndex.value = index
}

// 换题时新题从前进方向轻轻滑入，看得出是往前还是往回翻。
watch(
  () => currentQuestion.value?.questionId,
  async (_id, previousId) => {
    const before = questions.value.findIndex((question) => question.questionId === previousId)
    await nextTick()
    questionTitle.value?.scrollIntoView({ block: 'nearest', behavior: 'auto' })
    questionTitle.value?.focus({ preventScroll: true })
    if (before < 0 || !questionBody.value) return
    const from = currentIndex.value > before ? 16 : -16
    springAnimate(
      questionBody.value,
      [
        { transform: `translate3d(${from}px, 0, 0)`, opacity: 0 },
        { transform: 'translate3d(0, 0, 0)', opacity: 1 },
      ],
      springs.layout,
    )
  },
)
</script>

<template>
  <div class="exam-take">
    <div v-if="loading" class="layout" aria-busy="true">
      <div class="paper">
        <div class="paper-head" aria-hidden="true">
          <span class="skeleton" style="width: 280px; height: 28px" />
          <span class="skeleton" style="width: 140px; height: 14px; margin-top: 12px" />
        </div>
        <div class="question-card" aria-hidden="true">
          <span class="skeleton" style="width: 120px; height: 16px" />
          <span class="skeleton" style="width: 86%; height: 18px; margin-top: 24px" />
          <span class="skeleton" style="width: 64%; height: 18px; margin-top: 12px" />
          <span v-for="n in 4" :key="n" class="skeleton" style="height: 52px; margin-top: 10px" />
        </div>
      </div>
      <div class="exam-panel" aria-hidden="true">
        <span class="skeleton" style="width: 100%; height: 148px; border-radius: var(--lt-radius-lg)" />
      </div>
      <span class="sr-only" role="status">正在恢复考试与剩余时间</span>
    </div>

    <div v-else-if="loadError" class="state">
      <h1 class="state-title">暂时无法恢复考试</h1>
      <p class="state-text">{{ loadError }}</p>
      <div class="state-actions">
        <LtButton variant="primary" @click="load">
          <template #icon><RotateCcw aria-hidden="true" /></template>
          重试恢复
        </LtButton>
        <LtButton @click="leaveForRecords">返回考试记录</LtButton>
      </div>
    </div>

    <div v-else-if="!currentQuestion" class="state">
      <h1 class="state-title">这份试卷暂无可作答题目</h1>
      <p class="state-text">请返回考试记录，核对试卷状态。</p>
      <div class="state-actions">
        <LtButton @click="leaveForRecords">返回考试记录</LtButton>
      </div>
    </div>

    <div v-else class="layout">
      <div class="paper">
        <header class="paper-head">
          <h1 class="paper-title">{{ paperTitle }}</h1>
          <p class="paper-meta">
            <span class="progress-text">第 {{ currentIndex + 1 }} / {{ questions.length }} 题</span>
            <span aria-hidden="true">·</span>
            <span>共 {{ questions.length }} 题，满分 {{ totalScore }} 分</span>
          </p>
        </header>

        <article class="question-card question-area" aria-labelledby="exam-question-title">
          <div ref="questionBody" class="question-body">
            <p v-if="currentQuestion.sectionTitle" class="q-section">{{ currentQuestion.sectionTitle }}</p>
            <div class="q-meta">
              <strong class="q-number">{{ numberOf(currentIndex) }}</strong>
              <span class="q-type">{{
                TYPE_LABELS[currentQuestion.questionType] || currentQuestion.questionType
              }}</span>
              <span class="q-score">{{ currentQuestion.score }} 分</span>
            </div>
            <div id="exam-question-title" ref="questionTitle" class="q-content" tabindex="-1">
              <QuestionProse :content="currentQuestion.content" />
            </div>
            <fieldset class="answer-options" :disabled="locked">
              <legend class="sr-only">作答</legend>
              <ExamChoices
                v-if="choiceOptions.length"
                :key="currentQuestion.questionId"
                :options="choiceOptions"
                :multiple="currentQuestion.questionType === 'MULTIPLE_CHOICE'"
                :binary="currentQuestion.questionType === 'TRUE_FALSE'"
                :label="
                  currentQuestion.questionType === 'MULTIPLE_CHOICE'
                    ? '多选答案'
                    : currentQuestion.questionType === 'TRUE_FALSE'
                      ? '判断答案'
                      : '单选答案'
                "
                name="exam-answer"
                :is-selected="isSelected"
                @pick="pick"
              />
              <textarea
                v-else
                v-model="answers[currentQuestion.questionId]"
                class="answer-text"
                rows="7"
                :aria-label="`${numberOf(currentIndex)}答案`"
                placeholder="在这里写下你的答案"
              />
            </fieldset>
          </div>

          <div v-if="submitError" class="submit-error" role="alert">
            <p>{{ submitError }}</p>
            <LtButton size="sm" :disabled="locked" @click="doSubmit">重试交卷</LtButton>
          </div>

          <div class="nav-btns">
            <LtButton :disabled="currentIndex === 0 || locked" @click="goTo(currentIndex - 1)">
              <template #icon><ChevronLeft aria-hidden="true" /></template>
              上一题
            </LtButton>
            <LtButton v-if="!isLast" variant="primary" :disabled="locked" @click="goTo(currentIndex + 1)">
              下一题
            </LtButton>
            <LtButton v-else variant="primary" :disabled="locked" @click="handleSubmit">
              {{ submitted ? '正在交卷…' : '提交试卷' }}
            </LtButton>
          </div>
        </article>
      </div>

      <ExamPanel
        :questions="questions"
        :current-index="currentIndex"
        :answers="answers"
        :answered-count="answeredCount"
        :progress-percent="progressPercent"
        :countdown-text="countdownText"
        :remain-seconds="remainSeconds"
        :submitted="submitted"
        :locked="locked"
        :draft-storage-error="draftStorageError"
        @submit="handleSubmit"
        @go="goTo"
      />
    </div>

    <LtSheet v-model:open="submitOpen" title="提交确认" :width="440">
      <p class="sheet-text">
        {{
          unanswered
            ? `还有 ${unanswered} 题未作答。确定提交试卷？提交后不可修改。`
            : '已完成全部作答。确定提交试卷？提交后不可修改。'
        }}
      </p>
      <div class="sheet-actions">
        <LtButton @click="submitSheet.answer(false)">继续作答</LtButton>
        <LtButton variant="primary" @click="submitSheet.answer(true)">提交试卷</LtButton>
      </div>
    </LtSheet>

    <LtSheet v-model:open="leaveOpen" title="离开考试" :width="440">
      <p class="sheet-text">{{ leaveMessage }}</p>
      <div class="sheet-actions">
        <LtButton @click="leaveSheet.answer(true)">离开考试</LtButton>
        <LtButton variant="primary" @click="leaveSheet.answer(false)">继续考试</LtButton>
      </div>
    </LtSheet>
  </div>
</template>

<style scoped>
.exam-take {
  --panel-width: 296px;
}

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) var(--panel-width);
  gap: var(--lt-space-7);
  align-items: start;
}

.paper {
  min-width: 0;
}

.paper-head {
  display: grid;
  padding: var(--lt-space-3) 0 var(--lt-space-6);
}

.paper-title {
  margin: 0;
  font-size: var(--lt-text-title2);
  line-height: var(--lt-leading-tight);
  letter-spacing: var(--lt-tracking-title);
  overflow-wrap: anywhere;
}

.paper-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lt-space-2);
  margin: var(--lt-space-2) 0 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-subhead);
  font-variant-numeric: tabular-nums;
}

.progress-text {
  color: var(--lt-label);
  font-family: var(--lt-font-rounded);
  font-weight: 600;
}

.question-card {
  display: grid;
  padding: var(--lt-space-7) var(--lt-space-7) var(--lt-space-6);
  border-radius: var(--lt-radius-xl);
  background: var(--lt-surface);
  box-shadow: var(--lt-shadow-card);
}

.question-body {
  min-width: 0;
}

.q-section {
  margin: 0 0 var(--lt-space-3);
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
  font-weight: 600;
}

.q-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--lt-space-2) var(--lt-space-3);
  margin-bottom: var(--lt-space-4);
}

.q-number {
  font: 600 var(--lt-text-headline) / 1.2 var(--lt-font-rounded);
  font-variant-numeric: tabular-nums;
}

.q-type {
  padding: 3px 10px;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
  color: var(--lt-label-2);
  font-size: var(--lt-text-caption);
  font-weight: 600;
}

.q-score {
  margin-left: auto;
  color: var(--lt-label-3);
  font: 500 var(--lt-text-footnote) / 1 var(--lt-font-rounded);
  font-variant-numeric: tabular-nums;
}

.q-content {
  margin-bottom: var(--lt-space-6);
  outline: none;
}

.answer-options {
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
}

.answer-text {
  display: block;
  width: 100%;
  min-height: 168px;
  padding: 14px 16px;
  border: 0;
  border-radius: var(--lt-radius-md);
  background: var(--lt-fill-3);
  box-shadow: inset 0 0 0 0.5px var(--lt-separator);
  color: var(--lt-label);
  font: inherit;
  font-size: var(--lt-text-reading);
  line-height: var(--lt-leading-reading);
  resize: vertical;
  transition: box-shadow var(--lt-dur-fade) var(--lt-ease-fade);
}

.answer-text::placeholder {
  color: var(--lt-label-3);
}

.answer-text:focus-visible {
  outline: none;
  box-shadow: inset 0 0 0 1.5px var(--lt-accent);
}

.answer-text:disabled {
  opacity: 0.6;
}

.submit-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lt-space-4);
  margin-top: var(--lt-space-5);
  padding: var(--lt-space-3) var(--lt-space-3) var(--lt-space-3) var(--lt-space-4);
  border-radius: var(--lt-radius-md);
  background: var(--lt-red-soft);
  color: var(--lt-red-text);
  font-size: var(--lt-text-subhead);
}

.submit-error p {
  margin: 0;
}

.nav-btns {
  display: flex;
  justify-content: space-between;
  gap: var(--lt-space-3);
  margin-top: var(--lt-space-7);
  padding-top: var(--lt-space-5);
  border-top: 0.5px solid var(--lt-separator);
}

.state {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--lt-space-3);
  max-width: 560px;
  margin: 0 auto;
  padding: var(--lt-space-10) 0;
}

.state-title {
  margin: 0;
  font-size: var(--lt-text-title3);
}

.state-text {
  margin: 0 0 var(--lt-space-2);
  color: var(--lt-label-2);
}

.state-actions {
  display: flex;
  gap: var(--lt-space-3);
}

.sheet-text {
  margin: var(--lt-space-2) 0 0;
  color: var(--lt-label-2);
  line-height: 1.6;
}

.sheet-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--lt-space-3);
  margin-top: var(--lt-space-6);
}

.skeleton {
  display: block;
  border-radius: 8px;
  background: var(--lt-fill-2);
  animation: pending 1.4s ease-in-out infinite alternate;
}

@keyframes pending {
  to {
    opacity: 0.45;
  }
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
</style>
