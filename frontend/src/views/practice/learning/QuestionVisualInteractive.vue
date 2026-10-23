<template>
  <div class="visual-interactive">
    <div v-if="loading" class="vi-loading" role="status">
      <Loading class="vi-spinner" aria-hidden="true" />
      <span>{{ loadingText || '正在准备可视化讲解' }}</span>
    </div>

    <!-- 结构化内容无效时保留原文本，避免丢失可阅读信息。 -->
    <div v-else-if="fallbackMode" class="vi-fallback">
      <p role="status">可视化数据解析失败，已切换为文本显示</p>
      <MarkdownRenderer :content="rawContent" />
    </div>

    <QuestionVisualRenderer v-else-if="data" :data="data" />

    <div v-else class="vi-empty">
      <p>暂无可视化数据</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { LoaderCircle as Loading } from '@lucide/vue'
import MarkdownRenderer from '@/views/question/QuestionProse.vue'
import QuestionVisualRenderer from './QuestionVisualRenderer.vue'
import { parseQuestionVisualContent } from '@/components/question-visual/useQuestionVisualContent'

const props = defineProps<{
  content: string
  loading?: boolean
  loadingText?: string
}>()

const contentState = computed(() => parseQuestionVisualContent(props.content))
const data = computed(() => contentState.value.data)
const fallbackMode = computed(() => contentState.value.fallbackMode)
const rawContent = computed(() => contentState.value.rawContent)
</script>

<style scoped>
.visual-interactive {
  padding-block: var(--lt-space-1);
}
.vi-loading,
.vi-empty {
  display: grid;
  justify-items: center;
  gap: var(--lt-space-2);
  padding: var(--lt-space-5) 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.vi-spinner {
  width: var(--lt-space-5);
  height: var(--lt-space-5);
  animation: spin var(--lt-dur-layout) linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(1turn);
  }
}
.vi-fallback {
  padding-block: var(--lt-space-1);
}
.vi-fallback-notice {
  margin-bottom: var(--lt-space-3);
}
</style>
