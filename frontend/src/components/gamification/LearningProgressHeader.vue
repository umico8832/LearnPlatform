<script setup lang="ts">
import { computed } from 'vue'
import type { GamificationSummary } from '@/api/gamification'

const props = defineProps<{ summary: GamificationSummary; compact?: boolean }>()
const linkLabel = computed(() => {
  const { level, todayAnsweredCount, dailyGoal, streakDays } = props.summary
  return `查看学习进度：等级 ${level}，今日已答 ${todayAnsweredCount}/${dailyGoal}${props.compact ? '' : `，连续学习 ${streakDays} 天`}`
})
</script>

<template>
  <router-link
    to="/profile#learning"
    class="learning-progress-header"
    :class="{ 'is-compact': compact }"
    data-testid="gamification-summary"
    :aria-label="linkLabel"
  >
    <span class="learning-progress-header__level">Lv.{{ summary.level }}</span>
    <span class="learning-progress-header__goal">今日 {{ summary.todayAnsweredCount }}/{{ summary.dailyGoal }}</span>
    <span v-if="!compact && summary.streakDays > 0" class="learning-progress-header__streak"
      >连续 {{ summary.streakDays }} 天</span
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
  color: var(--lp-text);
  font-weight: var(--lp-weight-semibold);
  font-variant-numeric: tabular-nums;
}
.learning-progress-header__goal {
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.learning-progress-header__streak {
  color: var(--lp-text-muted);
  white-space: nowrap;
}
</style>
