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
    <p class="eyebrow">本题反馈</p>
    <h2>{{ title }}</h2>
    <p v-if="!graded" class="note">答案已记录，尚未判分，不计入正确率。</p>
    <dl v-else class="answers">
      <div>
        <dt>你的答案</dt>
        <dd>{{ result.userAnswer || '未填写' }}</dd>
      </div>
      <div v-if="result.correct === false">
        <dt>参考答案</dt>
        <dd>{{ result.correctAnswer || '待补充' }}</dd>
      </div>
    </dl>
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
  padding: var(--lt-space-5);
  border-radius: var(--lt-radius-lg);
  background: var(--lt-fill-2);
  box-shadow: inset var(--lt-space-1) 0 var(--lt-accent-fill);
}
.feedback.correct {
  box-shadow: inset var(--lt-space-1) 0 var(--lt-green);
}
.feedback.wrong {
  box-shadow: inset var(--lt-space-1) 0 var(--lt-orange);
}
.eyebrow,
.feedback h2,
.feedback h3,
.feedback p {
  margin: 0;
}
.eyebrow {
  color: var(--lt-label-2);
  font-size: var(--lt-text-caption);
}
.feedback h2 {
  font-size: var(--lt-text-title3);
}
.note,
.resolved {
  color: var(--lt-label-2);
  line-height: var(--lt-leading-reading);
}
.answers {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--lt-space-3);
  margin: 0;
}
.answers div {
  padding: var(--lt-space-3);
  border-radius: var(--lt-radius-md);
  background: var(--lt-surface);
}
.answers dt {
  color: var(--lt-label-2);
  font-size: var(--lt-text-caption);
}
.answers dd {
  margin: var(--lt-space-1) 0 0;
  overflow-wrap: anywhere;
}
.analysis {
  padding-top: var(--lt-space-4);
  border-top: solid var(--lt-hairline);
}
.analysis h3 {
  margin-bottom: var(--lt-space-2);
  font-size: var(--lt-text-body);
}
.analysis :deep(.markdown-body) {
  color: var(--lt-label-2);
  line-height: var(--lt-leading-reading);
  white-space: pre-wrap;
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
  .answers {
    grid-template-columns: 1fr;
  }
}
</style>
