<script setup lang="ts">
import type { RewardFeedback } from '@/api/gamification'
import GamificationXpFloat from './GamificationXpFloat.vue'

defineProps<{ reward: RewardFeedback }>()
</script>

<template>
  <div class="answer-reward" aria-live="polite">
    <GamificationXpFloat :xp="reward.awardedXp" data-testid="gamification-xp-float" />
    <span v-if="reward.awardedXp === 0">
      {{ reward.reason === 'DUPLICATE_QUESTION_SAME_DAY' ? '此学习对象今日已记录经验' : '本次经验已记录' }}
    </span>
    <span class="combo-badge" data-testid="gamification-combo">连续答对 {{ reward.summary.currentCombo }}</span>
  </div>
</template>

<style scoped>
.answer-reward {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: var(--lp-space-3);
  margin: var(--lp-space-4) 0;
  color: var(--lp-text-secondary);
}
.combo-badge {
  color: var(--lp-reward-combo);
  background: var(--lp-reward-combo-soft);
  border-radius: var(--lp-radius-full);
  padding: var(--lp-space-1) var(--lp-space-3);
  font-weight: var(--lp-weight-semibold);
}
</style>
