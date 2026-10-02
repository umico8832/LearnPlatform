<script setup lang="ts">
import { computed, ref } from 'vue'
import AnswerRewardFeedback from '@/components/gamification/AnswerRewardFeedback.vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import AiQuestionAssistant from '@/components/AiQuestionAssistant.vue'
import QuestionLearningAsset from '@/components/QuestionLearningAsset.vue'
import type { PracticeResultVO } from '@/api/practice'
import { isGradedPracticeResult, practiceResultTitle } from './practiceSessionPresentation'

const props = defineProps<{ result: PracticeResultVO; wrongPractice?: boolean }>()
const graded = computed(() => isGradedPracticeResult(props.result))
const title = computed(() => practiceResultTitle(props.result))
const understandingOpen = ref(false)
</script>

<template>
  <section
    class="practice-answer-feedback"
    :class="{ 'is-correct': result.correct === true, 'is-wrong': result.correct === false }"
    aria-live="polite"
    data-testid="practice-feedback"
  >
    <p class="practice-answer-feedback__eyebrow">本题反馈</p>
    <h2>{{ title }}</h2>
    <p v-if="!graded" class="practice-answer-feedback__pending">答案已记录，尚未判分，不计入正确率。</p>
    <dl v-else class="practice-answer-feedback__answers">
      <div>
        <dt>你的答案</dt>
        <dd>{{ result.userAnswer || '未填写' }}</dd>
      </div>
      <div v-if="result.correct === false">
        <dt>参考答案</dt>
        <dd>{{ result.correctAnswer || '待补充' }}</dd>
      </div>
    </dl>
    <p v-if="wrongPractice && result.correct === true" class="practice-answer-feedback__resolved">
      这道错题已移出错题本。
    </p>
    <div v-if="result.analysis" class="practice-answer-feedback__analysis">
      <h3>解析</h3>
      <MarkdownRenderer :content="result.analysis" />
    </div>
    <details
      class="practice-answer-feedback__understand"
      @toggle="understandingOpen = ($event.currentTarget as HTMLDetailsElement).open"
    >
      <summary>继续理解这道题</summary>
      <AiQuestionAssistant v-if="understandingOpen" :question-id="result.questionId" />
      <QuestionLearningAsset
        v-if="understandingOpen && result.correct === false"
        :question-id="result.questionId"
        collapsible
      />
    </details>
    <AnswerRewardFeedback v-if="result.reward" :reward="result.reward" />
  </section>
</template>

<style scoped>
.practice-answer-feedback {
  display: grid;
  gap: var(--lp-space-3);
  margin-top: var(--lp-space-6);
  padding: var(--lp-space-5);
  border: var(--lp-border-hairline);
  border-inline-start: 3px solid var(--lp-primary);
  border-radius: var(--lp-radius-lg);
  background: var(--lp-surface-subtle);
}
.practice-answer-feedback.is-correct {
  border-inline-start-color: var(--lp-success);
}
.practice-answer-feedback.is-wrong {
  border-inline-start-color: var(--lp-danger);
}
.practice-answer-feedback__eyebrow,
.practice-answer-feedback h2,
.practice-answer-feedback h3,
.practice-answer-feedback p {
  margin: 0;
}
.practice-answer-feedback__eyebrow {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
}
.practice-answer-feedback h2 {
  color: var(--lp-text);
  font-size: var(--lp-text-xl);
}
.practice-answer-feedback__pending,
.practice-answer-feedback__resolved {
  color: var(--lp-text-secondary);
  line-height: var(--lp-leading-body);
}
.practice-answer-feedback__answers {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--lp-space-3);
  margin: 0;
}
.practice-answer-feedback dt {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
}
.practice-answer-feedback dd {
  margin: var(--lp-space-1) 0 0;
  color: var(--lp-text);
  overflow-wrap: anywhere;
}
.practice-answer-feedback__analysis {
  display: grid;
  gap: var(--lp-space-2);
  padding-top: var(--lp-space-4);
  border-top: var(--lp-border-hairline);
}
.practice-answer-feedback__analysis h3 {
  color: var(--lp-text);
  font-size: var(--lp-text-base);
}
.practice-answer-feedback__analysis :deep(.markdown-body) {
  color: var(--lp-text-secondary);
  line-height: var(--lp-leading-relaxed);
  white-space: pre-wrap;
}
.practice-answer-feedback__understand {
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}
.practice-answer-feedback__understand summary {
  cursor: pointer;
  color: var(--lp-primary);
  font-weight: var(--lp-weight-semibold);
}
</style>
