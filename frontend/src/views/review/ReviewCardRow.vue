<script setup lang="ts">
import { computed, ref } from 'vue'
import { LtButton } from '@/components/living'
import type { ReviewScheduleVO } from '@/api/review'
import { redoWhen } from '@/utils/redoDate'
import QuestionProse from '../question/QuestionProse.vue'

/**
 * One card in the plan: its FSRS scheduling state and next date, with the two
 * bookkeeping actions. Both ask once before acting, since neither can be undone here.
 */
const props = defineProps<{ card: ReviewScheduleVO; removing: boolean; resetting: boolean; error?: string }>()
const emit = defineEmits<{ remove: []; reset: [] }>()

const unavailable = computed(() => props.card.availableForReview === false)
const when = computed(() => redoWhen(props.card.nextReviewDate))
const confirming = ref<'remove' | 'reset' | null>(null)
const busy = computed(() => props.removing || props.resetting)

function confirm() {
  if (confirming.value === 'remove') emit('remove')
  else if (confirming.value === 'reset') emit('reset')
  confirming.value = null
}
</script>

<template>
  <article class="row">
    <div class="main">
      <p class="stem"><QuestionProse :content="card.questionContent" inline size="body" /></p>
      <p class="meta">
        <span>{{ unavailable ? '待配置答案' : card.statusLabel }}</span>
        <span v-if="card.lastRating === 'AGAIN'" class="flag">上次答错</span>
        <span v-else-if="card.lastRating === 'HARD'" class="flag">上次觉得难</span>
        <span class="course">{{ card.courseName }}</span>
      </p>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
    </div>
    <p class="when" :data-tone="when.tone">
      <span class="when-label">下次</span>
      {{ when.text }}
    </p>
    <div class="actions">
      <template v-if="confirming">
        <span class="confirm">{{ confirming === 'remove' ? '移出复习计划？' : '清空排期，今天重新开始？' }}</span>
        <LtButton size="sm" :disabled="busy" @click="confirm">{{ confirming === 'remove' ? '移出' : '重置' }}</LtButton>
        <LtButton size="sm" variant="quiet" :disabled="busy" @click="confirming = null">取消</LtButton>
      </template>
      <template v-else>
        <button type="button" class="link" :disabled="busy" @click="confirming = 'reset'">
          {{ resetting ? '正在重置…' : '重置' }}
        </button>
        <button type="button" class="link" :disabled="busy" @click="confirming = 'remove'">
          {{ removing ? '正在移出…' : '移出' }}
        </button>
      </template>
    </div>
  </article>
</template>

<style scoped>
.row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 112px 232px;
  align-items: center;
  column-gap: var(--lt-space-6);
  padding: var(--lt-space-4) var(--lt-space-7);
}

.row + .row {
  border-top: 1px solid var(--lt-separator);
}

.main {
  min-width: 0;
}

.stem {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lt-space-3);
  margin: 4px 0 0;
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
}

.flag {
  color: var(--lt-orange-text);
}

.error {
  margin: var(--lt-space-2) 0 0;
  color: var(--lt-red-text);
  font-size: var(--lt-text-footnote);
}

.when {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
  color: var(--lt-label-2);
  font: 500 var(--lt-text-footnote) / 1.3 var(--lt-font-rounded);
  font-variant-numeric: tabular-nums;
}

.when[data-tone='due'] {
  color: var(--lt-orange-text);
}

.when-label {
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption2);
  font-weight: 400;
}

.actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--lt-space-4);
}

.confirm {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}

.link {
  padding: 0;
  border: 0;
  background: none;
  color: var(--lt-accent);
  font: inherit;
  font-size: var(--lt-text-footnote);
  cursor: pointer;
}

.link:hover:not(:disabled) {
  text-decoration: underline;
}

.link:disabled {
  color: var(--lt-label-3);
  cursor: default;
}
</style>
