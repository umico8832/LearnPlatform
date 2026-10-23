<script setup lang="ts">
import LtButton from '@/components/living/LtButton.vue'
import LtSheet from '@/components/living/LtSheet.vue'
import type { SimilarQuestions } from '@/api/statistics'
import QuestionProse from '@/views/question/QuestionProse.vue'
defineProps<{
  modelValue: boolean
  loading: boolean
  data: SimilarQuestions | null
  sourceContent: string
  error?: string
  starting?: boolean
  practiceError?: string
}>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; 'start-practice': []; retry: [] }>()
</script>
<template>
  <LtSheet :open="modelValue" title="相似题推荐" :width="840" @update:open="emit('update:modelValue', $event)"
    ><div v-if="loading" class="loading" aria-busy="true"><span role="status">正在读取相似题</span></div>
    <div v-else-if="error" class="notice" role="alert">
      <p>{{ error }}</p>
      <LtButton @click="emit('retry')">重试读取</LtButton>
    </div>
    <template v-else-if="data"
      ><div class="source"><strong>原题：</strong><QuestionProse inline :content="sourceContent" /></div>
      <ul class="rows">
        <li v-for="item in data.similarQuestions" :key="item.questionId">
          <div>
            <QuestionProse class="question" :content="item.questionContent" size="body" />
            <span
              >{{ item.reason }} · {{ item.questionType
              }}<template v-if="item.difficulty"> · 难度 {{ item.difficulty }}</template></span
            >
          </div>
          <div class="score">
            <i><b :style="{ width: `${item.similarityScore}%` }" /></i><em>{{ item.similarityScore }}%</em>
          </div>
          <span class="tag" :data-attempted="item.alreadyAttempted">{{
            item.alreadyAttempted ? '已练过' : '未练过'
          }}</span>
        </li>
      </ul></template
    >
    <p v-else class="muted">暂无相似题目</p>
    <p v-if="practiceError" class="notice" role="alert">{{ practiceError }}</p>
    <div class="actions">
      <LtButton @click="emit('update:modelValue', false)">关闭</LtButton
      ><LtButton
        variant="primary"
        :disabled="starting || !data?.similarQuestions?.length"
        @click="emit('start-practice')"
        >{{ starting ? '准备中…' : '开始练习相似题' }}</LtButton
      >
    </div></LtSheet
  >
</template>
<style scoped>
.loading {
  min-height: calc(var(--lt-space-10) * 2 + var(--lt-space-4));
  display: grid;
  place-items: center;
  color: var(--lt-label-2);
}
.source,
.notice {
  margin: 0;
  padding: var(--lt-space-4);
  border-radius: var(--lt-radius-md);
  background: var(--lt-fill-2);
  line-height: var(--lt-leading-ui);
}
.notice {
  background: var(--lt-orange-soft);
  color: var(--lt-orange-text);
}
.rows {
  display: grid;
  gap: var(--lt-space-3);
  margin: var(--lt-space-4) 0;
  padding: 0;
  list-style: none;
}
.rows li {
  display: grid;
  grid-template-columns: minmax(0, 1fr) calc(var(--lt-space-10) + var(--lt-space-7) + var(--lt-space-5)) auto;
  gap: var(--lt-space-4);
  align-items: center;
  padding: var(--lt-space-4);
  border-radius: var(--lt-radius-md);
  background: var(--lt-fill-2);
}
.rows li > div:first-child {
  display: grid;
  gap: var(--lt-space-2);
}
.rows span,
.muted {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.score {
  display: flex;
  align-items: center;
  gap: var(--lt-space-2);
}
.score i {
  flex: 1;
  height: calc(var(--lt-space-2) - var(--lt-space-1) / 4);
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-hover);
  overflow: hidden;
}
.score b {
  display: block;
  height: 100%;
  background: var(--lt-accent);
}
.score em {
  font-style: normal;
  font-size: var(--lt-text-caption);
}
.tag {
  padding: calc(var(--lt-space-1) * 0.75) var(--lt-space-2);
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-hover);
  white-space: nowrap;
}
.tag[data-attempted='true'] {
  background: var(--lt-green-soft);
  color: var(--lt-green-text);
}
.actions {
  display: flex;
  justify-content: end;
  gap: var(--lt-space-3);
  margin-top: var(--lt-space-5);
}
@media (max-width: 700px) {
  .rows li {
    grid-template-columns: 1fr;
  }
  .score {
    width: calc(var(--lt-space-10) * 2 + var(--lt-space-5) + var(--lt-space-4));
  }
}
</style>
