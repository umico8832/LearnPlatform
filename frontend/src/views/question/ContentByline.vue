<script setup lang="ts">
import { computed } from 'vue'
import type { UnderstandingContentSource } from '@/api/understanding'

/** Who wrote a piece of teaching content and whether a person has checked it; never claims more than the server says. */
const props = defineProps<{ what: string; source: UnderstandingContentSource }>()

const kind = computed(() => (props.source.kind === 'AI_GENERATED' ? 'AI 生成' : '人工整理'))
const reviewed = computed(() => props.source.reviewStatus === 'REVIEWED')
</script>

<template>
  <p class="byline">
    {{ what }} · {{ kind }} ·
    <span :data-pending="reviewed ? undefined : ''">{{ reviewed ? '已审核' : '待审核' }}</span>
  </p>
</template>

<style scoped>
.byline {
  margin: var(--lt-space-3) 0 0;
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
}

[data-pending] {
  color: var(--lt-orange-text);
  font-weight: 600;
}
</style>
