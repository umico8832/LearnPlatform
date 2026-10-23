<script setup lang="ts">
import { computed } from 'vue'
import { LtButton } from '@/components/living'
import type { ExamQuestionItem } from '@/api/exam'

/** 限时考试右栏：剩余时间与交卷、答题卡、草稿说明。只显示是否已答，不提示对错。 */
const props = defineProps<{
  questions: ExamQuestionItem[]
  currentIndex: number
  answers: Record<number, string>
  answeredCount: number
  progressPercent: number
  countdownText: string
  remainSeconds: number
  submitted: boolean
  locked: boolean
  draftStorageError: string
}>()

const emit = defineEmits<{ submit: []; go: [index: number] }>()

const warn = computed(() => props.remainSeconds < 300)
const unanswered = computed(() => props.questions.length - props.answeredCount)

function numberOf(index: number) {
  return props.questions[index]?.displayNumber || `第 ${index + 1} 题`
}

function isAnswered(questionId: number) {
  return Boolean(props.answers[questionId]?.trim())
}
</script>

<template>
  <aside class="exam-panel" aria-label="答题导航">
    <div class="exam-clock" :data-warn="warn || undefined">
      <span class="clock-label">剩余时间</span>
      <span class="countdown" role="timer" aria-live="off" :aria-label="`剩余 ${countdownText}`">
        {{ countdownText }}
      </span>
      <p v-if="warn" class="clock-warn" role="status">剩余不足 5 分钟，请留意交卷时间。</p>
      <LtButton class="clock-submit" :disabled="locked" @click="emit('submit')">
        {{ submitted ? '正在交卷…' : '提交试卷' }}
      </LtButton>
    </div>

    <section class="sheet" aria-labelledby="exam-sheet-heading">
      <div class="sheet-head">
        <h2 id="exam-sheet-heading" class="sheet-heading">答题卡</h2>
        <span class="sheet-summary">已答 {{ answeredCount }} · 未答 {{ unanswered }}</span>
      </div>
      <div class="sheet-meter" aria-hidden="true">
        <span class="sheet-meter-fill" :style="{ width: `${progressPercent}%` }" />
      </div>
      <div class="sheet-grid">
        <button
          v-for="(q, idx) in questions"
          :key="q.questionId"
          type="button"
          class="sheet-item"
          :class="{ answered: isAnswered(q.questionId), current: idx === currentIndex }"
          :disabled="locked"
          :title="numberOf(idx)"
          :aria-label="`前往${numberOf(idx)}${isAnswered(q.questionId) ? '，已作答' : '，未作答'}`"
          :aria-current="idx === currentIndex ? 'step' : undefined"
          @click="emit('go', idx)"
        >
          {{ q.displayNumber || idx + 1 }}
        </button>
      </div>
      <p class="sheet-legend" aria-hidden="true">
        <span class="legend-item"><span class="legend-swatch" data-kind="answered" />已答</span>
        <span class="legend-item"><span class="legend-swatch" />未答</span>
        <span class="legend-item"><span class="legend-swatch" data-kind="current" />当前</span>
      </p>
    </section>

    <p class="exam-note">
      答案暂存于当前标签页，刷新可继续作答；离开或交卷后清除。计时不会暂停。
      <span v-if="draftStorageError" class="exam-note-warn" role="status">{{ draftStorageError }}</span>
    </p>
  </aside>
</template>

<style scoped>
.exam-panel {
  position: sticky;
  top: calc(var(--focus-bar-height, 0px) + var(--lt-space-5));
  display: grid;
  gap: var(--lt-space-4);
}

.exam-clock {
  display: grid;
  padding: var(--lt-space-5);
  border-radius: var(--lt-radius-lg);
  background: var(--lt-surface);
  box-shadow: var(--lt-shadow-card);
}

.clock-label {
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
  font-weight: 600;
}

.countdown {
  margin-top: var(--lt-space-1);
  font: 600 var(--lt-text-title1) / 1.1 var(--lt-font-rounded);
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.01em;
  transition: color var(--lt-dur-fade) var(--lt-ease-fade);
}

.exam-clock[data-warn] .countdown {
  color: var(--lt-orange-text);
}

.clock-warn {
  margin: var(--lt-space-2) 0 0;
  color: var(--lt-orange-text);
  font-size: var(--lt-text-footnote);
}

.clock-submit {
  width: 100%;
  margin-top: var(--lt-space-4);
}

.sheet {
  padding: var(--lt-space-5);
  border-radius: var(--lt-radius-lg);
  background: var(--lt-surface);
  box-shadow: var(--lt-shadow-card);
}

.sheet-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--lt-space-3);
}

.sheet-heading {
  margin: 0;
  font-size: var(--lt-text-headline);
}

.sheet-summary {
  color: var(--lt-label-2);
  font: 500 var(--lt-text-footnote) / 1 var(--lt-font-rounded);
  font-variant-numeric: tabular-nums;
}

.sheet-meter {
  height: 4px;
  margin: var(--lt-space-3) 0 var(--lt-space-4);
  overflow: hidden;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
}

.sheet-meter-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--lt-accent);
  transition: width var(--lt-dur-layout) var(--lt-ease-layout);
}

.sheet-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--lt-space-2);
}

.sheet-item {
  display: grid;
  place-items: center;
  min-width: 0;
  height: 40px;
  padding: 0 2px;
  overflow: hidden;
  border: 0;
  border-radius: var(--lt-radius-sm);
  background: var(--lt-fill-3);
  color: var(--lt-label-2);
  font: 500 var(--lt-text-footnote) / 1 var(--lt-font-rounded);
  font-variant-numeric: tabular-nums;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  transition:
    background-color var(--lt-dur-fade) var(--lt-ease-fade),
    color var(--lt-dur-fade) var(--lt-ease-fade),
    box-shadow var(--lt-dur-fade) var(--lt-ease-fade);
}

.sheet-item.answered {
  background: var(--lt-accent-soft);
  color: var(--lt-label);
  font-weight: 600;
}

.sheet-item.current {
  box-shadow: inset 0 0 0 1.5px var(--lt-accent);
  color: var(--lt-label);
}

.sheet-item:focus-visible {
  outline: 2px solid var(--lt-accent);
  outline-offset: 2px;
}

.sheet-item:disabled {
  cursor: default;
  opacity: 0.6;
}

@media (hover: hover) and (pointer: fine) {
  .sheet-item:not(.answered):not(:disabled):hover {
    background: var(--lt-fill-2);
    color: var(--lt-label);
  }
}

.sheet-legend {
  display: flex;
  gap: var(--lt-space-4);
  margin: var(--lt-space-4) 0 0;
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.legend-swatch {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  background: var(--lt-fill-2);
}

.legend-swatch[data-kind='answered'] {
  background: var(--lt-accent-soft);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--lt-accent) 40%, transparent);
}

.legend-swatch[data-kind='current'] {
  background: transparent;
  box-shadow: inset 0 0 0 1.5px var(--lt-accent);
}

.exam-note {
  margin: 0;
  padding: 0 var(--lt-space-2);
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
  line-height: 1.6;
}

.exam-note-warn {
  display: block;
  margin-top: var(--lt-space-1);
  color: var(--lt-orange-text);
}
</style>
