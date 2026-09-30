<script setup lang="ts">
import { computed } from 'vue'
import type { GamificationSummary } from '@/api/gamification'

const props = defineProps<{ summary: GamificationSummary; compact?: boolean }>()
const progress = computed(() =>
  props.summary.nextLevelXp
    ? Math.min(100, (props.summary.xpIntoLevel / (props.summary.nextLevelXp - props.summary.levelStartXp)) * 100)
    : 100,
)
const goal = computed(() =>
  props.summary.dailyGoal ? Math.min(100, (props.summary.todayAnsweredCount / props.summary.dailyGoal) * 100) : 0,
)
</script>

<template>
  <router-link
    to="/profile#learning"
    class="learning-progress-header"
    :class="{ 'is-compact': compact }"
    data-testid="gamification-summary"
    aria-label="查看学习进度"
  >
    <span class="learning-progress-header__level">Lv.{{ summary.level }}</span>
    <span class="learning-progress-header__xp" data-testid="gamification-xp-progress"
      ><span :style="{ width: `${progress}%` }"
    /></span>
    <span v-if="!compact" class="learning-progress-header__streak">◆ {{ summary.streakDays }} 天</span>
    <span
      class="learning-progress-header__goal"
      :aria-label="`今日目标 ${summary.todayAnsweredCount}/${summary.dailyGoal}`"
      ><i :style="{ '--goal': `${goal}%` }" />{{ summary.todayAnsweredCount }}/{{ summary.dailyGoal }}</span
    >
  </router-link>
</template>

<style scoped>
.learning-progress-header {
  display: flex;
  align-items: center;
  gap: var(--lp-space-2);
  min-width: 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-xs);
  text-decoration: none;
}
.learning-progress-header__level {
  padding: 3px 6px;
  color: var(--lp-on-primary);
  font-weight: var(--lp-weight-bold);
  background: var(--lp-reward-level);
  border-radius: var(--lp-radius-full);
}
.learning-progress-header__xp {
  width: 88px;
  height: 6px;
  overflow: hidden;
  background: var(--lp-reward-xp-soft);
  border-radius: var(--lp-radius-full);
}
.learning-progress-header__xp span {
  display: block;
  height: 100%;
  background: var(--lp-reward-xp);
  border-radius: inherit;
  transition: width var(--lp-duration-feedback) var(--lp-ease-out);
}
.learning-progress-header__streak {
  color: var(--lp-reward-streak);
  font-weight: var(--lp-weight-semibold);
  white-space: nowrap;
}
.learning-progress-header__goal {
  display: flex;
  align-items: center;
  gap: 3px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.learning-progress-header__goal i {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: conic-gradient(var(--lp-reward-xp) var(--goal), var(--lp-surface-inset) 0);
}
.is-compact .learning-progress-header__xp {
  width: 56px;
}
@media (prefers-reduced-motion: reduce) {
  .learning-progress-header__xp span {
    transition-duration: var(--lp-duration-fast);
  }
}
</style>
