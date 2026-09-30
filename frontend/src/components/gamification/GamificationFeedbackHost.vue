<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import GamificationAchievementNotice from './GamificationAchievementNotice.vue'
import GamificationLevelCelebration from './GamificationLevelCelebration.vue'
import GamificationPracticeSummary from './GamificationPracticeSummary.vue'
import type { CelebrationRequest, CelebrationScheduler } from '@/utils/celebrationScheduler'
import { launchCelebrationConfetti } from '@/utils/celebrationConfetti'

const props = defineProps<{ scheduler: CelebrationScheduler }>()
const current = ref<CelebrationRequest>()
let unsubscribe: (() => void) | undefined
let dismissalTimer: ReturnType<typeof setTimeout> | undefined

const dismiss = () => props.scheduler.dismiss()
const currentAchievement = computed(() => current.value?.achievements?.[0])
const isLevel = computed(() => current.value?.kind === 'level')
const isSummary = computed(() => current.value?.kind === 'summary' && current.value.summary)

const scheduleDismissal = () => {
  if (dismissalTimer) clearTimeout(dismissalTimer)
  if (current.value?.kind === 'achievement') dismissalTimer = setTimeout(dismiss, 6000)
}
const onKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape' && current.value) dismiss()
}

watch(
  () => current.value?.id,
  () => {
    if (current.value?.kind === 'level' || current.value?.kind === 'summary') void launchCelebrationConfetti()
  },
)

onMounted(() => {
  unsubscribe = props.scheduler.subscribe((next) => {
    current.value = next
    scheduleDismissal()
  })
  window.addEventListener('keydown', onKeydown)
})
onUnmounted(() => {
  unsubscribe?.()
  if (dismissalTimer) clearTimeout(dismissalTimer)
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <aside v-if="current" class="gamification-feedback-host" :class="{ 'is-level': isLevel }" aria-label="学习奖励提示">
    <Transition name="gamification-celebration" mode="out-in" appear>
      <GamificationLevelCelebration
        v-if="isLevel"
        :key="current.id"
        :level="current.level ?? 0"
        :xp-gained="current.xpGained"
        @dismiss="dismiss"
      />
      <GamificationPracticeSummary
        v-else-if="isSummary"
        :key="current.id"
        :summary="current.summary!"
        @continue="dismiss"
      />
      <GamificationAchievementNotice
        v-else-if="currentAchievement"
        :key="current.id"
        :achievement="currentAchievement"
        @dismiss="dismiss"
      />
    </Transition>
  </aside>
</template>

<style scoped>
.gamification-feedback-host {
  position: fixed;
  z-index: var(--lp-z-toast);
  top: calc(var(--lp-header-height) + var(--lp-space-4));
  right: var(--lp-space-6);
  pointer-events: none;
}
.gamification-feedback-host.is-level {
  inset: 0;
  display: grid;
  place-items: center;
  background: color-mix(in srgb, var(--lp-reward-level-soft) 38%, transparent);
}
.gamification-feedback-host :deep(button) {
  pointer-events: auto;
}
.gamification-celebration-enter-active {
  animation: gamification-celebration-in var(--lp-duration-celebration) var(--lp-ease-celebration);
}
.gamification-celebration-leave-active {
  transition: opacity var(--lp-duration-fast) var(--lp-ease-out);
}
.gamification-celebration-leave-to {
  opacity: 0;
}
@keyframes gamification-celebration-in {
  from {
    opacity: 0;
    transform: translateY(calc(var(--lp-space-4) * -1)) scale(0.97);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
@media (prefers-reduced-motion: reduce) {
  .gamification-celebration-enter-active {
    animation: none;
  }
}
</style>
