<script setup lang="ts">
import { computed } from 'vue'
import AnswerRewardFeedback from '@/views/practice/PracticeRewardFeedback.vue'
import QuestionProse from '@/views/question/QuestionProse.vue'
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
  >
    <p class="sr-only" role="status">{{ title }}</p>
    <h2 class="verdict"><span class="verdict-dot" aria-hidden="true" />{{ title }}</h2>
    <p v-if="result.correct === null" class="note">已记录本次作答，不计入自动判分正确率。</p>
    <div class="answers">
      <p
        class="answer-chip"
        :data-tone="result.correct === true ? 'green' : result.correct === false ? 'orange' : 'neutral'"
      >
        <span class="answer-label">你的答案</span><span class="answer-value">{{ result.userAnswer || '未填写' }}</span>
      </p>
      <p
        v-if="(result.correct === false || result.correct === null) && result.correctAnswer"
        class="answer-chip"
        data-tone="green"
      >
        <span class="answer-label">参考答案</span><span class="answer-value">{{ result.correctAnswer }}</span>
      </p>
    </div>
    <div v-if="result.analysis" class="exam-learning-feedback__analysis analysis">
      <h3>解析</h3>
      <QuestionProse :content="result.analysis" />
    </div>
    <AnswerRewardFeedback v-if="result.reward" :reward="result.reward" />
  </section>
</template>
<style scoped>
.exam-learning-feedback {
  display: grid;
  gap: var(--lt-space-4);
  margin: 0;
  padding: 0 0 var(--lt-space-5);
}
.exam-learning-feedback h2,
.exam-learning-feedback h3,
.exam-learning-feedback p {
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
.is-correct .verdict {
  color: var(--lt-green-text);
}
.is-correct .verdict-dot {
  background: var(--lt-green);
}
.is-wrong .verdict {
  color: var(--lt-orange-text);
}
.is-wrong .verdict-dot {
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
.answer-chip[data-tone='neutral'] {
  background: var(--lt-fill-2);
  color: var(--lt-label-2);
}
.answer-chip[data-tone='neutral'] .answer-value {
  background: var(--lt-fill);
  color: var(--lt-label);
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
