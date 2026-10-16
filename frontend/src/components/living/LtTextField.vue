<script setup lang="ts">
import { computed, ref, useId } from 'vue'
import { Eye, EyeOff } from '@lucide/vue'

/**
 * Form text field: visible label above, message below. A password field gets a
 * show/hide toggle. The parent validates; `error` non-empty marks it invalid.
 */
const props = withDefaults(
  defineProps<{
    label: string
    type?: 'text' | 'email' | 'password'
    autocomplete?: string
    placeholder?: string
    /** Validation message; empty means valid. */
    error?: string
    /** Helper text under the field, replaced by the error while invalid. */
    hint?: string
    inputmode?: 'text' | 'numeric'
    maxlength?: number
    disabled?: boolean
  }>(),
  { type: 'text', autocomplete: 'off', placeholder: undefined, error: '', hint: '', disabled: false },
)

const model = defineModel<string>({ required: true })
const id = useId()
const input = ref<HTMLInputElement | null>(null)
const revealed = ref(false)
const inputType = computed(() => (props.type === 'password' && revealed.value ? 'text' : props.type))
const message = computed(() => props.error || props.hint)

defineExpose({ focus: () => input.value?.focus() })
</script>

<template>
  <div class="lt-text-field" :data-invalid="Boolean(error) || undefined">
    <div class="head">
      <label class="label" :for="`${id}-input`">{{ label }}</label>
      <slot name="label-trailing" />
    </div>
    <div class="box">
      <input
        :id="`${id}-input`"
        ref="input"
        v-model="model"
        class="input"
        :type="inputType"
        :autocomplete="autocomplete"
        :placeholder="placeholder"
        :inputmode="inputmode"
        :maxlength="maxlength"
        :disabled="disabled"
        :aria-invalid="Boolean(error)"
        :aria-describedby="message ? `${id}-message` : undefined"
        spellcheck="false"
      />
      <button
        v-if="type === 'password'"
        type="button"
        class="reveal"
        :aria-label="revealed ? '隐藏密码' : '显示密码'"
        :aria-pressed="revealed"
        :disabled="disabled"
        @click="revealed = !revealed"
      >
        <EyeOff v-if="revealed" :size="18" aria-hidden="true" />
        <Eye v-else :size="18" aria-hidden="true" />
      </button>
    </div>
    <p v-if="message" :id="`${id}-message`" class="message" :data-error="Boolean(error) || undefined">
      {{ message }}
    </p>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--lt-space-3);
  margin: 0 var(--lt-space-1) var(--lt-space-2);
}

.label {
  color: var(--lt-label);
  font-size: var(--lt-text-footnote);
  font-weight: 500;
}

.box {
  display: flex;
  align-items: center;
  height: var(--lt-control-lg);
  padding: 0 6px 0 16px;
  border-radius: var(--lt-radius-md);
  background: var(--lt-fill-3);
  box-shadow: inset 0 0 0 0.5px var(--lt-separator);
  transition: box-shadow var(--lt-dur-fade) var(--lt-ease-fade);
}

.box:focus-within {
  box-shadow:
    inset 0 0 0 1.5px var(--lt-accent),
    0 0 0 4px var(--lt-accent-soft);
}

[data-invalid] .box {
  box-shadow:
    inset 0 0 0 1.5px var(--lt-red),
    0 0 0 4px var(--lt-red-soft);
}

.input {
  flex: 1;
  min-width: 0;
  height: 100%;
  padding: 0;
  border: 0;
  outline: none;
  background: transparent;
  color: var(--lt-label);
  font: var(--lt-text-body) / 1 var(--lt-font);
}

.input:focus-visible {
  outline: none;
}

.input::placeholder {
  color: var(--lt-label-3);
}

.input:disabled {
  color: var(--lt-label-2);
}

.reveal {
  display: grid;
  flex: none;
  place-items: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: 0;
  border-radius: var(--lt-radius-sm);
  background: none;
  color: var(--lt-label-2);
  cursor: pointer;
}

.reveal:hover:not(:disabled) {
  background: var(--lt-fill-2);
  color: var(--lt-label);
}

.reveal:disabled {
  cursor: default;
}

.message {
  margin: var(--lt-space-2) var(--lt-space-1) 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-caption);
  line-height: 1.4;
}

.message[data-error] {
  color: var(--lt-red-text);
}
</style>
