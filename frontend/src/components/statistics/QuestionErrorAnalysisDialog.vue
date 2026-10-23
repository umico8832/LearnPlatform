<script setup lang="ts">
import LtButton from '@/components/living/LtButton.vue'
import LtSheet from '@/components/living/LtSheet.vue'
import type { QuestionErrorAnalysis } from '@/api/statistics'
import QuestionProse from '@/views/question/QuestionProse.vue'
defineProps<{ modelValue: boolean; loading: boolean; data: QuestionErrorAnalysis | null; error?: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; retry: [] }>()
</script>
<template>
  <LtSheet :open="modelValue" title="单题作答记录" :width="750" @update:open="emit('update:modelValue', $event)"
    ><div v-if="loading" class="loading" aria-busy="true"><span role="status">正在读取作答记录</span></div>
    <div v-else-if="error" class="notice" role="alert">
      <p>{{ error }}</p>
      <LtButton @click="emit('retry')">重试读取</LtButton>
    </div>
    <article v-else-if="data" class="detail">
      <header>
        <QuestionProse :content="data.questionContent" />
        <span
          >{{ data.questionType }}<template v-if="data.difficulty"> · 难度 {{ data.difficulty }}</template></span
        >
      </header>
      <dl class="metrics">
        <div>
          <dt>总作答</dt>
          <dd>{{ data.totalAttempts }}</dd>
        </div>
        <div>
          <dt>答对</dt>
          <dd class="green">{{ data.correctCount }}</dd>
        </div>
        <div>
          <dt>答错</dt>
          <dd class="orange">{{ data.wrongCount }}</dd>
        </div>
        <div>
          <dt>正确率</dt>
          <dd>{{ data.correctRate }}%</dd>
        </div>
      </dl>
      <p class="trend" :data-trend="data.masteryTrend">{{ data.trendDescription }}</p>
      <p v-if="data.currentMasteryLevel !== null" class="muted">
        当前掌握程度：{{
          data.currentMasteryLevel === 2 ? '已掌握' : data.currentMasteryLevel === 1 ? '部分掌握' : '未掌握'
        }}
      </p>
      <section>
        <h3>作答模式</h3>
        <QuestionProse :content="data.errorPattern" size="body" />
      </section>
      <section v-if="data.attempts.length">
        <h3>作答历史（共 {{ data.attempts.length }} 次）</h3>
        <ol>
          <li v-for="attempt in data.attempts" :key="attempt.recordId" class="attempt-row">
            <i :data-correct="attempt.isCorrect" />
            <div>
              <strong>{{ attempt.isCorrect === 1 ? '答对' : attempt.isCorrect === 0 ? '答错' : '待判分' }}</strong
              ><span v-if="attempt.userAnswer">答案：{{ attempt.userAnswer }}</span
              ><time v-if="attempt.createTime">{{ attempt.createTime.replace('T', ' ') }}</time>
            </div>
            <em v-if="attempt.answerTime">{{ attempt.answerTime }}s</em>
          </li>
        </ol>
      </section>
    </article>
    <p v-else class="muted">暂无数据</p>
    <div class="actions"><LtButton @click="emit('update:modelValue', false)">关闭</LtButton></div></LtSheet
  >
</template>
<style scoped>
.loading {
  min-height: calc(var(--lt-space-10) * 2 + var(--lt-space-4));
  display: grid;
  place-items: center;
  color: var(--lt-label-2);
}
.notice {
  padding: var(--lt-space-4);
  border-radius: var(--lt-radius-md);
  background: var(--lt-orange-soft);
  color: var(--lt-orange-text);
}
.detail {
  display: grid;
  gap: var(--lt-space-5);
}
header {
  padding: var(--lt-space-4);
  border-radius: var(--lt-radius-md);
  background: var(--lt-fill-2);
}
header p {
  margin: 0;
  font-size: var(--lt-text-reading);
  line-height: var(--lt-leading-reading);
}
header span,
.muted {
  display: block;
  margin-top: var(--lt-space-2);
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--lt-space-3);
  margin: 0;
}
.metrics div {
  padding: var(--lt-space-3);
  border-radius: var(--lt-radius-md);
  background: var(--lt-fill-2);
}
dt {
  color: var(--lt-label-2);
  font-size: var(--lt-text-caption);
}
dd {
  margin: var(--lt-space-1) 0 0;
  font: 600 var(--lt-text-title3)/1 var(--lt-font-rounded);
}
.green {
  color: var(--lt-green-text);
}
.orange {
  color: var(--lt-orange-text);
}
.trend,
section {
  margin: 0;
  padding: var(--lt-space-4);
  border-radius: var(--lt-radius-md);
  background: var(--lt-fill-2);
}
.trend[data-trend='DECLINING'] {
  background: var(--lt-orange-soft);
  color: var(--lt-orange-text);
}
h3 {
  margin: 0 0 var(--lt-space-2);
  font-size: var(--lt-text-footnote);
}
section p {
  margin: 0;
  line-height: var(--lt-leading-ui);
}
ol {
  display: grid;
  gap: var(--lt-space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}
li {
  display: flex;
  gap: var(--lt-space-3);
  align-items: start;
}
li i {
  width: var(--lt-space-2);
  height: var(--lt-space-2);
  margin-top: calc(var(--lt-space-1) + var(--lt-space-2) / 4);
  border-radius: 50%;
  background: var(--lt-label-2);
}
li i[data-correct='1'] {
  background: var(--lt-green);
}
li i[data-correct='0'] {
  background: var(--lt-orange);
}
li div {
  display: grid;
  gap: calc(var(--lt-space-1) / 2);
  flex: 1;
}
li span,
time {
  color: var(--lt-label-2);
  font-size: var(--lt-text-caption);
}
li em {
  font-style: normal;
  font-size: var(--lt-text-caption);
}
.actions {
  display: flex;
  justify-content: end;
  margin-top: var(--lt-space-5);
}
@media (max-width: 700px) {
  .metrics {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
