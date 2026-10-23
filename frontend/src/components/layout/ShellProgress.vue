<script setup lang="ts">
import { computed } from 'vue'
import type { GamificationSummary } from '@/api/gamification'

/**
 * Quiet learning facts in the learner bar: level, today's answers against the
 * daily goal (a small ring), and the streak. Opens the learning section of the profile.
 */
const props = defineProps<{ summary: GamificationSummary; compact?: boolean }>()

const RADIUS = 6.5
const CIRCUMFERENCE = 2 * Math.PI * RADIUS

const ratio = computed(() => {
  const { todayAnsweredCount, dailyGoal } = props.summary
  return dailyGoal > 0 ? Math.min(1, todayAnsweredCount / dailyGoal) : 0
})

const linkLabel = computed(() => {
  const { level, todayAnsweredCount, dailyGoal, streakDays } = props.summary
  return `查看学习进度：等级 ${level}，今日已答 ${todayAnsweredCount}/${dailyGoal}${props.compact ? '' : `，连续学习 ${streakDays} 天`}`
})
</script>

<template>
  <RouterLink
    to="/profile#learning"
    class="shell-progress"
    data-testid="gamification-summary"
    :aria-label="linkLabel"
    :title="linkLabel"
  >
    <span class="level">Lv.{{ summary.level }}</span>
    <span class="today">
      <svg class="ring" viewBox="0 0 16 16" aria-hidden="true">
        <circle class="ring-track" cx="8" cy="8" :r="RADIUS" />
        <circle
          v-if="ratio > 0"
          class="ring-value"
          cx="8"
          cy="8"
          :r="RADIUS"
          :stroke-dasharray="CIRCUMFERENCE"
          :stroke-dashoffset="CIRCUMFERENCE * (1 - ratio)"
        />
      </svg>
      <span class="num">{{ summary.todayAnsweredCount }}/{{ summary.dailyGoal }}</span>
    </span>
    <span v-if="!compact && summary.streakDays > 0" class="streak">连续 {{ summary.streakDays }} 天</span>
  </RouterLink>
</template>

<style scoped>
.shell-progress {
  display: inline-flex;
  align-items: center;
  gap: var(--lt-space-3);
  height: var(--lt-control-md);
  padding: 0 var(--lt-space-3);
  border-radius: var(--lt-radius-pill);
  color: var(--lt-label-2);
  font-family: var(--lt-font-rounded);
  font-size: var(--lt-text-footnote);
  font-variant-numeric: tabular-nums;
  text-decoration: none;
  white-space: nowrap;
  transition: background-color var(--lt-dur-fade) var(--lt-ease-fade);
}

.shell-progress:hover {
  background: var(--lt-fill-3);
}

.level {
  color: var(--lt-label);
  font-weight: 600;
}

.today {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.ring {
  width: 16px;
  height: 16px;
  transform: rotate(-90deg);
}

.ring circle {
  fill: none;
  stroke-width: 2.5;
}

.ring-track {
  stroke: var(--lt-fill);
}

.ring-value {
  stroke: var(--lt-accent);
  stroke-linecap: round;
  transition: stroke-dashoffset var(--lt-dur-layout) var(--lt-ease-layout);
}

.streak {
  color: var(--lt-label-3);
}
</style>
