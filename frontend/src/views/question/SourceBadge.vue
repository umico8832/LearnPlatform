<script setup lang="ts">
import { computed } from 'vue'
import { ArrowUpRight, BadgeCheck, Bot, CircleDashed, FileText } from '@lucide/vue'
import type { QuestionSource } from '@/api/understanding'

/** Where a question comes from and how far that has been checked; never claims more than the server says. */
const props = defineProps<{ source: QuestionSource }>()

const kindLabel = computed(() => {
  if (props.source.kind === 'OFFICIAL_EXAM') return props.source.title ?? '考试真题'
  return props.source.kind === 'AI_GENERATED' ? 'AI 生成' : '人工整理'
})

const checkLabel = computed(() => {
  const { kind, verification } = props.source
  if (verification === 'VERIFIED') return '已核对来源'
  if (verification === 'REVIEWED') return '已审核'
  return kind === 'OFFICIAL_EXAM' ? '未核对来源' : '未审核'
})

const icon = computed(() => {
  if (props.source.verification === 'UNREVIEWED') return CircleDashed
  if (props.source.kind === 'AI_GENERATED') return Bot
  return props.source.verification === 'VERIFIED' ? BadgeCheck : FileText
})
</script>

<template>
  <span class="source" :data-verification="source.verification">
    <component :is="icon" class="source-icon" :size="14" aria-hidden="true" />
    <a v-if="source.url" class="source-kind" :href="source.url" target="_blank" rel="noopener noreferrer">
      {{ kindLabel }}<ArrowUpRight :size="12" aria-hidden="true" />
      <span class="sr-only">（在新窗口打开原题来源）</span>
    </a>
    <span v-else class="source-kind">{{ kindLabel }}</span>
    <span class="source-dot" aria-hidden="true">·</span>
    <span class="source-check">{{ checkLabel }}</span>
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

.source[data-verification='VERIFIED'] .source-icon {
  color: var(--lt-accent);
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

.source-dot,
.source-check {
  flex: none;
  color: var(--lt-label-3);
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
