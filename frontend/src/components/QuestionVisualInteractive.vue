<template>
  <div class="visual-interactive">
    <!-- 加载状态 -->
    <div v-if="loading" class="vi-loading">
      <el-icon class="is-loading" :size="24"><Loading /></el-icon>
      <span>{{ loadingText }}</span>
    </div>

    <!-- 错误状态：JSON 解析失败，回退为 Markdown 显示 -->
    <div v-else-if="fallbackMode" class="vi-fallback">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="可视化数据解析失败，已切换为文本显示"
        style="margin-bottom: 12px"
      />
      <MarkdownRenderer :content="rawContent" />
    </div>

    <QuestionVisualRenderer v-else-if="data" :data="data" />

    <!-- 空状态 -->
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
  padding: 4px 0;
}

.vi-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 20px 0;
  color: #909399;
  justify-content: center;
}

.vi-empty {
  padding: 20px 0;
}

.vi-fallback {
  padding: 4px 0;
}
</style>
