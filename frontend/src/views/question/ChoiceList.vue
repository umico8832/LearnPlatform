<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Check } from '@lucide/vue'
import { springAnimate, springs } from '@/motion'
import type { UnderstandingOption } from '@/api/understanding'
import QuestionProse from './QuestionProse.vue'

/**
 * Single-choice options as native radios: arrow keys and screen readers work
 * as usual, letters pick an option and Enter submits while the list is active.
 * Once locked, the learner's choice and (when opened) the correct answer stay marked.
 */
const props = defineProps<{
  options: UnderstandingOption[]
  name: string
  label: string
  /** Answer submitted; options no longer change. */
  locked: boolean
  /** The submitted answer. */
  chosen?: string | null
  /** The correct answer, once the server has opened it. */
  correct?: string | null
  /** Letter shortcuts and Enter apply to this list. */
  active: boolean
}>()

const selected = defineModel<string | null>({ required: true })
const emit = defineEmits<{ submit: [] }>()

const rows = ref<HTMLElement[]>([])

type State = 'idle' | 'selected' | 'locked' | 'wrong' | 'right' | 'dim'

function stateOf(label: string): State {
  if (!props.locked) return selected.value === label ? 'selected' : 'idle'
  if (props.correct && label === props.correct) return 'right'
  if (label === props.chosen) return 'wrong'
  return props.correct ? 'dim' : 'locked'
}

const items = computed(() =>
  props.options.map((option) => {
    const state = stateOf(option.label)
    const mine = props.locked && option.label === props.chosen
    let tag = ''
    if (mine) tag = '你的选择'
    else if (state === 'right') tag = '正确答案'
    let spoken = ''
    if (state === 'right') spoken = mine ? '，你的选择，正确' : '，正确答案'
    else if (state === 'wrong') spoken = '，你的选择，错误'
    return { ...option, state, tag, spoken }
  }),
)

function pulse(label: string) {
  const index = props.options.findIndex((option) => option.label === label)
  const row = rows.value[index]
  if (row) springAnimate(row, [{ transform: 'scale(0.985)' }, { transform: 'scale(1)' }], springs.pointer)
}

function pick(label: string) {
  if (props.locked) return
  selected.value = label
}

function ignore(target: EventTarget | null): boolean {
  const el = target instanceof HTMLElement ? target : null
  return Boolean(el?.closest('input:not([type="radio"]), textarea, select, [contenteditable="true"], dialog, .viz'))
}

function onKeydown(event: KeyboardEvent) {
  if (!props.active || props.locked || event.defaultPrevented) return
  if (event.metaKey || event.ctrlKey || event.altKey || ignore(event.target)) return
  const target = event.target instanceof HTMLElement ? event.target : null
  if (event.key === 'Enter') {
    const onOtherControl = target?.closest('button, a') && !target.closest('.choices')
    if (onOtherControl || !selected.value) return
    event.preventDefault()
    emit('submit')
    return
  }
  const letter = event.key.toUpperCase()
  const index = props.options.findIndex((option) => option.label.toUpperCase() === letter)
  if (index < 0 || target?.closest('button, a')) return
  event.preventDefault()
  pick(props.options[index]!.label)
  rows.value[index]?.querySelector('input')?.focus({ preventScroll: true })
}

watch(
  () => props.active && !props.locked,
  (listening) => {
    if (listening) window.addEventListener('keydown', onKeydown)
    else window.removeEventListener('keydown', onKeydown)
  },
  { immediate: true },
)

watch(selected, (label) => label && !props.locked && pulse(label))
watch(
  () => props.correct,
  (label) => label && pulse(label),
)

onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div class="choices" role="radiogroup" :aria-label="label" :aria-disabled="locked || undefined">
    <label v-for="item in items" :key="item.label" ref="rows" class="choice" :data-state="item.state">
      <input
        class="choice-input"
        type="radio"
        :name="name"
        :value="item.label"
        :checked="locked ? item.label === chosen : selected === item.label"
        :disabled="locked"
        @change="pick(item.label)"
      />
      <span class="choice-key" aria-hidden="true">{{ item.label }}</span>
      <QuestionProse class="choice-text" :content="item.content" inline />
      <span class="sr-only">{{ item.spoken }}</span>
      <span v-if="item.tag" class="choice-tag" aria-hidden="true">
        <Check v-if="item.state === 'right'" :size="14" />
        {{ item.tag }}
      </span>
    </label>
  </div>
</template>

<style scoped>
.choices {
  display: grid;
  gap: 10px;
}

.choice {
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
    box-shadow var(--lt-dur-fade) var(--lt-ease-fade),
    opacity var(--lt-dur-fade) var(--lt-ease-fade);
}

.choice:has(.choice-input:focus-visible) {
  outline: 2px solid var(--lt-accent);
  outline-offset: 2px;
}

.choice-input {
  position: absolute;
  inset: 0;
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

.choice-tag {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 4px;
  font-size: var(--lt-text-caption);
  font-weight: 600;
}

.choice[data-state='selected'] {
  background: var(--lt-accent-soft);
  box-shadow: inset 0 0 0 1.5px var(--lt-accent);
}

.choice[data-state='selected'] .choice-key {
  background: var(--lt-accent-fill);
  color: var(--lt-on-accent);
}

.choice:is([data-state='locked'], [data-state='wrong'], [data-state='right'], [data-state='dim']) {
  cursor: default;
}

.choice[data-state='wrong'] {
  background: var(--lt-orange-soft);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--lt-orange) 45%, transparent);
}

.choice[data-state='wrong'] .choice-key {
  background: var(--lt-orange);
  color: var(--lt-on-tint);
}

.choice[data-state='wrong'] .choice-tag {
  color: var(--lt-orange-text);
}

.choice[data-state='right'] {
  background: var(--lt-green-soft);
  box-shadow: inset 0 0 0 1.5px var(--lt-green);
}

.choice[data-state='right'] .choice-key {
  background: var(--lt-green);
  color: var(--lt-on-tint);
}

.choice[data-state='right'] .choice-tag {
  color: var(--lt-green-text);
}

.choice[data-state='dim'] {
  opacity: 0.5;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}

@media (hover: hover) and (pointer: fine) {
  .choice[data-state='idle']:hover {
    background: var(--lt-fill-2);
  }
}
</style>
