<script setup lang="ts">
import { computed, ref } from 'vue'
import { springAnimate, springs } from '@/motion'
import QuestionProse from '@/views/question/QuestionProse.vue'

const props = defineProps<{
  options: { key: string; value: string; content: string }[]
  /** Several options may be chosen (checkboxes). */
  multiple?: boolean
  evaluated: boolean
  correctAnswers: string[]
  name: string
  label: string
  /** Large centered tiles for true/false. */
  binary?: boolean
  isSelected: (value: string) => boolean
}>()

const emit = defineEmits<{ pick: [value: string] }>()

const rows = ref<HTMLElement[]>([])
const items = computed(() =>
  props.options.map((option) => {
    const selected = props.isSelected(option.value)
    const state = !props.evaluated
      ? selected
        ? 'selected'
        : 'idle'
      : props.correctAnswers.includes(option.value)
        ? 'right'
        : selected
          ? 'wrong'
          : 'dim'
    return { ...option, selected, state }
  }),
)

function pick(value: string, index: number) {
  emit('pick', value)
  const row = rows.value[index]
  if (row) springAnimate(row, [{ transform: 'scale(0.985)' }, { transform: 'scale(1)' }], springs.pointer)
}
</script>

<template>
  <div class="choices" :data-binary="binary || undefined" :role="multiple ? 'group' : 'radiogroup'" :aria-label="label">
    <label v-for="(item, index) in items" :key="item.value" ref="rows" class="option-item" :data-state="item.state">
      <input
        class="choice-input"
        :type="multiple ? 'checkbox' : 'radio'"
        :name="name"
        :value="item.value"
        :checked="item.selected"
        @change="pick(item.value, index)"
      />
      <span v-if="item.key" class="choice-key" aria-hidden="true">{{ item.key }}</span>
      <QuestionProse class="choice-text" :content="item.content" inline />
      <span v-if="evaluated && item.state === 'right'" class="choice-tag">{{
        item.selected ? '你的选择，正确' : '正确答案'
      }}</span>
      <span v-else-if="evaluated && item.state === 'wrong'" class="choice-tag">你的选择，错误</span>
    </label>
  </div>
</template>

<style scoped>
.choices {
  display: grid;
  gap: var(--lt-space-3);
}

.choices[data-binary] {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--lt-space-3);
}

.option-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--lt-space-3);
  min-height: calc(var(--lt-control-lg) + var(--lt-space-2));
  padding: var(--lt-space-3) var(--lt-space-4);
  border-radius: var(--lt-radius-md);
  background: var(--lt-fill-3);
  box-shadow: inset 0 0 0 0.5px var(--lt-separator);
  font-size: var(--lt-text-body);
  line-height: var(--lt-leading-ui);
  font-variant-numeric: tabular-nums;
  cursor: pointer;
  transition:
    background-color var(--lt-dur-fade) var(--lt-ease-fade),
    box-shadow var(--lt-dur-fade) var(--lt-ease-fade);
}

.choices[data-binary] .option-item {
  justify-content: center;
  min-height: calc(var(--lt-control-lg) + var(--lt-space-5));
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
  width: var(--lt-control-sm);
  height: var(--lt-control-sm);
  border-radius: var(--lt-space-2);
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

.option-item[data-state='selected'] {
  background: var(--lt-accent-soft);
  box-shadow: inset 0 0 0 1.5px var(--lt-accent);
}

.option-item[data-state='selected'] .choice-key {
  background: var(--lt-accent-fill);
  color: var(--lt-on-accent);
}

fieldset:disabled .option-item {
  cursor: default;
}
.option-item[data-state='wrong'] {
  background: var(--lt-orange-soft);
  box-shadow: inset 0 0 0 1px var(--lt-orange);
}
.option-item[data-state='right'] {
  background: var(--lt-green-soft);
  box-shadow: inset 0 0 0 1px var(--lt-green);
}
.option-item[data-state='wrong'] .choice-key {
  background: var(--lt-orange);
  color: var(--lt-on-tint);
}
.option-item[data-state='right'] .choice-key {
  background: var(--lt-green);
  color: var(--lt-on-tint);
}
.option-item[data-state='dim'] {
  opacity: 0.5;
}
.choice-tag {
  font-size: var(--lt-text-caption);
  color: var(--lt-label-2);
}
.option-item[data-state='wrong'] .choice-tag {
  color: var(--lt-orange-text);
}
.option-item[data-state='right'] .choice-tag {
  color: var(--lt-green-text);
}
@media (hover: hover) and (pointer: fine) {
  .option-item[data-state='idle']:hover {
    background: var(--lt-fill-2);
  }
}
</style>
