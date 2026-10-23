<script setup lang="ts">
import { computed, ref } from 'vue'
import { springAnimate, springs } from '@/motion'
import QuestionProse from '@/views/question/QuestionProse.vue'

/**
 * Exam options as native radios or checkboxes. A timed exam stays silent until
 * submission, so the only state shown is the learner's own selection.
 */
const props = defineProps<{
  options: { key: string; value: string; content: string }[]
  /** Several options may be chosen (checkboxes). */
  multiple?: boolean
  name: string
  label: string
  /** Large centered tiles for true/false. */
  binary?: boolean
  isSelected: (value: string) => boolean
}>()

const emit = defineEmits<{ pick: [value: string] }>()

const rows = ref<HTMLElement[]>([])
const items = computed(() => props.options.map((option) => ({ ...option, selected: props.isSelected(option.value) })))

function pick(value: string, index: number) {
  emit('pick', value)
  const row = rows.value[index]
  if (row) springAnimate(row, [{ transform: 'scale(0.985)' }, { transform: 'scale(1)' }], springs.pointer)
}
</script>

<template>
  <div class="choices" :data-binary="binary || undefined" :role="multiple ? 'group' : 'radiogroup'" :aria-label="label">
    <label
      v-for="(item, index) in items"
      :key="item.value"
      ref="rows"
      class="option-item"
      :data-selected="item.selected || undefined"
    >
      <input
        class="choice-input"
        :type="multiple ? 'checkbox' : 'radio'"
        :name="name"
        :value="item.value"
        :checked="item.selected"
        @change="pick(item.value, index)"
      />
      <span v-if="item.key" class="choice-key">{{ item.key }}</span>
      <QuestionProse class="choice-text" :content="item.content" inline />
    </label>
  </div>
</template>

<style scoped>
.choices {
  display: grid;
  gap: 10px;
}

.choices[data-binary] {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--lt-space-3);
}

.option-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 52px;
  padding: 12px 14px;
  border-radius: var(--lt-radius-md);
  background: var(--lt-fill-3);
  box-shadow: inset 0 0 0 0.5px var(--lt-separator);
  font-size: var(--lt-text-body);
  line-height: 1.5;
  font-variant-numeric: tabular-nums;
  cursor: pointer;
  transition:
    background-color var(--lt-dur-fade) var(--lt-ease-fade),
    box-shadow var(--lt-dur-fade) var(--lt-ease-fade);
}

.choices[data-binary] .option-item {
  justify-content: center;
  min-height: 64px;
  font-size: var(--lt-text-headline);
  font-weight: 600;
}

.option-item:has(.choice-input:focus-visible) {
  outline: 2px solid var(--lt-accent);
  outline-offset: 2px;
}

.choice-input {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  margin: 0;
  opacity: 0;
  cursor: inherit;
}

.choice-key {
  display: grid;
  flex: none;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: 8px;
  background: var(--lt-surface);
  box-shadow: 0 0 0 0.5px var(--lt-separator);
  color: var(--lt-label-2);
  font: 600 var(--lt-text-footnote) / 1 var(--lt-font-rounded);
  transition:
    background-color var(--lt-dur-fade) var(--lt-ease-fade),
    color var(--lt-dur-fade) var(--lt-ease-fade);
}

.choice-text {
  flex: 1;
}

.choices[data-binary] .choice-text {
  flex: none;
}

.option-item[data-selected] {
  background: var(--lt-accent-soft);
  box-shadow: inset 0 0 0 1.5px var(--lt-accent);
}

.option-item[data-selected] .choice-key {
  background: var(--lt-accent-fill);
  color: var(--lt-on-accent);
}

fieldset:disabled .option-item {
  cursor: default;
  opacity: 0.6;
}

@media (hover: hover) and (pointer: fine) {
  .option-item:not([data-selected]):hover {
    background: var(--lt-fill-2);
  }

  fieldset:disabled .option-item:hover {
    background: var(--lt-fill-3);
  }
}
</style>
