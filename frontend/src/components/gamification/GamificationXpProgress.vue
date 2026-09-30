<script setup lang="ts">
import { computed, watch } from 'vue'
import { useAnimatedNumber } from '@/composables/useAnimatedNumber'
import { useReducedMotion } from '@/composables/useReducedMotion'

const props = defineProps<{ level: number; currentXp: number; nextLevelXp: number }>()
const { reducedMotion } = useReducedMotion()
const { value: displayXp, animateTo } = useAnimatedNumber(props.currentXp, { reducedMotion })
const progress = computed(() =>
  props.nextLevelXp > 0 ? Math.min(100, Math.max(0, (props.currentXp / props.nextLevelXp) * 100)) : 0,
)
watch(() => props.currentXp, animateTo)
</script>

<template>
  <section class="gamification-xp-progress" aria-label="学习经验进度">
    <span class="gamification-xp-progress__level">Lv.{{ level }}</span>
    <div
      class="gamification-xp-progress__track"
      role="progressbar"
      :aria-valuenow="Math.round(progress)"
      aria-valuemin="0"
      aria-valuemax="100"
    >
      <span class="gamification-xp-progress__fill" :style="{ width: `${progress}%` }" />
    </div>
    <output class="gamification-xp-progress__value">{{ displayXp }} / {{ nextLevelXp }} 经验</output>
  </section>
</template>

<style scoped>
.gamification-xp-progress {
  display: grid;
  grid-template-columns: auto minmax(72px, 1fr) auto;
  align-items: center;
  gap: var(--lp-space-2);
  min-width: 0;
}
.gamification-xp-progress__level {
  color: var(--lp-on-primary);
  font-size: var(--lp-text-xs);
  font-weight: var(--lp-weight-bold);
  padding: 3px 6px;
  background: var(--lp-reward-level);
  border-radius: var(--lp-radius-full);
}
.gamification-xp-progress__track {
  height: 6px;
  overflow: hidden;
  background: var(--lp-reward-xp-soft);
  border-radius: var(--lp-radius-full);
}
.gamification-xp-progress__fill {
  display: block;
  height: 100%;
  background: var(--lp-reward-xp);
  border-radius: inherit;
  transition: width var(--lp-duration-feedback) var(--lp-ease-out);
}
.gamification-xp-progress__value {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
@media (prefers-reduced-motion: reduce) {
  .gamification-xp-progress__fill {
    transition-duration: var(--lp-duration-fast);
  }
}
</style>
