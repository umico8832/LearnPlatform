<script setup lang="ts">
withDefaults(
  defineProps<{
    rows?: number
    card?: boolean
    label?: string
    announce?: boolean
  }>(),
  { rows: 3, card: false, label: '正在加载', announce: true },
)

const rowWidths = ['92%', '100%', '78%', '96%', '84%']
</script>

<template>
  <div
    class="lp-skeleton"
    :class="{ 'is-card': card }"
    :role="announce ? 'status' : undefined"
    :aria-label="announce ? label : undefined"
    :aria-hidden="announce ? undefined : true"
  >
    <div
      v-for="index in rows"
      :key="index"
      class="lp-skeleton-line"
      :style="{ width: rowWidths[(index - 1) % rowWidths.length] }"
    />
  </div>
</template>

<style scoped>
.lp-skeleton {
  display: grid;
  gap: var(--lp-space-3);
  padding: var(--lp-space-2) 0;
}
.lp-skeleton.is-card {
  padding: var(--lp-space-5);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  background: var(--lp-surface);
}
.lp-skeleton-line {
  height: var(--lp-text-base);
  border-radius: var(--lp-radius-xs);
  background: linear-gradient(
    90deg,
    var(--lp-skeleton-base) 25%,
    var(--lp-skeleton-highlight) 50%,
    var(--lp-skeleton-base) 75%
  );
  background-size: 200% 100%;
  animation: lp-skeleton-shimmer var(--lp-duration-skeleton) var(--lp-ease-in-out) infinite;
}
@keyframes lp-skeleton-shimmer {
  from {
    background-position: 200% 0;
  }
  to {
    background-position: -200% 0;
  }
}
@media (prefers-reduced-motion: reduce) {
  .lp-skeleton-line {
    animation: none;
  }
}
</style>
