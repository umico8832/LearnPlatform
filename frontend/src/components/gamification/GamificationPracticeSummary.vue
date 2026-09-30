<script setup lang="ts">
import { computed, watch } from 'vue'
import { useAnimatedNumber } from '@/composables/useAnimatedNumber'
import { useReducedMotion } from '@/composables/useReducedMotion'
import type { GamificationPracticeSummaryData } from './types'

const props = defineProps<{ summary: GamificationPracticeSummaryData }>()
const emit = defineEmits<{ continue: [] }>()
const { reducedMotion } = useReducedMotion()
const { value: rate, animateTo: animateRate } = useAnimatedNumber(0, { reducedMotion })
const { value: xp, animateTo: animateXp } = useAnimatedNumber(0, { reducedMotion })
const { value: combo, animateTo: animateCombo } = useAnimatedNumber(0, { reducedMotion })
const hasGradedRate = computed(() => props.summary.correctRate !== null)
const correctRate = computed(() => Math.max(0, Math.min(100, Math.round(props.summary.correctRate ?? 0))))
const ringOffset = computed(() => 100 - rate.value)
watch(
  () => [correctRate.value, props.summary.xpGained, props.summary.longestCombo] as const,
  ([nextRate, nextXp, nextCombo]) => {
    animateRate(nextRate)
    animateXp(nextXp)
    animateCombo(nextCombo)
  },
  { immediate: true },
)
</script>

<template>
  <article class="gamification-summary" aria-labelledby="gamification-summary-title">
    <header>
      <p>{{ summary.kicker || '本组练习完成' }}</p>
      <h2 id="gamification-summary-title">{{ summary.answeredCount }} 题已记录</h2>
    </header>
    <div class="gamification-summary__results">
      <div
        class="gamification-summary__rate"
        role="img"
        :aria-label="hasGradedRate ? `${summary.rateLabel || '正确率'} ${rate}%` : '本组作答待判分'"
      >
        <svg viewBox="0 0 36 36" aria-hidden="true">
          <path
            class="gamification-summary__ring-base"
            pathLength="100"
            d="M18 2.5a15.5 15.5 0 1 1 0 31a15.5 15.5 0 1 1 0-31"
          />
          <path
            class="gamification-summary__ring-value"
            pathLength="100"
            :style="{ strokeDashoffset: ringOffset }"
            d="M18 2.5a15.5 15.5 0 1 1 0 31a15.5 15.5 0 1 1 0-31"
          />
        </svg>
        <strong>{{ hasGradedRate ? `${rate}%` : '待判分' }}</strong
        ><span>{{ summary.rateLabel || '正确率' }}</span>
      </div>
      <dl>
        <div>
          <dt>获得经验</dt>
          <dd>+{{ xp }}</dd>
        </div>
        <div>
          <dt>{{ summary.comboLabel || '本组期间最高连击' }}</dt>
          <dd>{{ combo }}</dd>
        </div>
      </dl>
    </div>
    <ul v-if="summary.achievements?.length" aria-label="本次解锁成就">
      <li v-for="achievement in summary.achievements" :key="achievement.id">
        {{ achievement.icon || '✦' }} {{ achievement.title }}
      </li>
    </ul>
    <button type="button" @click="emit('continue')">继续学习</button>
  </article>
</template>

<style scoped>
.gamification-summary {
  width: min(520px, 100%);
  padding: var(--lp-space-6);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-xl);
  box-shadow: var(--lp-shadow-md);
}
header p {
  margin: 0;
  color: var(--lp-reward-xp);
  font-size: var(--lp-text-sm);
  font-weight: var(--lp-weight-semibold);
}
h2 {
  margin: var(--lp-space-1) 0 0;
  color: var(--lp-text);
  font-size: var(--lp-text-3xl);
}
.gamification-summary__results {
  display: flex;
  align-items: center;
  gap: var(--lp-space-5);
  margin: var(--lp-space-5) 0;
}
.gamification-summary__rate {
  position: relative;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  width: 92px;
  height: 92px;
}
.gamification-summary__rate svg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}
.gamification-summary__ring-base,
.gamification-summary__ring-value {
  fill: none;
  stroke-width: 3.5;
}
.gamification-summary__ring-base {
  stroke: var(--lp-reward-xp-soft);
}
.gamification-summary__ring-value {
  stroke: var(--lp-reward-xp);
  stroke-linecap: round;
  stroke-dasharray: 100;
  transition: stroke-dashoffset var(--lp-duration-celebration) var(--lp-ease-celebration);
}
.gamification-summary__rate strong {
  color: var(--lp-text);
  font-size: var(--lp-text-xl);
  font-variant-numeric: tabular-nums;
  z-index: 1;
}
.gamification-summary__rate span {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
  z-index: 1;
}
dl {
  display: grid;
  flex: 1;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--lp-space-3);
  margin: 0;
}
dl div {
  padding: var(--lp-space-3);
  background: var(--lp-surface-soft);
  border-radius: var(--lp-radius-md);
}
dt {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
}
dd {
  margin: var(--lp-space-1) 0 0;
  color: var(--lp-text);
  font-size: var(--lp-text-lg);
  font-weight: var(--lp-weight-bold);
  font-variant-numeric: tabular-nums;
}
ul {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lp-space-2);
  padding: 0;
  margin: 0 0 var(--lp-space-5);
  list-style: none;
}
li {
  padding: var(--lp-space-1) var(--lp-space-2);
  color: var(--lp-reward-achievement);
  font-size: var(--lp-text-xs);
  background: var(--lp-reward-achievement-soft);
  border-radius: var(--lp-radius-full);
}
button {
  width: 100%;
  min-height: 40px;
  color: var(--lp-on-primary);
  font: inherit;
  font-weight: var(--lp-weight-semibold);
  background: var(--lp-primary);
  border: 0;
  border-radius: var(--lp-radius-md);
  cursor: pointer;
}
button:focus-visible {
  outline: 3px solid var(--lp-primary-softer);
  outline-offset: 2px;
}
@media (prefers-reduced-motion: reduce) {
  .gamification-summary__ring-value {
    transition-duration: var(--lp-duration-fast);
  }
}
</style>
