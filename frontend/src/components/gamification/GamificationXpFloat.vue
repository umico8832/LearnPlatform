<script setup lang="ts">
import { computed, watch } from 'vue'
import { useReducedMotion } from '@/composables/useReducedMotion'

const props = withDefaults(
  defineProps<{
    xp: number
    active?: boolean
  }>(),
  { active: true },
)

const emit = defineEmits<{ complete: [] }>()
const { reducedMotion } = useReducedMotion()
const visible = computed(() => props.active && props.xp > 0)

watch(
  () => props.xp,
  (xp, previousXp) => {
    if (xp > 0 && xp !== previousXp && reducedMotion.value) emit('complete')
  },
)
</script>

<template>
  <Transition name="gamification-xp-float" appear @after-leave="emit('complete')">
    <output v-if="visible" class="gamification-xp-float" aria-live="polite">+{{ xp }} 经验</output>
  </Transition>
</template>

<style scoped>
.gamification-xp-float {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: var(--lp-space-1) var(--lp-space-3);
  color: var(--lp-reward-xp);
  font-size: var(--lp-text-sm);
  font-weight: var(--lp-weight-bold);
  font-variant-numeric: tabular-nums;
  background: var(--lp-reward-xp-soft);
  border-radius: var(--lp-radius-full);
  box-shadow: var(--lp-shadow-xs);
}
.gamification-xp-float-enter-active {
  animation: gamification-xp-float var(--lp-duration-feedback) var(--lp-ease-celebration);
}
.gamification-xp-float-leave-active {
  transition: opacity var(--lp-duration-fast) var(--lp-ease-out);
}
.gamification-xp-float-enter-from,
.gamification-xp-float-leave-to {
  opacity: 0;
}
@keyframes gamification-xp-float {
  from {
    opacity: 0;
    transform: translateY(var(--lp-space-2));
  }
  55% {
    opacity: 1;
  }
  to {
    opacity: 1;
    transform: translateY(calc(var(--lp-space-3) * -1));
  }
}
@media (prefers-reduced-motion: reduce) {
  .gamification-xp-float-enter-active {
    animation: none;
  }
}
</style>
