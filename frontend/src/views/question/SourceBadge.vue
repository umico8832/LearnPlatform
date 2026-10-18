<script setup lang="ts">
import { ArrowUpRight, FileText } from '@lucide/vue'
import type { QuestionSource } from '@/api/understanding'

/** Which exam a past-paper question comes from; other questions show nothing. */
defineProps<{ source: QuestionSource }>()
</script>

<template>
  <span v-if="source.kind === 'OFFICIAL_EXAM'" class="source">
    <FileText class="source-icon" :size="14" aria-hidden="true" />
    <a v-if="source.url" class="source-kind" :href="source.url" target="_blank" rel="noopener noreferrer">
      {{ source.title ?? '考试真题' }}<ArrowUpRight :size="12" aria-hidden="true" />
      <span class="sr-only">（在新窗口打开原题来源）</span>
    </a>
    <span v-else class="source-kind">{{ source.title ?? '考试真题' }}</span>
  </span>
</template>

<style scoped>
.source {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-caption);
  font-weight: 500;
}

.source-icon {
  flex: none;
}

.source-kind {
  display: inline-flex;
  align-items: center;
  gap: 1px;
  min-width: 0;
  overflow: hidden;
  color: inherit;
  text-decoration: none;
  white-space: nowrap;
  text-overflow: ellipsis;
}

a.source-kind:hover {
  color: var(--lt-accent);
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
