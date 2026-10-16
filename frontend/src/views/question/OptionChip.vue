<script setup lang="ts">
import QuestionProse from './QuestionProse.vue'

/** One option named in an explanation: the one the learner chose (orange) or the correct one (green). */
defineProps<{ tone: 'orange' | 'green'; label: string; optionKey?: string | null; content?: string | null }>()
</script>

<template>
  <p class="pick" :data-tone="tone">
    <span class="pick-label">{{ label }}</span>
    <span class="pick-key">{{ optionKey }}</span>
    <QuestionProse v-if="content" class="pick-text" :content="content" inline />
  </p>
</template>

<style scoped>
.pick {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: 100%;
  margin: 2px 0 0;
  padding: 4px 14px 4px 4px;
  border-radius: var(--lt-radius-pill);
  font-size: var(--lt-text-footnote);
  line-height: 1.4;
}

.pick[data-tone='orange'] {
  background: var(--lt-orange-soft);
}

.pick[data-tone='green'] {
  background: var(--lt-green-soft);
}

.pick-key {
  display: grid;
  flex: none;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  color: var(--lt-on-tint);
  font: 600 var(--lt-text-caption) / 1 var(--lt-font-rounded);
}

.pick[data-tone='orange'] .pick-key {
  background: var(--lt-orange);
}

.pick[data-tone='green'] .pick-key {
  background: var(--lt-green);
}

.pick-label {
  flex: none;
  padding-left: 8px;
  font-weight: 600;
}

.pick[data-tone='orange'] .pick-label {
  color: var(--lt-orange-text);
}

.pick[data-tone='green'] .pick-label {
  color: var(--lt-green-text);
}

.pick-text {
  min-width: 0;
  font-variant-numeric: tabular-nums;
}
</style>
