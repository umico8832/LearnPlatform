<script setup lang="ts">
import { computed } from 'vue'
import { useReducedMotion } from '@/composables/useReducedMotion'

const props = withDefaults(
  defineProps<{
    percent: number
    label: string
    size?: 'sm' | 'md' | 'lg'
    tone?: 'primary' | 'success' | 'warning' | 'danger'
    showLabel?: boolean
  }>(),
  { size: 'md', tone: 'primary', showLabel: true },
)

const { reducedMotion } = useReducedMotion()
const percent = computed(() => (Number.isFinite(props.percent) ? Math.max(0, Math.min(100, props.percent)) : null))
const displayedPercent = computed(() => (percent.value === null ? '—' : `${Math.round(percent.value)}%`))
const valueText = computed(() =>
  percent.value === null ? `${props.label}暂不可用` : `${props.label} ${percent.value}%`,
)
const dashOffset = computed(() => 100 - (percent.value ?? 0))
</script>

<template>
  <div
    class="lp-progress-ring"
    :data-size="size"
    :data-tone="tone"
    :data-reduced-motion="reducedMotion"
    role="progressbar"
    :aria-label="label"
    :aria-valuenow="percent ?? undefined"
    :aria-valuetext="valueText"
    aria-valuemin="0"
    aria-valuemax="100"
  >
    <svg viewBox="0 0 36 36" aria-hidden="true">
      <circle class="lp-progress-ring-track" cx="18" cy="18" r="15.5" pathLength="100" />
      <circle
        class="lp-progress-ring-value"
        :data-available="percent !== null"
        cx="18"
        cy="18"
        r="15.5"
        pathLength="100"
        :style="{ strokeDashoffset: dashOffset }"
      />
    </svg>
    <span v-if="showLabel" class="lp-progress-ring-label" aria-hidden="true">{{ displayedPercent }}</span>
  </div>
</template>

<style scoped>
.lp-progress-ring {
  position: relative;
  display: inline-grid;
  place-items: center;
  width: var(--lp-space-12);
  aspect-ratio: 1;
  color: var(--lp-primary);
}
.lp-progress-ring[data-size='sm'] {
  width: var(--lp-space-10);
}
.lp-progress-ring[data-size='lg'] {
  width: var(--lp-space-16);
}
svg {
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}
circle {
  fill: none;
  stroke-width: 3;
}
.lp-progress-ring-track {
  stroke: var(--lp-surface-inset);
}
.lp-progress-ring-value {
  stroke: currentcolor;
  stroke-dasharray: 100;
  transition: stroke-dashoffset var(--lp-duration-slow) var(--lp-ease-out);
}
.lp-progress-ring-value[data-available='false'] {
  visibility: hidden;
}
.lp-progress-ring[data-tone='success'] {
  color: var(--lp-success);
}
.lp-progress-ring[data-tone='warning'] {
  color: var(--lp-warning);
}
.lp-progress-ring[data-tone='danger'] {
  color: var(--lp-danger);
}
.lp-progress-ring[data-reduced-motion='true'] .lp-progress-ring-value {
  transition: none;
}
.lp-progress-ring-label {
  position: absolute;
  color: var(--lp-text);
  font-size: var(--lp-text-xs);
  font-weight: var(--lp-weight-semibold);
  font-variant-numeric: tabular-nums;
}
</style>
