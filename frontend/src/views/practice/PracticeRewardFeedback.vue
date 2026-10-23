<script setup lang="ts">
import { computed } from 'vue'
import type { RewardFeedback } from '@/api/gamification'
const props = defineProps<{ reward: RewardFeedback }>()
const recordText = computed(() =>
  props.reward.awardedXp > 0
    ? `本次记录 +${props.reward.awardedXp} 经验`
    : props.reward.reason === 'DUPLICATE_QUESTION_SAME_DAY'
      ? '此学习对象今日已记录经验'
      : '本次经验已记录',
)
</script>
<template>
  <p class="reward" data-testid="gamification-answer-record">{{ recordText }}</p>
</template>
<style scoped>
.reward {
  margin: 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
  line-height: var(--lt-leading-ui);
  font-variant-numeric: tabular-nums;
}
</style>
