<script setup lang="ts">
withDefaults(defineProps<{ days: number; label?: string }>(), { label: '连续学习' })
</script>

<template>
  <div class="gamification-streak" role="status" :aria-label="`${label} ${days} 天`">
    <span aria-hidden="true">◆</span><strong>{{ days }}</strong
    ><span>{{ label }}</span>
  </div>
</template>

<style scoped>
.gamification-streak {
  display: inline-flex;
  align-items: center;
  gap: var(--lp-space-1);
  min-height: 28px;
  padding: var(--lp-space-1) var(--lp-space-2);
  color: var(--lp-reward-streak);
  font-size: var(--lp-text-xs);
  font-weight: var(--lp-weight-semibold);
  background: var(--lp-reward-streak-soft);
  border: 1px solid color-mix(in srgb, var(--lp-reward-streak) 24%, transparent);
  border-radius: var(--lp-radius-full);
}
.gamification-streak strong {
  font-size: var(--lp-text-sm);
  font-variant-numeric: tabular-nums;
}
</style>
