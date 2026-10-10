<script setup lang="ts">
import { useId } from 'vue'

withDefaults(
  defineProps<{
    label: string
    /** Validation message shown under the field; empty means valid. */
    error?: string
    /** Extra description for assistive technology, e.g. the accepted format. */
    description?: string
    /** Visible input width in characters. */
    chars?: number
    placeholder?: string
  }>(),
  { error: '', description: '', chars: 14, placeholder: undefined },
)

const model = defineModel<string>({ required: true })
const emit = defineEmits<{
  /** Enter or blur after editing: the parent parses and validates. */
  commit: [value: string]
  /** Escape: the parent restores the last valid value. */
  cancel: []
}>()

const id = useId()

function onKeydown(event: KeyboardEvent) {
  const input = event.target as HTMLInputElement
  if (event.key === 'Enter') {
    event.preventDefault()
    emit('commit', model.value)
  } else if (event.key === 'Escape') {
    event.preventDefault()
    emit('cancel')
    input.blur()
  }
}
</script>

<template>
  <div class="lt-field" :data-invalid="Boolean(error)">
    <label class="lt-field-label" :for="`${id}-input`">{{ label }}</label>
    <input
      :id="`${id}-input`"
      v-model="model"
      class="lt-field-input"
      :style="{ width: `${chars * 0.62}em` }"
      :placeholder="placeholder"
      :aria-invalid="Boolean(error)"
      :aria-describedby="`${id}-description ${id}-error`"
      spellcheck="false"
      autocomplete="off"
      @change="emit('commit', model)"
      @keydown="onKeydown"
    />
    <span :id="`${id}-description`" class="lt-sr-only">{{ description }}</span>
    <span :id="`${id}-error`" class="lt-field-error" aria-live="polite">{{ error }}</span>
  </div>
</template>

<style scoped>
.lt-field {
  position: relative;
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 8px;
  height: var(--lt-control-md);
  padding: 0 12px;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-3);
  box-shadow: inset 0 0 0 0.5px var(--lt-separator);
  transition: box-shadow var(--lt-dur-fade) var(--lt-ease-fade);
}

.lt-field:focus-within {
  box-shadow:
    inset 0 0 0 1.5px var(--lt-accent),
    0 0 0 4px var(--lt-accent-soft);
}

.lt-field[data-invalid='true'] {
  box-shadow:
    inset 0 0 0 1.5px var(--lt-red),
    0 0 0 4px var(--lt-red-soft);
}

.lt-field-label {
  color: var(--lt-label-2);
  font-size: var(--lt-text-caption);
  white-space: nowrap;
}

.lt-field-input {
  min-width: 0;
  padding: 0;
  border: 0;
  outline: none;
  background: transparent;
  color: var(--lt-label);
  font: 600 var(--lt-text-subhead) / 1 var(--lt-font-rounded);
  letter-spacing: 0.02em;
}

.lt-field-input:focus-visible {
  outline: none;
}

.lt-field-input::placeholder {
  color: var(--lt-label-3);
  font-weight: 500;
}

.lt-field-error {
  position: absolute;
  top: calc(100% + 6px);
  left: 12px;
  z-index: 5;
  color: var(--lt-red-text);
  font-size: var(--lt-text-caption);
  white-space: nowrap;
}

.lt-field-error:empty {
  display: none;
}
</style>
