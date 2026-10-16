<script setup lang="ts">
import { computed } from 'vue'
import { ArrowRight } from '@lucide/vue'
import { LtButton } from '@/components/living'
import type { ReviewScheduleVO } from '@/api/review'
import QuestionProse from '../question/QuestionProse.vue'
import { dueReason } from './useReview'

/** One question due today: why it is back, how late it is, and the way in. */
const props = defineProps<{ card: ReviewScheduleVO }>()
const emit = defineEmits<{ open: [] }>()

const reason = computed(() => dueReason(props.card))
const when = computed(() => (props.card.overdueDays > 0 ? `逾期 ${props.card.overdueDays} 天` : '今天'))
</script>

<template>
  <article class="row">
    <p class="why" :data-tone="reason.tone">
      <span class="why-text">{{ reason.text }}</span>
      <span class="when" :data-late="card.overdueDays > 0 || undefined">{{ when }}</span>
    </p>
    <div class="main">
      <h3 class="stem"><QuestionProse :content="card.questionContent" inline /></h3>
      <p class="course">{{ card.courseName }}</p>
    </div>
    <LtButton class="go" @click="emit('open')">
      复习
      <template #trailing><ArrowRight aria-hidden="true" /></template>
    </LtButton>
  </article>
</template>

<style scoped>
.row {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) auto;
  align-items: center;
  column-gap: var(--lt-space-6);
  padding: var(--lt-space-5) var(--lt-space-7);
}

.row + .row {
  border-top: 1px solid var(--lt-separator);
}

.why {
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-self: start;
  margin: 2px 0 0;
}

.why-text {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
  font-weight: 500;
}

.why[data-tone='orange'] .why-text {
  color: var(--lt-orange-text);
}

.when {
  color: var(--lt-label-3);
  font: 500 var(--lt-text-caption) / 1.3 var(--lt-font-rounded);
  font-variant-numeric: tabular-nums;
}

.when[data-late] {
  color: var(--lt-orange-text);
}

.main {
  min-width: 0;
}

.stem {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  font-size: var(--lt-text-body);
  font-weight: 500;
  line-height: 1.55;
}

.course {
  margin: 4px 0 0;
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
}
</style>
