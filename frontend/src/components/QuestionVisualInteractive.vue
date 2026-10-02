<template>
  <div class="visual-interactive">
    <div v-if="loading" class="vi-loading" role="status">
      <el-icon class="is-loading" :size="20" aria-hidden="true"><Loading /></el-icon>
      <span>{{ loadingText || '正在准备可视化讲解' }}</span>
    </div>

    <!-- 结构化内容无效时保留原文本，避免丢失可阅读信息。 -->
    <div v-else-if="fallbackMode" class="vi-fallback">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="可视化数据解析失败，已切换为文本显示"
        class="vi-fallback-notice"
      />
      <MarkdownRenderer :content="rawContent" />
    </div>

    <QuestionVisualRenderer v-else-if="data" :data="data" />

    <div v-else class="vi-empty">
      <el-empty description="暂无可视化数据" :image-size="60" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import MarkdownRenderer from './MarkdownRenderer.vue'
import QuestionVisualRenderer from './question-visual/QuestionVisualRenderer.vue'
import { parseQuestionVisualContent } from './question-visual/useQuestionVisualContent'

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
  padding-block: var(--lp-space-1);
}
.vi-loading,
.vi-empty {
  display: grid;
  justify-items: center;
  gap: var(--lp-space-2);
  padding: var(--lp-space-5) 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}
.vi-fallback {
  padding-block: var(--lp-space-1);
}
.vi-fallback-notice {
  margin-bottom: var(--lp-space-3);
}
</style>
