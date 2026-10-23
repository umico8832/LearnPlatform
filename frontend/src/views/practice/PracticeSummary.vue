<script setup lang="ts">
import { computed } from 'vue'
import { LtButton, LtCard } from '@/components/living'
import type { GamificationPracticeSummaryData } from '@/components/gamification/types'
const props = defineProps<{ summary: GamificationPracticeSummaryData }>()
const emit = defineEmits<{ continue: [] }>()
const hasGradedRate = computed(() => props.summary.correctRate !== null)
const correctRate = computed(() => Math.max(0, Math.min(100, Math.round(props.summary.correctRate ?? 0))))
const rateLabel = computed(() => props.summary.rateLabel || '正确率')
</script>
<template>
  <LtCard as="article" radius="xl" class="summary gamification-summary" aria-labelledby="summary-title"
    ><p>{{ summary.kicker || '本组练习完成' }}</p>
    <h1 id="summary-title">{{ summary.answeredCount }} 题已记录</h1>
    <div class="results">
      <p
        v-if="hasGradedRate"
        role="progressbar"
        :aria-label="rateLabel"
        :aria-valuenow="correctRate"
        aria-valuemin="0"
        aria-valuemax="100"
        :aria-valuetext="`${rateLabel} ${correctRate}%`"
      >
        <span>{{ rateLabel }}</span
        ><strong>{{ correctRate }}%</strong>
      </p>
      <p v-else>
        <span>{{ rateLabel }}</span
        ><strong>待判分</strong>
      </p>
      <dl>
        <div>
          <dt>已答</dt>
          <dd>{{ summary.answeredCount }} 题</dd>
        </div>
        <div v-if="summary.pendingCount">
          <dt>待判分</dt>
          <dd>{{ summary.pendingCount }} 题</dd>
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
    <LtButton variant="primary" @click="emit('continue')">继续学习</LtButton></LtCard
  >
</template>
<style scoped>
.summary {
  width: min(calc(var(--lt-space-10) * 7), 100%);
}
.summary > p {
  margin: 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.summary h1 {
  margin: var(--lt-space-1) 0 0;
  font-size: var(--lt-text-title2);
}
.results {
  display: grid;
  gap: var(--lt-space-4);
  margin: var(--lt-space-5) 0;
}
.results > p {
  display: flex;
  gap: var(--lt-space-2);
  margin: 0;
  color: var(--lt-label-2);
}
.results strong {
  color: var(--lt-label);
  font-size: var(--lt-text-title3);
}
.results dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--lt-space-3);
  margin: 0;
}
.results dt {
  color: var(--lt-label-2);
  font-size: var(--lt-text-caption);
}
.results dd {
  margin: var(--lt-space-1) 0 0;
  font-variant-numeric: tabular-nums;
}
.summary ul {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lt-space-2);
  padding: 0;
  margin: 0 0 var(--lt-space-5);
  list-style: none;
  color: var(--lt-label-2);
  font-size: var(--lt-text-caption);
}
</style>
