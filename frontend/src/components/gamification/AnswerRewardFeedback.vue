<script setup lang="ts">
import { computed } from 'vue'
import type { RewardFeedback } from '@/api/gamification'

const props = defineProps<{ reward: RewardFeedback }>()
const recordText = computed(() => {
  if (props.reward.awardedXp > 0) return `本次记录 +${props.reward.awardedXp} 经验`
  return props.reward.reason === 'DUPLICATE_QUESTION_SAME_DAY' ? '此学习对象今日已记录经验' : '本次经验已记录'
})
</script>

<template>
  <p class="answer-reward" data-testid="gamification-answer-record">{{ recordText }}</p>
</template>

<style scoped>
.answer-reward {
  margin: var(--lp-space-3) 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  line-height: var(--lp-leading-body);
  font-variant-numeric: tabular-nums;
}
</style>
