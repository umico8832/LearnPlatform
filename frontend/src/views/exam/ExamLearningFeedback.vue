<script setup lang="ts">
import { computed } from 'vue'
import AnswerRewardFeedback from '@/components/gamification/AnswerRewardFeedback.vue'
import type { ExamLearningAnswerResultVO } from '@/api/exam'

const props = defineProps<{ result: ExamLearningAnswerResultVO }>()
const title = computed(() => {
  if (props.result.correct === null) return '答案已保存，供你对照参考答案自评'
  return props.result.correct ? '回答正确' : '回答错误'
})
</script>

<template>
  <section
    class="exam-learning-feedback"
    :class="{
      'is-correct': result.correct === true,
      'is-wrong': result.correct === false,
      'is-self-review': result.correct === null,
    }"
    tabindex="-1"
    aria-live="polite"
  >
    <p class="exam-learning-feedback__eyebrow">本题反馈</p>
    <h3>{{ title }}</h3>
    <p v-if="result.correct === null" class="exam-learning-feedback__pending">已记录本次作答，不计入自动判分正确率。</p>
    <dl class="exam-learning-feedback__answers">
      <div>
        <dt>你的答案</dt>
        <dd>{{ result.userAnswer || '未填写' }}</dd>
      </div>
      <div v-if="(result.correct === false || result.correct === null) && result.correctAnswer">
        <dt>参考答案</dt>
        <dd>{{ result.correctAnswer }}</dd>
      </div>
    </dl>
    <div v-if="result.analysis" class="exam-learning-feedback__analysis">
      <h4>解析</h4>
      <p>{{ result.analysis }}</p>
    </div>
    <AnswerRewardFeedback v-if="result.reward" :reward="result.reward" />
  </section>
</template>

<style scoped>
.exam-learning-feedback {
  display: grid;
  gap: var(--lp-space-3);
  margin-top: var(--lp-space-5);
  padding: var(--lp-space-5);
  border: var(--lp-border-hairline);
  border-inline-start: 3px solid var(--lp-primary);
  border-radius: var(--lp-radius-lg);
  background: var(--lp-surface-subtle);
}
.exam-learning-feedback.is-correct {
  border-inline-start-color: var(--lp-success);
}
.exam-learning-feedback.is-wrong {
  border-inline-start-color: var(--lp-danger);
}
.exam-learning-feedback.is-self-review {
  border-inline-start-color: var(--lp-info);
}
.exam-learning-feedback__eyebrow,
.exam-learning-feedback h3,
.exam-learning-feedback h4,
.exam-learning-feedback p {
  margin: 0;
}
.exam-learning-feedback__eyebrow,
.exam-learning-feedback dt {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
}
.exam-learning-feedback h3 {
  color: var(--lp-text);
  font-size: var(--lp-text-xl);
}
.exam-learning-feedback__pending,
.exam-learning-feedback__analysis p {
  color: var(--lp-text-secondary);
  line-height: var(--lp-leading-body);
}
.exam-learning-feedback__answers {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--lp-space-3);
  margin: 0;
}
.exam-learning-feedback dd {
  margin: var(--lp-space-1) 0 0;
  color: var(--lp-text);
  overflow-wrap: anywhere;
}
.exam-learning-feedback__analysis {
  display: grid;
  gap: var(--lp-space-2);
  padding-top: var(--lp-space-4);
  border-top: var(--lp-border-hairline);
}
.exam-learning-feedback__analysis h4 {
  color: var(--lp-text);
  font-size: var(--lp-text-base);
}
@media (max-width: 640px) {
  .exam-learning-feedback__answers {
    grid-template-columns: 1fr;
  }
}
</style>
