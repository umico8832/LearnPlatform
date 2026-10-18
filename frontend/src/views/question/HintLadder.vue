<script setup lang="ts">
import type { HintLevel, TutorHint } from '@/api/questionTutor'
import QuestionProse from './QuestionProse.vue'

/**
 * The hints the learner has asked for, one level at a time: a concept to
 * think about, then the reasoning steps, then a similar case from the course.
 * None of them rules an option in or out.
 */
defineProps<{ hints: TutorHint[] }>()

const LEVELS: Record<HintLevel, string> = { 1: '想一想概念', 2: '推理的步骤', 3: '一个相似的例子' }
const TOTAL = 3
</script>

<template>
  <div class="ladder">
    <h2 class="sr-only">提示</h2>
    <p class="meter" :aria-label="`已用 ${hints.length} 级提示，共 ${TOTAL} 级`">
      <span v-for="n in TOTAL" :key="n" class="meter-bar" :data-on="n <= hints.length || undefined" />
      <span class="meter-text" aria-hidden="true">已用 {{ hints.length }}/{{ TOTAL }}</span>
    </p>
    <section v-for="hint in hints" :key="hint.level" class="hint">
      <h3 class="hint-label" tabindex="-1" data-focus>
        提示 {{ hint.level }}<span class="hint-kind"> · {{ LEVELS[hint.level] }}</span>
      </h3>
      <QuestionProse class="hint-text" :content="hint.text" />
    </section>
  </div>
</template>

<style scoped>
.meter {
  display: flex;
  align-items: center;
  gap: 4px;
  margin: 0 0 var(--lt-space-4);
}

.meter-bar {
  width: 20px;
  height: 4px;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
  transition: background-color var(--lt-dur-fade) var(--lt-ease-fade);
}

.meter-bar[data-on] {
  background: var(--lt-accent);
}

.meter-text {
  margin-left: 6px;
  color: var(--lt-label-3);
  font: 500 var(--lt-text-caption) / 1 var(--lt-font-rounded);
  font-variant-numeric: tabular-nums;
}

.hint + .hint {
  margin-top: var(--lt-space-7);
}

.hint-label {
  margin: 0 0 var(--lt-space-2);
  color: var(--lt-accent);
  font-size: var(--lt-text-footnote);
  font-weight: 600;
}

.hint-kind {
  color: var(--lt-label-2);
  font-weight: 500;
}

/* 标签只为读屏和键盘定位而聚焦，不画焦点框。 */
.hint-label:focus {
  outline: none;
}

.hint-text {
  font-size: var(--lt-text-reading);
  line-height: var(--lt-leading-reading);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
</style>
