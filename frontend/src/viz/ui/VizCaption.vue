<script setup lang="ts">
import { ref, watch } from 'vue'
import { blurIn } from '@/motion'
import type { Step } from '../engine/types'

/** What the current step does, as code and one plain sentence. */
const props = defineProps<{ step: Step; index: number; total: number }>()

const title = ref<HTMLElement | null>(null)
const note = ref<HTMLElement | null>(null)

watch(
  () => `${props.index}:${props.step.title}:${props.step.note}`,
  () => {
    if (title.value) blurIn(title.value, 3)
    if (note.value) blurIn(note.value, 5)
  },
  { flush: 'post' },
)
</script>

<template>
  <div class="viz-caption" aria-live="polite">
    <div class="viz-caption-meta">
      <span class="viz-caption-count">第 {{ index + 1 }} 步</span>
      <span>共 {{ total }} 步</span>
      <span v-if="step.phase" class="viz-caption-phase">{{ step.phase }}</span>
    </div>
    <code ref="title" class="viz-caption-title">{{ step.title }}</code>
    <p ref="note" class="viz-caption-note">{{ step.note }}</p>
  </div>
</template>

<style scoped>
.viz-caption-meta {
  display: flex;
  align-items: center;
  gap: var(--lt-space-2);
  color: var(--lt-label-2);
  font-size: var(--lt-text-caption);
}

.viz-caption-count {
  color: var(--lt-label);
  font-weight: 600;
}

.viz-caption-phase {
  padding: 2px 8px;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
}

.viz-caption-title {
  display: block;
  margin-top: 6px;
  color: var(--lt-label);
  font: 600 16px / 1.35 var(--lt-font-mono);
}

.viz-caption-note {
  max-width: 68ch;
  margin: 6px 0 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-body);
  line-height: 1.6;
  text-wrap: pretty;
}
</style>
