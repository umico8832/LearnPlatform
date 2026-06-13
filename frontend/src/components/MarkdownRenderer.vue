<template>
  <div class="markdown-body" v-html="renderedHtml"></div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const props = defineProps<{ content: string }>()

const renderedHtml = computed(() => {
  if (!props.content) return ''
  try {
    const html = marked.parse(props.content) as string
    return DOMPurify.sanitize(html)
  } catch {
    return DOMPurify.sanitize(props.content)
  }
})
</script>

<style scoped>
.markdown-body { font-size: 14px; line-height: 1.8; color: #303133; }
.markdown-body :deep(h1), .markdown-body :deep(h2), .markdown-body :deep(h3) { margin: 12px 0 8px; font-weight: 600; }
.markdown-body :deep(h2) { font-size: 16px; }
.markdown-body :deep(h3) { font-size: 15px; }
.markdown-body :deep(p) { margin: 8px 0; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { padding-left: 20px; margin: 8px 0; }
.markdown-body :deep(li) { margin: 4px 0; }
.markdown-body :deep(code) { background: #f5f7fa; padding: 2px 6px; border-radius: 4px; font-size: 13px; color: #e6a23c; }
.markdown-body :deep(pre) { background: #f5f7fa; padding: 12px; border-radius: 8px; overflow-x: auto; }
.markdown-body :deep(pre code) { background: none; padding: 0; color: #303133; }
.markdown-body :deep(blockquote) { border-left: 4px solid #409eff; padding-left: 12px; color: #909399; margin: 8px 0; }
.markdown-body :deep(table) { border-collapse: collapse; width: 100%; margin: 8px 0; }
.markdown-body :deep(th), .markdown-body :deep(td) { border: 1px solid #ebeef5; padding: 8px 12px; text-align: left; }
.markdown-body :deep(th) { background: #f5f7fa; font-weight: 600; }
.markdown-body :deep(strong) { color: #409eff; }
</style>
