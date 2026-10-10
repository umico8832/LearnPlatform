<script setup lang="ts">
import { computed } from 'vue'
import DOMPurify from 'dompurify'
import { marked } from 'marked'

/** Markdown on the new baseline: stems and explanations at reading size, statements in the monospace face. */
const props = withDefaults(defineProps<{ content: string; inline?: boolean; size?: 'reading' | 'body' }>(), {
  inline: false,
  size: 'reading',
})

const html = computed(() => {
  if (!props.content) return ''
  const rendered = props.inline ? marked.parseInline(props.content) : marked.parse(props.content)
  return DOMPurify.sanitize(rendered as string)
})
</script>

<template>
  <span v-if="inline" class="prose" data-inline v-html="html" />
  <div v-else class="prose" :data-size="size" v-html="html" />
</template>

<style scoped>
.prose {
  min-width: 0;
  overflow-wrap: anywhere;
  text-wrap: pretty;
}

.prose[data-size='reading'] {
  font-size: var(--lt-text-reading);
  line-height: var(--lt-leading-reading);
}

.prose[data-size='body'] {
  font-size: var(--lt-text-body);
  line-height: 1.65;
}

.prose :deep(p) {
  margin: 0;
}

.prose :deep(p + p),
.prose :deep(:is(ul, ol, pre, blockquote)) {
  margin: 0.75em 0 0;
}

.prose :deep(:is(ul, ol)) {
  padding-left: 1.4em;
}

.prose :deep(li + li) {
  margin-top: 0.25em;
}

.prose :deep(strong) {
  font-weight: 600;
}

.prose :deep(code) {
  padding: 0.1em 0.35em;
  border-radius: 6px;
  background: var(--lt-code-bg);
  font-family: var(--lt-font-mono);
  font-size: 0.9em;
}

.prose :deep(pre) {
  padding: var(--lt-space-3) var(--lt-space-4);
  overflow-x: auto;
  border-radius: var(--lt-radius-sm);
  background: var(--lt-code-bg);
  line-height: 1.6;
}

.prose :deep(pre code) {
  padding: 0;
  background: none;
}

.prose :deep(blockquote) {
  padding-left: var(--lt-space-3);
  border-left: 3px solid var(--lt-separator);
  color: var(--lt-label-2);
}

.prose :deep(a) {
  color: var(--lt-accent);
  text-decoration: none;
}

.prose :deep(a:hover) {
  text-decoration: underline;
}
</style>
