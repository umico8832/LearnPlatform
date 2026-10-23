<script setup lang="ts">
import { computed, ref } from 'vue'
import { ChevronDown } from '@lucide/vue'
import MarkdownRenderer from '@/views/question/QuestionProse.vue'
import QuestionLearningAsset from './learning/QuestionLearningAsset.vue'
import type { PracticeResultVO } from '@/api/practice'
import { isGradedPracticeResult, practiceResultTitle } from './practiceSessionPresentation'
import PracticeAiAssistant from './PracticeAiAssistant.vue'
import PracticeRewardFeedback from './PracticeRewardFeedback.vue'

const props = defineProps<{ result: PracticeResultVO; wrongPractice?: boolean }>()
const graded = computed(() => isGradedPracticeResult(props.result))
const title = computed(() => practiceResultTitle(props.result))
const feedbackStatus = computed(() =>
  !graded.value ? '答案已记录，尚未判分。' : props.result.correct ? '本题回答正确。' : '本题回答错误。',
)
const understandingOpen = ref(false)
</script>

<template>
  <section
    class="feedback practice-answer-feedback"
    :class="{ correct: result.correct === true, wrong: result.correct === false }"
    data-testid="practice-feedback"
  >
    <p class="lt-sr-only" role="status" aria-atomic="true" data-testid="practice-feedback-status">
      {{ feedbackStatus }}
    </p>
    <h2 class="verdict"><span class="verdict-dot" aria-hidden="true" />{{ title }}</h2>
    <p v-if="!graded" class="note">答案已记录，尚未判分，不计入正确率。</p>
    <div v-else class="answers">
      <p class="answer-chip" :data-tone="result.correct ? 'green' : 'orange'">
        <span class="answer-label">你的答案</span><span class="answer-value">{{ result.userAnswer || '未填写' }}</span>
      </p>
      <p v-if="result.correct === false" class="answer-chip" data-tone="green">
        <span class="answer-label">参考答案</span
        ><span class="answer-value">{{ result.correctAnswer || '待补充' }}</span>
      </p>
    </div>
    <p v-if="wrongPractice && result.correct === true" class="resolved">这道错题已移出错题本。</p>
    <div v-if="result.analysis" class="analysis practice-answer-feedback__analysis">
      <h3>解析</h3>
      <MarkdownRenderer :content="result.analysis" />
    </div>
    <details class="understand" @toggle="understandingOpen = ($event.currentTarget as HTMLDetailsElement).open">
      <summary>继续理解这道题 <ChevronDown aria-hidden="true" /></summary>
      <PracticeAiAssistant v-if="understandingOpen" :question-id="result.questionId" /><QuestionLearningAsset
        v-if="understandingOpen && result.correct === false"
        :question-id="result.questionId"
        collapsible
      />
    </details>
    <PracticeRewardFeedback v-if="result.reward" :reward="result.reward" />
  </section>
</template>

<style scoped>
.feedback {
  display: grid;
  gap: var(--lt-space-4);
  margin: 0 var(--lt-space-6);
  padding: 0 0 var(--lt-space-5);
}
.feedback h2,
.feedback h3,
.feedback p {
  margin: 0;
}
.verdict {
  display: flex;
  align-items: center;
  gap: var(--lt-space-2);
  font-size: var(--lt-text-body);
  font-weight: 600;
}
.verdict-dot {
  flex: none;
  width: var(--lt-space-2);
  height: var(--lt-space-2);
  border-radius: var(--lt-radius-pill);
  background: var(--lt-accent);
}
.correct .verdict {
  color: var(--lt-green-text);
}
.correct .verdict-dot {
  background: var(--lt-green);
}
.wrong .verdict {
  color: var(--lt-orange-text);
}
.wrong .verdict-dot {
  background: var(--lt-orange);
}
.note,
.resolved {
  color: var(--lt-label-2);
  line-height: var(--lt-leading-reading);
}
.answers {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lt-space-3);
}
.answer-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--lt-space-2);
  max-width: 100%;
  padding: var(--lt-space-1) var(--lt-space-3) var(--lt-space-1) var(--lt-space-1);
  border-radius: var(--lt-radius-pill);
  font-size: var(--lt-text-footnote);
  line-height: var(--lt-leading-ui);
}
.answer-chip[data-tone='orange'] {
  background: var(--lt-orange-soft);
  color: var(--lt-orange-text);
}
.answer-chip[data-tone='green'] {
  background: var(--lt-green-soft);
  color: var(--lt-green-text);
}
.answer-label {
  flex: none;
  padding-left: var(--lt-space-2);
  font-weight: 600;
}
.answer-value {
  min-width: var(--lt-space-5);
  padding: var(--lt-space-1);
  border-radius: var(--lt-radius-pill);
  color: var(--lt-on-tint);
  text-align: center;
  overflow-wrap: anywhere;
  font-family: var(--lt-font-rounded);
  font-variant-numeric: tabular-nums;
}
.answer-chip[data-tone='orange'] .answer-value {
  background: var(--lt-orange);
}
.answer-chip[data-tone='green'] .answer-value {
  background: var(--lt-green);
}
.analysis {
  font-size: var(--lt-text-reading);
  line-height: var(--lt-leading-reading);
}
.analysis h3 {
  margin-bottom: var(--lt-space-2);
  font-size: var(--lt-text-body);
}
.understand {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.understand summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--lt-accent);
  cursor: pointer;
}
.understand summary svg {
  width: var(--lt-space-4);
}
.understand[open] summary svg {
  transform: rotate(180deg);
}
@media (max-width: 640px) {
  .feedback {
    margin: 0 var(--lt-space-3);
  }
}
</style>
