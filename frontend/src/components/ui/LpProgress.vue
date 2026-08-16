<script setup lang="ts">
import { computed } from 'vue'

/** 细进度条：用于学习会话、考试、复习的进度表达，安静且不抢内容。 */
const props = withDefaults(
  defineProps<{
    /** 0-100 */
    percent: number
    tone?: 'primary' | 'success' | 'warning' | 'danger'
    showLabel?: boolean
    label?: string
  }>(),
  { tone: 'primary', showLabel: false, label: '' },
)

const clamped = computed(() => Math.max(0, Math.min(100, props.percent)))
</script>

<template>
  <div class="lp-progress" role="progressbar" :aria-valuenow="clamped" aria-valuemin="0" aria-valuemax="100">
    <div class="lp-progress-track">
      <div class="lp-progress-fill" :data-tone="tone" :style="{ width: `${clamped}%` }" />
    </div>
    <span v-if="showLabel" class="lp-progress-label">{{ label || `${clamped}%` }}</span>
  </div>
</template>

<style scoped>
.lp-progress {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  min-width: 0;
}
.lp-progress-track {
  flex: 1;
  height: 4px;
  min-width: 40px;
  border-radius: var(--lp-radius-full);
  background: var(--lp-surface-inset);
  overflow: hidden;
}
.lp-progress-fill {
  height: 100%;
  border-radius: var(--lp-radius-full);
  background: var(--lp-primary);
  transition:
    width var(--lp-duration-slow) var(--lp-ease-out),
    background-color var(--lp-duration-fast) var(--lp-ease-out);
}
.lp-progress-fill[data-tone='success'] {
  background: var(--lp-success);
}
.lp-progress-fill[data-tone='warning'] {
  background: var(--lp-warning);
}
.lp-progress-fill[data-tone='danger'] {
  background: var(--lp-danger);
}
.lp-progress-label {
  flex: 0 0 auto;
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
  font-variant-numeric: tabular-nums;
}
</style>
