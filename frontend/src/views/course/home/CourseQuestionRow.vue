<script setup lang="ts">
import { computed } from 'vue'
import { ChevronRight } from '@lucide/vue'
import { redoWhen } from '@/utils/redoDate'
import QuestionProse from '../../question/QuestionProse.vue'
import type { CourseQuestion } from './useCourseHome'

/** One question in a chapter: where the learner stands on it, its stem, and the way in. */
const props = defineProps<{ question: CourseQuestion; index: number }>()

const mark = computed(() => {
  const { status, redoDueDate } = props.question
  if (status === 'TO_REDO') {
    const when = redoWhen(redoDueDate)
    return { tone: 'orange', label: '待重做', detail: when.tone === 'none' ? '' : when.text }
  }
  if (status === 'DONE') return { tone: 'green', label: '已完成', detail: '' }
  if (status === 'NEW') return { tone: 'none', label: '未做', detail: '' }
  return null
})
</script>

<template>
  <RouterLink class="row" :to="`/questions/${question.id}`">
    <span class="index">{{ index }}</span>
    <span class="stem"><QuestionProse :content="question.stem" inline size="body" /></span>
    <span v-if="mark" class="mark" :data-tone="mark.tone">
      <span class="dot" aria-hidden="true" />
      {{ mark.label }}<span v-if="mark.detail" class="detail"> · {{ mark.detail }}</span>
    </span>
    <ChevronRight class="chevron" :size="18" aria-hidden="true" />
  </RouterLink>
</template>

<style scoped>
.row {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) auto 18px;
  align-items: center;
  column-gap: var(--lt-space-4);
  padding: var(--lt-space-4) var(--lt-space-6);
  color: var(--lt-label);
  text-decoration: none;
}

.row + .row {
  border-top: 1px solid var(--lt-separator);
}

.row:hover {
  background: var(--lt-fill);
}

.row:focus-visible {
  outline: 2px solid var(--lt-accent);
  outline-offset: -2px;
  border-radius: inherit;
}

.index {
  color: var(--lt-label-3);
  font: 500 var(--lt-text-footnote) / 1 var(--lt-font-rounded);
  font-variant-numeric: tabular-nums;
}

.stem {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-height: 1.55;
}

.mark {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
  white-space: nowrap;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--lt-label-3);
}

.mark[data-tone='orange'] {
  color: var(--lt-orange-text);
}

.mark[data-tone='orange'] .dot {
  background: var(--lt-orange);
}

.mark[data-tone='green'] {
  color: var(--lt-green-text);
}

.mark[data-tone='green'] .dot {
  background: var(--lt-green);
}

.detail {
  font-variant-numeric: tabular-nums;
}

.chevron {
  color: var(--lt-label-3);
}
</style>
