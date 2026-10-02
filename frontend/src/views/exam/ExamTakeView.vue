<template>
  <div class="exam-take-container">
    <LpStatePanel v-if="loading" state="loading" loading-label="正在恢复考试与剩余时间" />
    <LpStatePanel
      v-else-if="loadError"
      state="error"
      title="暂时无法恢复考试"
      :description="loadError"
      retry-label="重试恢复"
      @retry="load"
    >
      <template #retry
        ><el-button type="primary" @click="load">重试恢复</el-button
        ><el-button @click="leaveForRecords">返回考试记录</el-button></template
      >
    </LpStatePanel>
    <LpEmptyState
      v-else-if="!currentQuestion"
      title="这份试卷暂无可作答题目"
      description="请返回考试记录，核对试卷状态。"
    >
      <template #actions><el-button @click="leaveForRecords">返回考试记录</el-button></template>
    </LpEmptyState>
    <template v-else>
      <header class="take-header">
        <div class="take-header-title">
          <span class="section-kicker">{{ paperTitle }}</span>
          <h1 class="take-heading">考试进行中</h1>
        </div>
        <div class="take-header-progress">
          <span class="progress-text">{{ currentIndex + 1 }} / {{ questions.length }}</span
          ><LpProgress
            :percent="progressPercent"
            tone="primary"
            :label="`已作答 ${answeredCount} / ${questions.length} 题`"
          /><span class="answered-text">已答 {{ answeredCount }} / {{ questions.length }}</span>
        </div>
        <div class="take-header-right">
          <span
            :class="['countdown', { 'countdown-warn': remainSeconds < 300 }]"
            role="timer"
            aria-live="off"
            :aria-label="`剩余 ${countdownText}`"
            ><el-icon aria-hidden="true"><Timer /></el-icon>{{ countdownText }}</span
          ><el-button type="primary" :loading="submitted" :disabled="locked" @click="handleSubmit">提交试卷</el-button>
        </div>
      </header>
      <p class="exam-note">
        答案暂存在本页，刷新或离开后需重新作答；计时会继续。<span v-if="remainSeconds < 300" role="status"
          >剩余不足 5 分钟，请留意交卷时间。</span
        >
      </p>
      <div class="question-area">
        <article class="question-card" aria-labelledby="exam-question-title">
          <div v-if="currentQuestion.sectionTitle" class="q-section">{{ currentQuestion.sectionTitle }}</div>
          <div class="q-meta">
            <strong class="q-number">{{ currentQuestion.displayNumber || `第 ${currentIndex + 1} 题` }}</strong
            ><el-tag size="small" effect="plain">{{ getTypeLabel(currentQuestion.questionType) }}</el-tag
            ><span class="q-score">{{ currentQuestion.score }} 分</span>
          </div>
          <div id="exam-question-title" ref="questionTitle" class="q-content" tabindex="-1">
            <MarkdownRenderer :content="currentQuestion.content" />
          </div>
          <fieldset class="answer-options" :disabled="locked">
            <legend>作答</legend>
            <div
              v-if="currentQuestion.questionType === 'SINGLE_CHOICE'"
              class="option-list"
              role="radiogroup"
              aria-label="单选答案"
            >
              <label
                v-for="opt in currentQuestion.options"
                :key="opt.id"
                class="option-item"
                :class="{ selected: answers[currentQuestion.questionId] === opt.optionLabel }"
                ><input
                  v-model="answers[currentQuestion.questionId]"
                  type="radio"
                  name="exam-answer"
                  :value="opt.optionLabel"
                /><span class="opt-label">{{ opt.optionLabel }}</span
                ><span>{{ opt.content }}</span></label
              >
            </div>
            <div
              v-else-if="currentQuestion.questionType === 'MULTIPLE_CHOICE'"
              class="option-list"
              role="group"
              aria-label="多选答案"
            >
              <label
                v-for="opt in currentQuestion.options"
                :key="opt.id"
                class="option-item"
                :class="{ selected: isMultiSelected(currentQuestion.questionId, opt.optionLabel) }"
                ><input
                  type="checkbox"
                  :checked="isMultiSelected(currentQuestion.questionId, opt.optionLabel)"
                  @change="toggleMulti(currentQuestion.questionId, opt.optionLabel)"
                /><span class="opt-label">{{ opt.optionLabel }}</span
                ><span>{{ opt.content }}</span></label
              >
            </div>
            <div
              v-else-if="currentQuestion.questionType === 'TRUE_FALSE'"
              class="option-list tf-list"
              role="radiogroup"
              aria-label="判断答案"
            >
              <label
                v-for="opt in [
                  { value: 'TRUE', label: '正确' },
                  { value: 'FALSE', label: '错误' },
                ]"
                :key="opt.value"
                class="option-item"
                :class="{ selected: answers[currentQuestion.questionId] === opt.value }"
                ><input
                  v-model="answers[currentQuestion.questionId]"
                  type="radio"
                  name="exam-answer"
                  :value="opt.value"
                />{{ opt.label }}</label
              >
            </div>
            <el-input
              v-else
              v-model="answers[currentQuestion.questionId]"
              type="textarea"
              :rows="6"
              :aria-label="`${currentQuestion.displayNumber || `第 ${currentIndex + 1} 题`}答案`"
              placeholder="请输入答案"
            />
          </fieldset>
          <div v-if="submitError" class="submit-error" role="alert">
            <p>{{ submitError }}</p>
            <el-button :loading="submitted" :disabled="locked" @click="doSubmit">重试交卷</el-button>
          </div>
          <div class="nav-btns">
            <el-button :disabled="currentIndex === 0 || locked" @click="goTo(currentIndex - 1)">上一题</el-button
            ><el-button
              v-if="currentIndex < questions.length - 1"
              type="primary"
              :disabled="locked"
              @click="goTo(currentIndex + 1)"
              >下一题</el-button
            ><el-button v-else type="primary" :loading="submitted" :disabled="locked" @click="handleSubmit"
              >提交试卷</el-button
            >
          </div>
        </article>
        <aside class="answer-sheet" aria-label="答题导航">
          <h2 class="sheet-heading">答题卡</h2>
          <p class="sheet-summary">已答 {{ answeredCount }} · 未答 {{ questions.length - answeredCount }}</p>
          <div class="sheet-grid">
            <button
              v-for="(q, idx) in questions"
              :key="q.questionId"
              type="button"
              class="sheet-item"
              :class="{ answered: answers[q.questionId]?.trim(), current: idx === currentIndex }"
              :disabled="locked"
              :title="q.displayNumber || `第 ${idx + 1} 题`"
              :aria-label="`前往${q.displayNumber || `第 ${idx + 1} 题`}${answers[q.questionId]?.trim() ? '，已作答' : '，未作答'}`"
              :aria-current="idx === currentIndex ? 'step' : undefined"
              @click="goTo(idx)"
            >
              {{ q.displayNumber || idx + 1
              }}<span v-if="answers[q.questionId]?.trim()" class="sheet-check" aria-hidden="true">✓</span>
            </button>
          </div>
        </aside>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Timer } from '@element-plus/icons-vue'
import { useExamTakingSession } from './useExamTakingSession'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
const route = useRoute()
const recordId = computed(() => Number(route.params.recordId))
const questionTitle = ref<HTMLElement>()
const {
  loading,
  loadError,
  submitError,
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
  remainSeconds,
  countdownText,
  load,
  handleSubmit,
  doSubmit,
  leaveForRecords,
} = useExamTakingSession(recordId)
function getTypeLabel(type: string) {
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
function goTo(index: number) {
  if (!locked.value && index >= 0 && index < questions.value.length) currentIndex.value = index
}
watch(
  () => currentQuestion.value?.questionId,
  async () => {
    await nextTick()
    questionTitle.value?.scrollIntoView({ block: 'nearest', behavior: 'auto' })
    questionTitle.value?.focus({ preventScroll: true })
  },
)
</script>

<style scoped>
.exam-take-container {
  padding: var(--lp-space-6) var(--lp-space-4);
  max-width: var(--lp-container-max);
  margin: 0 auto;
}

.take-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lp-space-5);
  margin-bottom: var(--lp-space-5);
  padding: var(--lp-space-4) var(--lp-space-5);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-sm);
}

.take-header-title {
  min-width: 0;
}

.take-heading {
  margin: var(--lp-space-1) 0 0;
  color: var(--lp-text);
  font-size: var(--lp-text-xl);
  line-height: var(--lp-leading-tight);
}

.take-header-progress {
  display: flex;
  flex-direction: column;
  gap: var(--lp-space-2);
  flex: 1;
  min-width: 140px;
}

.progress-text {
  font-weight: var(--lp-weight-semibold);
  font-size: var(--lp-text-base);
  font-variant-numeric: tabular-nums;
}

.answered-text {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
  font-variant-numeric: tabular-nums;
}

.take-header-right {
  display: flex;
  align-items: center;
  gap: var(--lp-space-4);
  flex-shrink: 0;
}

.question-area {
  display: flex;
  gap: var(--lp-space-5);
  align-items: flex-start;
}

.question-card {
  flex: 1;
  min-width: 0;
  padding: var(--lp-space-6);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}

.q-meta {
  display: flex;
  align-items: center;
  gap: var(--lp-space-2);
  margin-bottom: var(--lp-space-3);
}

.q-section {
  margin-bottom: var(--lp-space-2);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  font-weight: var(--lp-weight-semibold);
}

.q-number {
  color: var(--lp-text);
  font-size: var(--lp-text-md);
}

.q-score {
  font-size: var(--lp-text-sm);
  color: var(--lp-text-muted);
}

.q-content {
  color: var(--lp-text);
  font-size: var(--lp-text-lg);
  line-height: var(--lp-leading-relaxed);
  margin-bottom: var(--lp-space-5);
  overflow-wrap: anywhere;
}

.option-list {
  display: flex;
  flex-direction: column;
  gap: var(--lp-space-3);
  margin-bottom: var(--lp-space-5);
}

.option-item {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  width: 100%;
  min-height: 48px;
  padding: var(--lp-space-3) var(--lp-space-4);
  color: var(--lp-text);
  text-align: left;
  font: inherit;
  background: var(--lp-surface);
  border: 1px solid var(--lp-border-strong);
  border-radius: var(--lp-radius-md);
  cursor: pointer;
  transition:
    background-color var(--lp-duration-fast) var(--lp-ease-out),
    border-color var(--lp-duration-fast) var(--lp-ease-out);
}

.option-item:hover {
  border-color: var(--lp-primary);
  background: var(--lp-surface-subtle);
}

.option-item:focus-within {
  outline: var(--lp-focus-width) solid var(--lp-focus-ring);
  outline-offset: 2px;
  border-color: var(--lp-primary);
}

.option-item.selected {
  border-color: var(--lp-primary);
  background: var(--lp-primary-soft);
}

.opt-label {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  flex: 0 0 auto;
  font-weight: var(--lp-weight-bold);
  color: var(--lp-primary);
  background: var(--lp-primary-soft);
  border-radius: var(--lp-radius-full);
}

.option-item.selected .opt-label {
  color: var(--lp-on-primary);
  background: var(--lp-primary);
}

.tf-list {
  flex-direction: row;
  gap: var(--lp-space-4);
}

.tf-list .option-item {
  flex: 1;
  justify-content: center;
  font-size: var(--lp-text-lg);
  font-weight: var(--lp-weight-semibold);
}

.nav-btns {
  display: flex;
  justify-content: center;
  gap: var(--lp-space-3);
  margin-top: var(--lp-space-5);
}

.answer-sheet {
  width: 280px;
  flex-shrink: 0;
  padding: var(--lp-space-4);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
  height: fit-content;
  position: sticky;
  top: var(--lp-space-5);
}

.sheet-heading {
  margin: 0 0 var(--lp-space-3);
  font-size: var(--lp-text-base);
}

.sheet-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--lp-space-2);
}

.sheet-item {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  min-height: 44px;
  position: relative;
  padding: 0 var(--lp-space-1);
  overflow: hidden;
  color: var(--lp-text);
  background: var(--lp-surface);
  border: 1px solid var(--lp-border-strong);
  border-radius: var(--lp-radius-xs);
  font: inherit;
  font-size: var(--lp-text-xs);
  white-space: nowrap;
  text-overflow: ellipsis;
  cursor: pointer;
  transition:
    background-color var(--lp-duration-fast) var(--lp-ease-out),
    color var(--lp-duration-fast) var(--lp-ease-out);
}

.sheet-item:hover {
  background: var(--lp-surface-soft);
}

.sheet-item:focus-visible {
  outline: var(--lp-focus-width) solid var(--lp-focus-ring);
  outline-offset: 2px;
}

.sheet-item.answered {
  background: var(--lp-primary);
  color: var(--lp-on-primary);
  border-color: var(--lp-primary);
}

.sheet-item.current {
  border-color: var(--lp-primary);
  outline: 2px solid var(--lp-primary);
  outline-offset: 2px;
}

.countdown {
  display: inline-flex;
  align-items: center;
  gap: var(--lp-space-1);
  font-size: var(--lp-text-lg);
  font-weight: var(--lp-weight-semibold);
  font-variant-numeric: tabular-nums;
  color: var(--lp-primary);
}

.countdown-warn {
  color: var(--lp-danger);
}

.exam-note {
  margin: 0 0 var(--lp-space-5);
  color: var(--lp-text-muted);
  font-size: var(--lp-text-sm);
  line-height: var(--lp-leading-body);
}
.exam-note span {
  margin-left: var(--lp-space-2);
  color: var(--lp-warning);
}
.answer-options {
  margin: 0;
  padding: 0;
  border: 0;
  min-width: 0;
}
.answer-options legend {
  margin-bottom: var(--lp-space-3);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}
.answer-options input {
  width: 16px;
  height: 16px;
  margin: 0;
  flex-shrink: 0;
  accent-color: var(--lp-primary);
}
.answer-options:disabled .option-item {
  cursor: default;
}
.sheet-summary {
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  margin: 0 0 var(--lp-space-4);
}
.sheet-check {
  position: absolute;
  bottom: 1px;
  right: 3px;
  font-size: 9px;
}
.submit-error {
  padding: var(--lp-space-4);
  margin-top: var(--lp-space-4);
  color: var(--lp-danger);
  background: var(--lp-surface-subtle);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
}
.submit-error p {
  margin: 0 0 var(--lp-space-3);
}
.q-content :deep(p:first-child) {
  margin-top: 0;
}
.q-content :deep(p:last-child) {
  margin-bottom: 0;
}

@media (max-width: 720px) {
  .exam-take-container {
    padding: 0;
  }

  .take-header {
    align-items: stretch;
    flex-direction: column;
    gap: var(--lp-space-3);
  }

  .take-header-progress {
    order: 2;
  }

  .take-header-right {
    justify-content: space-between;
  }

  .take-header-right .el-button {
    width: 100%;
    min-height: 44px;
  }

  .question-area {
    flex-direction: column;
    align-items: stretch;
  }

  .answer-sheet {
    position: static;
    width: auto;
    order: -1;
  }

  .sheet-grid {
    grid-template-columns: repeat(5, minmax(44px, 1fr));
  }

  .tf-list {
    gap: var(--lp-space-2);
  }

  .nav-btns {
    justify-content: stretch;
  }

  .nav-btns .el-button {
    flex: 1;
    min-height: 44px;
    margin-left: 0;
  }
}

@media (max-width: 420px) {
  .question-card {
    padding: var(--lp-space-4);
  }

  .q-meta {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .tf-list {
    flex-direction: column;
  }
}

@media (prefers-reduced-motion: reduce) {
  .countdown-warn {
    animation: none;
  }

  .option-item {
    transition: none;
  }
}
</style>
