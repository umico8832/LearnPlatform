<script setup lang="ts">
import { computed } from 'vue'
import { Minus, Plus } from '@lucide/vue'

const props = withDefaults(defineProps<{ label: string; min?: number; max?: number; step?: number }>(), {
  min: Number.NEGATIVE_INFINITY,
  max: Number.POSITIVE_INFINITY,
  step: 1,
})

const model = defineModel<number>({ required: true })

const canDecrease = computed(() => model.value - props.step >= props.min)
const canIncrease = computed(() => model.value + props.step <= props.max)

function change(direction: 1 | -1) {
  const next = Math.min(props.max, Math.max(props.min, model.value + direction * props.step))
  if (next !== model.value) model.value = next
}
</script>

<template>
  <div class="lt-stepper" role="group" :aria-label="label">
    <span class="lt-stepper-label" aria-hidden="true">{{ label }}</span>
    <button
      type="button"
      class="lt-stepper-button"
      :aria-label="`${label}减 ${step}`"
      :disabled="!canDecrease"
      @click="change(-1)"
    >
      <Minus :size="16" :stroke-width="2.2" aria-hidden="true" />
    </button>
    <output class="lt-stepper-value" aria-live="polite">{{ model }}</output>
    <button
      type="button"
      class="lt-stepper-button"
      :aria-label="`${label}加 ${step}`"
      :disabled="!canIncrease"
      @click="change(1)"
    >
      <Plus :size="16" :stroke-width="2.2" aria-hidden="true" />
    </button>
  </div>
</template>

<style scoped>
.lt-stepper {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 2px;
  height: var(--lt-control-md);
  padding: 0 3px 0 12px;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-3);
  box-shadow: inset 0 0 0 0.5px var(--lt-separator);
}

.lt-stepper-label {
  margin-right: 6px;
  color: var(--lt-label-2);
  font-size: var(--lt-text-caption);
  white-space: nowrap;
}

.lt-stepper-button {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  padding: 0;
  border: 0;
  border-radius: var(--lt-radius-pill);
  background: transparent;
  color: var(--lt-label);
  cursor: pointer;
  transition:
    background-color var(--lt-dur-fade) var(--lt-ease-fade),
    transform var(--lt-dur-snappy) var(--lt-ease-snappy);
}

.lt-stepper-button:active:not(:disabled) {
  transform: scale(0.88);
}

.lt-stepper-button:disabled {
  opacity: 0.3;
  cursor: default;
}

.lt-stepper-value {
  min-width: 22px;
  color: var(--lt-label);
  font: 600 var(--lt-text-subhead) / 1 var(--lt-font-rounded);
  font-variant-numeric: tabular-nums;
  text-align: center;
}

@media (hover: hover) and (pointer: fine) {
  .lt-stepper-button:not(:disabled):hover {
    background: var(--lt-fill-2);
  }
}
</style>
