<script setup lang="ts">
import { computed } from 'vue'
import type { GamificationPracticeSummaryData } from './types'

const props = defineProps<{ summary: GamificationPracticeSummaryData }>()
const emit = defineEmits<{ continue: [] }>()
const hasGradedRate = computed(() => props.summary.correctRate !== null)
const correctRate = computed(() => Math.max(0, Math.min(100, Math.round(props.summary.correctRate ?? 0))))
const rateLabel = computed(() => props.summary.rateLabel || '正确率')
</script>

<template>
  <article class="gamification-summary" aria-labelledby="gamification-summary-title">
    <header>
      <p>{{ summary.kicker || '本组练习完成' }}</p>
      <h2 id="gamification-summary-title">{{ summary.answeredCount }} 题已记录</h2>
    </header>
    <div class="gamification-summary__results">
      <div
        v-if="hasGradedRate"
        class="gamification-summary__rate"
        role="progressbar"
        :aria-label="rateLabel"
        :aria-valuenow="correctRate"
        aria-valuemin="0"
        aria-valuemax="100"
        :aria-valuetext="`${rateLabel} ${correctRate}%`"
      >
        <span>{{ rateLabel }}</span>
        <strong>{{ correctRate }}%</strong>
      </div>
      <p v-else class="gamification-summary__rate">
        <span>{{ rateLabel }}</span
        ><strong>待判分</strong>
      </p>
      <dl>
        <div>
          <dt>已答</dt>
          <dd>{{ summary.answeredCount }} 题</dd>
        </div>
        <div>
          <dt>获得经验</dt>
          <dd>+{{ summary.xpGained }}</dd>
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
  padding: var(--lp-space-5);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}
header p {
  margin: 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}
h2 {
  margin: var(--lp-space-1) 0 0;
  color: var(--lp-text);
  font-size: var(--lp-text-2xl);
}
.gamification-summary__results {
  display: grid;
  gap: var(--lp-space-4);
  margin: var(--lp-space-5) 0;
}
.gamification-summary__rate {
  display: flex;
  align-items: baseline;
  gap: var(--lp-space-2);
  margin: 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}
.gamification-summary__rate strong {
  color: var(--lp-text);
  font-size: var(--lp-text-xl);
  font-variant-numeric: tabular-nums;
}
dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--lp-space-3);
  margin: 0;
}
dt {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
}
dd {
  margin: var(--lp-space-1) 0 0;
  color: var(--lp-text);
  font-size: var(--lp-text-base);
  font-weight: var(--lp-weight-semibold);
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
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-xs);
}
button {
  padding: 0;
  color: var(--lp-primary);
  font: inherit;
  font-weight: var(--lp-weight-semibold);
  background: transparent;
  border: 0;
  cursor: pointer;
}
</style>
