<script setup lang="ts">
import { CircleAlert, CircleCheck } from '@lucide/vue'
import { LtButton, LtCard } from '@/components/living'
import type { QuestionSource, UnderstandingOption } from '@/api/understanding'
import ChoiceList from './ChoiceList.vue'
import QuestionProse from './QuestionProse.vue'
import SourceBadge from './SourceBadge.vue'

/**
 * One single-choice question to answer: source, stem, options and a submit row
 * that turns into the verdict once the server has judged it. Used for the
 * original question and for its variant.
 */
defineProps<{
  source: QuestionSource
  /** Shown at the top right, e.g. "单选题". */
  kind: string
  /** Screen-reader heading at `level`, so the page outline stays right. */
  heading: string
  headingId: string
  level: 2 | 3
  /** Variant only: what changed compared with the original question. */
  changed?: string | null
  stem: string
  options: UnderstandingOption[]
  name: string
  label: string
  chosen?: string | null
  correct?: string | null
  pending: boolean
  error?: string | null
  /** Set once judged; locks the options and replaces the submit row. */
  verdict: { correct: boolean; text: string } | null
}>()

const selected = defineModel<string | null>({ required: true })
const emit = defineEmits<{ submit: [] }>()
</script>

<template>
  <LtCard as="article" radius="xl" padding="none" class="question-card" :aria-labelledby="headingId">
    <div class="card-head">
      <SourceBadge :source="source" />
      <span class="card-type">{{ kind }}</span>
    </div>
    <component :is="`h${level}`" :id="headingId" class="lt-sr-only">{{ heading }}</component>
    <p v-if="changed" class="changed">
      <span class="changed-label">和原题相比</span>
      <span>{{ changed }}</span>
    </p>
    <QuestionProse class="stem" :content="stem" />
    <ChoiceList
      v-model="selected"
      class="choices"
      :name="name"
      :label="label"
      :options="options"
      :locked="Boolean(verdict)"
      :chosen="chosen"
      :correct="correct"
      :active="!verdict"
      @submit="emit('submit')"
    />
    <footer class="card-foot">
      <template v-if="!verdict">
        <span class="foot-note" :data-tone="error ? 'error' : undefined">
          {{ error || '按 A–D 选择，Enter 提交' }}
        </span>
        <LtButton variant="primary" size="lg" :disabled="!selected || pending" @click="emit('submit')">
          {{ pending ? '正在判分…' : '提交' }}
        </LtButton>
      </template>
      <p v-else class="verdict" :data-tone="verdict.correct ? 'green' : 'orange'">
        <CircleCheck v-if="verdict.correct" :size="18" aria-hidden="true" />
        <CircleAlert v-else :size="18" aria-hidden="true" />
        <span>{{ verdict.text }}</span>
      </p>
    </footer>
  </LtCard>
</template>

<style scoped>
.question-card {
  padding: 22px 28px 24px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lt-space-4);
  margin-bottom: var(--lt-space-4);
}

.card-type {
  flex: none;
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
}

.changed {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin: 0 0 var(--lt-space-4);
  padding: 10px 14px;
  border-radius: var(--lt-radius-sm);
  background: var(--lt-fill-3);
  box-shadow: inset 0 0 0 0.5px var(--lt-separator);
  font-size: var(--lt-text-subhead);
  line-height: 1.5;
}

.changed-label {
  flex: none;
  color: var(--lt-label-2);
  font-size: var(--lt-text-caption);
  font-weight: 600;
}

.choices {
  margin-top: var(--lt-space-5);
}

.card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lt-space-4);
  min-height: var(--lt-control-lg);
  margin-top: var(--lt-space-5);
}

.foot-note {
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
}

.foot-note[data-tone='error'] {
  color: var(--lt-red-text);
}

.verdict {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: var(--lt-text-body);
  font-weight: 600;
}

.verdict[data-tone='orange'] {
  color: var(--lt-orange-text);
}

.verdict[data-tone='green'] {
  color: var(--lt-green-text);
}
</style>
