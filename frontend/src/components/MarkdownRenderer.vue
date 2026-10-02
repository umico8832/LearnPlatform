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
.markdown-body {
  min-width: 0;
  font-size: var(--lp-text-base);
  line-height: var(--lp-leading-relaxed);
  color: var(--lp-text);
  overflow-wrap: anywhere;
}
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  margin: var(--lp-space-4) 0 var(--lp-space-2);
  font-weight: var(--lp-weight-semibold);
  line-height: var(--lp-leading-snug);
}
.markdown-body :deep(h1) {
  font-size: var(--lp-text-xl);
}
.markdown-body :deep(h2) {
  font-size: var(--lp-text-lg);
}
.markdown-body :deep(h3) {
  font-size: var(--lp-text-md);
}
.markdown-body :deep(p) {
  margin: var(--lp-space-2) 0;
}
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: var(--lp-space-5);
  margin: var(--lp-space-2) 0;
}
.markdown-body :deep(li) {
  margin: var(--lp-space-1) 0;
}
.markdown-body :deep(code) {
  background: var(--lp-surface-soft);
  padding: 2px var(--lp-space-1);
  border-radius: var(--lp-radius-xs);
  font-family: var(--lp-font-mono);
  font-size: var(--lp-text-sm);
  color: var(--lp-text);
}
.markdown-body :deep(pre) {
  background: var(--lp-surface-soft);
  padding: var(--lp-space-4);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-sm);
  overflow-x: auto;
}
.markdown-body :deep(pre code) {
  background: none;
  padding: 0;
}
.markdown-body :deep(blockquote) {
  border-left: 3px solid var(--lp-border-strong);
  padding-left: var(--lp-space-3);
  color: var(--lp-text-secondary);
  margin: var(--lp-space-3) 0;
}
.markdown-body :deep(table) {
  display: block;
  overflow-x: auto;
  border-collapse: collapse;
  max-width: 100%;
  margin: var(--lp-space-3) 0;
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: var(--lp-border-hairline);
  padding: var(--lp-space-2) var(--lp-space-3);
  text-align: left;
}
.markdown-body :deep(th) {
  background: var(--lp-surface-soft);
  font-weight: var(--lp-weight-semibold);
}
.markdown-body :deep(strong) {
  color: var(--lp-text);
  font-weight: var(--lp-weight-semibold);
}
.markdown-body :deep(a) {
  color: var(--lp-link);
  text-decoration: underline;
  text-underline-offset: 3px;
}
.markdown-body :deep(img) {
  max-width: 100%;
  height: auto;
}
.markdown-body :deep(> :first-child) {
  margin-top: 0;
}
.markdown-body :deep(> :last-child) {
  margin-bottom: 0;
}
</style>
